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
package org.apache.lucene.facet;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Two extra checks of RandomSamplingFacetsCollector.createManager with more than one slice, both
 * with default IndexSearcher slicing (an executor and ten small segments, which default slicing
 * groups five per slice):
 *
 * <ul>
 *   <li>the reduced collector's getOriginalMatchingDocs() must hold every hit (class javadoc: "If
 *       you require the original set of hits, you can call getOriginalMatchingDocs()");
 *   <li>the reduced sample must have about sampleSize hits (constructor javadoc: sampling ratio
 *       sampleSize / totalN).
 * </ul>
 */
public class TestSamplingReduceV3Check extends LuceneTestCase {

  private static Directory buildIndex(FacetsConfig config, int segments, int docsPerSegment)
      throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setRAMBufferSizeMB(256)
            .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int seg = 0; seg < segments; seg++) {
        for (int i = 0; i < docsPerSegment; i++) {
          Document doc = new Document();
          doc.add(new SortedSetDocValuesFacetField("color", "red"));
          w.addDocument(config.build(doc));
        }
        w.commit();
      }
    }
    return dir;
  }

  private static int count(List<FacetsCollector.MatchingDocs> mds) throws IOException {
    int n = 0;
    for (FacetsCollector.MatchingDocs md : mds) {
      DocIdSetIterator it = md.bits().iterator();
      while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
        n++;
      }
    }
    return n;
  }

  public void testDefaultSlicingTenSmallSegments() throws Exception {
    FacetsConfig config = new FacetsConfig();
    int segments = 10;
    int docsPerSegment = 2000;
    int sampleSize = 1000;
    try (Directory dir = buildIndex(config, segments, docsPerSegment);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(segments, reader.leaves().size());
      int totalHits = reader.numDocs();
      ExecutorService executor = Executors.newFixedThreadPool(2);
      try {
        IndexSearcher searcher = new IndexSearcher(reader, executor);
        int numSlices = searcher.getSlices().length;
        CollectorManager<RandomSamplingFacetsCollector, RandomSamplingFacetsCollector> manager =
            RandomSamplingFacetsCollector.createManager(sampleSize, 42L);
        RandomSamplingFacetsCollector reduced = searcher.search(new MatchAllDocsQuery(), manager);
        int original = count(reduced.getOriginalMatchingDocs());
        int sampled = count(reduced.getMatchingDocs());
        String msg =
            "slices="
                + numSlices
                + " totalHits="
                + totalHits
                + " sampleSize="
                + sampleSize
                + " reduced.getOriginalMatchingDocs()="
                + original
                + " reduced.getMatchingDocs()="
                + sampled
                + " samplingRate="
                + reduced.getSamplingRate();
        System.out.println(msg);
        assertEquals("default slicing must give 2 slices: " + msg, 2, numSlices);
        assertEquals("original hits: " + msg, totalHits, original);
        assertTrue("sample size: " + msg, Math.abs(sampled - sampleSize) <= sampleSize / 10);
      } finally {
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
      }
    }
  }
}
