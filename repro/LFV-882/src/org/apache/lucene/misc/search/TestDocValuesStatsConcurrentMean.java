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
package org.apache.lucene.misc.search;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.misc.search.DocValuesStats.LongDocValuesStats;
import org.apache.lucene.misc.search.DocValuesStats.SortedLongDocValuesStats;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DocValuesStatsCollectorManager on an IndexSearcher with an executor must give the same mean and
 * variance as on a searcher without one. Values are epoch timestamps in nanoseconds (about 1.76e18,
 * well inside the long range); 6 segments of 4 documents give 2 slices with the default slicing.
 */
public class TestDocValuesStatsConcurrentMean extends LuceneTestCase {

  // 2025-10-28T00:00:00Z in nanoseconds since the epoch; one document per second after it
  private static final long T0 = 1_761_609_600_000_000_000L;
  private static final int SEGMENTS = 6;
  private static final int DOCS_PER_SEGMENT = 4;
  private static final int N = SEGMENTS * DOCS_PER_SEGMENT;
  // exact mean of T0 + i * 1e9, i = 0..23, is T0 + 11.5e9; exact population variance is
  // 1e18 * (N * N - 1) / 12
  private static final double EXACT_MEAN = 1.7616096115E18;
  private static final double EXACT_VARIANCE = 1e18 * (N * N - 1) / 12.0;

  private Directory buildIndex(boolean multiValued) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < N; i++) {
        long value = T0 + i * 1_000_000_000L;
        Document doc = new Document();
        if (multiValued) {
          doc.add(new LongField("ts", value, LongField.Store.NO)); // sorted-numeric doc values
        } else {
          doc.add(new NumericDocValuesField("ts", value));
        }
        w.addDocument(doc);
        if ((i + 1) % DOCS_PER_SEGMENT == 0) {
          w.flush();
        }
      }
    }
    return dir;
  }

  public void testLongStatsMeanWithExecutor() throws Exception {
    try (Directory dir = buildIndex(false);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(SEGMENTS, reader.leaves().size());
      LongDocValuesStats serial =
          new IndexSearcher(reader)
              .search(
                  MatchAllDocsQuery.INSTANCE,
                  new DocValuesStatsCollectorManager<>(() -> new LongDocValuesStats("ts")));
      ExecutorService executor = Executors.newFixedThreadPool(2);
      try {
        IndexSearcher concurrentSearcher = new IndexSearcher(reader, executor);
        assertEquals(2, concurrentSearcher.getSlices().length);
        LongDocValuesStats concurrent =
            concurrentSearcher.search(
                MatchAllDocsQuery.INSTANCE,
                new DocValuesStatsCollectorManager<>(() -> new LongDocValuesStats("ts")));
        assertEquals(N, serial.count());
        assertEquals(N, concurrent.count());
        assertEquals(T0, concurrent.min().longValue());
        assertEquals(T0 + (N - 1) * 1_000_000_000L, concurrent.max().longValue());
        assertEquals("serial mean", EXACT_MEAN, serial.mean(), 1e-9 * EXACT_MEAN);
        assertEquals("concurrent mean", EXACT_MEAN, concurrent.mean(), 1e-9 * EXACT_MEAN);
        assertEquals("concurrent variance", EXACT_VARIANCE, concurrent.variance(), 1e-3 * EXACT_VARIANCE);
      } finally {
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
      }
    }
  }

  public void testSortedLongStatsMeanWithExecutor() throws Exception {
    try (Directory dir = buildIndex(true);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(SEGMENTS, reader.leaves().size());
      SortedLongDocValuesStats serial =
          new IndexSearcher(reader)
              .search(
                  MatchAllDocsQuery.INSTANCE,
                  new DocValuesStatsCollectorManager<>(() -> new SortedLongDocValuesStats("ts")));
      ExecutorService executor = Executors.newFixedThreadPool(2);
      try {
        IndexSearcher concurrentSearcher = new IndexSearcher(reader, executor);
        assertEquals(2, concurrentSearcher.getSlices().length);
        SortedLongDocValuesStats concurrent =
            concurrentSearcher.search(
                MatchAllDocsQuery.INSTANCE,
                new DocValuesStatsCollectorManager<>(() -> new SortedLongDocValuesStats("ts")));
        assertEquals(N, serial.valuesCount());
        assertEquals(N, concurrent.valuesCount());
        assertEquals("serial mean", EXACT_MEAN, serial.mean(), 1e-9 * EXACT_MEAN);
        assertEquals("concurrent mean", EXACT_MEAN, concurrent.mean(), 1e-9 * EXACT_MEAN);
        // one value per document, so the variance-over-documents denominator equals N here
        assertEquals("concurrent variance", EXACT_VARIANCE, concurrent.variance(), 1e-3 * EXACT_VARIANCE);
      } finally {
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
      }
    }
  }
}
