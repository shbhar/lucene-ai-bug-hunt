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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.SearcherTaxonomyManager.SearcherAndTaxonomy;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Plain-threads companion of TestSearcherTaxonomyManagerCtorOrder (no hooks): one thread indexes
 * documents with new categories while the test thread creates SearcherTaxonomyManagers, and every
 * first pair is checked. Timing dependent; use it to measure how often the real scheduler hits
 * the window.
 */
public class TestSearcherTaxonomyManagerCtorRacePlain extends LuceneTestCase {

  public void testFirstPairsUnderConcurrentIndexing() throws Exception {
    try (Directory indexDir = newDirectory();
        Directory taxoDir = newDirectory()) {
      IndexWriter writer =
          new IndexWriter(indexDir, new IndexWriterConfig(new MockAnalyzer(random())));
      DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
      FacetsConfig config = new FacetsConfig();
      AtomicBoolean stop = new AtomicBoolean();
      AtomicReference<Throwable> indexerFailure = new AtomicReference<>();
      Thread indexer =
          new Thread(
              () -> {
                try {
                  for (int i = 0; stop.get() == false && i < 200_000; i++) {
                    Document d = new Document();
                    d.add(new FacetField("Author", "a" + i)); // a new category per document
                    writer.addDocument(config.build(taxoWriter, d));
                  }
                } catch (Throwable t) {
                  indexerFailure.set(t);
                }
              });
      indexer.start();
      int managers = 300;
      int mismatches = 0;
      String firstMismatch = null;
      try {
        for (int m = 0; m < managers; m++) {
          SearcherTaxonomyManager mgr =
              new SearcherTaxonomyManager(writer, true, null, taxoWriter);
          try {
            SearcherAndTaxonomy pair = mgr.acquire();
            try {
              int numDocs = pair.searcher().getIndexReader().numDocs();
              if (numDocs > 0) {
                // documents are added in order by one thread: doc k carries category Author/a<k>
                int ord =
                    pair.taxonomyReader().getOrdinal(new FacetLabel("Author", "a" + (numDocs - 1)));
                if (ord == TaxonomyReader.INVALID_ORDINAL) {
                  mismatches++;
                  if (firstMismatch == null) {
                    firstMismatch =
                        "manager "
                            + m
                            + ": searcher numDocs="
                            + numDocs
                            + ", taxonomy size="
                            + pair.taxonomyReader().getSize();
                  }
                }
              }
            } finally {
              mgr.release(pair);
            }
          } finally {
            mgr.close();
          }
        }
      } finally {
        stop.set(true);
        indexer.join();
        writer.close();
        taxoWriter.close();
      }
      assertNull(indexerFailure.get());
      assertEquals(
          "first pairs whose searcher references an ordinal unknown to its taxonomy reader, out of "
              + managers
              + " managers; first: "
              + firstMismatch,
          0,
          mismatches);
    }
  }
}
