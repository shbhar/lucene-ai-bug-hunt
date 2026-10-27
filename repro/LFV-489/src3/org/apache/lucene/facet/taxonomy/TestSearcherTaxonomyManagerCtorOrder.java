/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.facet.taxonomy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.taxonomy.SearcherTaxonomyManager.SearcherAndTaxonomy;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

/**
 * The near-real-time SearcherTaxonomyManager constructor opens the taxonomy reader before the
 * index reader, the opposite of the order that TaxonomyReader's javadoc requires and that
 * refreshIfNeeded follows. A document indexed (with a new category) between the two opens is
 * visible to the first searcher, but its ordinal is unknown to the paired taxonomy reader.
 *
 * <p>Deterministic: the main IndexWriter's InfoStream pauses the constructing thread when its
 * getReader call starts (no lock is held at that point) and lets an indexing thread add one
 * document, which is exactly what an unlucky scheduler can do. Public API only.
 */
public class TestSearcherTaxonomyManagerCtorOrder extends LuceneTestCase {

  /** Pauses the armed thread once, at the start of IndexWriter.getReader. */
  private static final class PauseAtGetReader extends InfoStream {
    final AtomicReference<Thread> armed = new AtomicReference<>();
    final CountDownLatch paused = new CountDownLatch(1);
    final CountDownLatch resume = new CountDownLatch(1);

    @Override
    public void message(String component, String message) {
      if ("IW".equals(component)
          && "flush at getReader".equals(message)
          && armed.compareAndSet(Thread.currentThread(), null)) {
        paused.countDown();
        try {
          assertTrue(resume.await(30, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }

    @Override
    public boolean isEnabled(String component) {
      return "IW".equals(component);
    }

    @Override
    public void close() {}
  }

  private static String authorCounts(SearcherAndTaxonomy pair, FacetsConfig config) {
    try {
      FacetsCollector fc = new FacetsCollector();
      pair.searcher.search(new MatchAllDocsQuery(), fc);
      FacetResult result =
          new FastTaxonomyFacetCounts(pair.taxonomyReader, config, fc)
              .getTopChildren(10, "Author");
      // label order of tied counts is not part of the contract: compare sorted
      java.util.List<String> labels = new java.util.ArrayList<>();
      for (LabelAndValue lv : result.labelValues) {
        labels.add(lv.label + "=" + lv.value);
      }
      java.util.Collections.sort(labels);
      return String.join(" ", labels);
    } catch (Exception e) {
      return "facet counting threw " + e;
    }
  }

  public void testFirstPairIsMatchedUnderConcurrentIndexing() throws Exception {
    try (Directory indexDir = newDirectory();
        Directory taxoDir = newDirectory()) {
      PauseAtGetReader pause = new PauseAtGetReader();
      IndexWriter writer =
          new IndexWriter(
              indexDir, new IndexWriterConfig(new MockAnalyzer(random())).setInfoStream(pause));
      DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
      FacetsConfig config = new FacetsConfig();

      Document doc = new Document();
      doc.add(new FacetField("Author", "Alice"));
      writer.addDocument(config.build(taxoWriter, doc));

      // An application indexing thread: addCategory (via FacetsConfig.build), then addDocument.
      AtomicReference<Throwable> indexerFailure = new AtomicReference<>();
      Thread indexer =
          new Thread(
              () -> {
                try {
                  assertTrue(pause.paused.await(30, TimeUnit.SECONDS));
                  Document d = new Document();
                  d.add(new FacetField("Author", "Bob"));
                  writer.addDocument(config.build(taxoWriter, d));
                } catch (Throwable t) {
                  indexerFailure.set(t);
                } finally {
                  pause.resume.countDown();
                }
              });
      indexer.start();

      pause.armed.set(Thread.currentThread());
      SearcherTaxonomyManager mgr = new SearcherTaxonomyManager(writer, true, null, taxoWriter);
      indexer.join();
      assertNull(indexerFailure.get());

      try {
        SearcherAndTaxonomy pair = mgr.acquire();
        String first;
        int numDocs;
        int taxoSize;
        int bobOrd;
        try {
          numDocs = pair.searcher.getIndexReader().numDocs();
          taxoSize = pair.taxonomyReader.getSize();
          bobOrd = pair.taxonomyReader.getOrdinal(new FacetLabel("Author", "Bob"));
          first = authorCounts(pair, config);
        } finally {
          mgr.release(pair);
        }

        // Nothing changed in the index since the constructor, so a refresh keeps the same pair.
        boolean refreshed = mgr.maybeRefresh();
        pair = mgr.acquire();
        String afterRefresh;
        try {
          afterRefresh = authorCounts(pair, config);
        } finally {
          mgr.release(pair);
        }

        String msg =
            "first pair: searcher numDocs="
                + numDocs
                + ", taxonomy size="
                + taxoSize
                + ", taxonomy ordinal of Author/Bob="
                + bobOrd
                + "; counts: ["
                + first
                + "]; after maybeRefresh() (returned "
                + refreshed
                + "): ["
                + afterRefresh
                + "]";
        assertEquals(msg, 2, numDocs);
        assertEquals(msg, "Alice=1 Bob=1", first);
        assertEquals(msg, "Alice=1 Bob=1", afterRefresh);
      } finally {
        mgr.close();
        writer.close();
        taxoWriter.close();
      }
    }
  }
}
