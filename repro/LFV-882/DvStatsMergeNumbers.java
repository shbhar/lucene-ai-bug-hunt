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

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Locale;
import java.util.LongSummaryStatistics;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.misc.search.DocValuesStats.LongDocValuesStats;
import org.apache.lucene.misc.search.DocValuesStatsCollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Prints the numbers behind the bug doc: LongDocValuesStats through DocValuesStatsCollectorManager,
 * without an executor and with an executor under three slicings, for 24 epoch-nanosecond
 * timestamps T0 + i * 1e9 (i = 0..23). Public API only; no assertions.
 */
public class DvStatsMergeNumbers {

  static final long T0 = 1_761_609_600_000_000_000L; // 2025-10-28T00:00:00Z in nanoseconds
  static final int N = 24;

  static Directory index(int segments) throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      int perSegment = N / segments;
      for (int i = 0; i < N; i++) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("ts", T0 + i * 1_000_000_000L));
        w.addDocument(doc);
        if ((i + 1) % perSegment == 0) {
          w.flush();
        }
      }
    }
    return dir;
  }

  static IndexSearcher oneSegmentPerSlice(IndexReader reader, ExecutorService executor) {
    return new IndexSearcher(reader, executor) {
      @Override
      protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
        return slices(leaves, 1, 1, false);
      }
    };
  }

  static void print(String label, IndexSearcher searcher) throws Exception {
    LongDocValuesStats s =
        searcher.search(
            MatchAllDocsQuery.INSTANCE,
            new DocValuesStatsCollectorManager<>(() -> new LongDocValuesStats("ts")));
    System.out.printf(
        Locale.ROOT,
        "%-44s slices=%d count=%d min=%d max=%d sum=%d%n%-44s mean=%s variance=%s stdev=%s%n",
        label,
        searcher.getSlices().length,
        s.count(),
        s.min(),
        s.max(),
        s.sum(),
        "",
        s.mean(),
        s.variance(),
        s.stdev());
  }

  public static void main(String[] args) throws Exception {
    BigDecimal exactSum = BigDecimal.ZERO;
    LongSummaryStatistics jdk = new LongSummaryStatistics();
    for (int i = 0; i < N; i++) {
      long v = T0 + i * 1_000_000_000L;
      exactSum = exactSum.add(BigDecimal.valueOf(v));
      jdk.accept(v);
    }
    BigDecimal exactMean = exactSum.divide(BigDecimal.valueOf(N), MathContext.DECIMAL128);
    double exactVariance = 1e18 * (N * N - 1) / 12.0; // population variance of T0 + i * 1e9
    System.out.printf(
        Locale.ROOT,
        "exact: sum=%s (Long.MAX_VALUE=%d) mean=%s variance=%s stdev=%s%n",
        exactSum,
        Long.MAX_VALUE,
        exactMean.doubleValue(),
        exactVariance,
        Math.sqrt(exactVariance));
    System.out.printf(
        Locale.ROOT,
        "java.util.LongSummaryStatistics: sum=%d getAverage()=%s%n",
        jdk.getSum(),
        jdk.getAverage());

    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      try (Directory dir = index(6);
          DirectoryReader reader = DirectoryReader.open(dir)) {
        System.out.println("index: 6 segments of 4 documents");
        print("IndexSearcher(reader)", new IndexSearcher(reader));
        print("IndexSearcher(reader, executor) default", new IndexSearcher(reader, executor));
        print("one segment per slice (6 slices of 4)", oneSegmentPerSlice(reader, executor));
      }
      try (Directory dir = index(4);
          DirectoryReader reader = DirectoryReader.open(dir)) {
        System.out.println("index: 4 segments of 6 documents");
        print("IndexSearcher(reader)", new IndexSearcher(reader));
        print("IndexSearcher(reader, executor) default", new IndexSearcher(reader, executor));
        print("one segment per slice (4 slices of 6)", oneSegmentPerSlice(reader, executor));
      }
    } finally {
      executor.shutdown();
      executor.awaitTermination(10, TimeUnit.SECONDS);
    }
  }
}
