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
package v1repro;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.misc.search.DocValuesStats;
import org.apache.lucene.misc.search.DocValuesStatsCollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.NamedThreadFactory;

/**
 * Independent check: mean() and variance() of the long-valued DocValuesStats, computed through
 * DocValuesStatsCollectorManager on a searcher with an executor, against an exact reference
 * (BigDecimal) and against the same search without an executor. Only public/protected API, from a
 * package outside org.apache.lucene.misc.search.
 *
 * <p>Values are epoch-nanosecond timestamps (about 1.7e18): ordinary long values, far from
 * Long.MIN_VALUE / Long.MAX_VALUE, but six of them already sum past Long.MAX_VALUE.
 */
public class TestDvStatsMergeOverflowV1 extends LuceneTestCase {

  private static final long BASE_NANOS = 1_700_000_000_000_000_000L; // 2023-11-14T22:13:20Z
  private static final long HOUR_NANOS = 3_600_000_000_000L;

  /**
   * Exact mean of {@code values} and exact sum of squared deviations divided by {@code divisor}.
   * The divisor is the number of values for LongDocValuesStats and the number of documents for
   * SortedLongDocValuesStats, whose variance() divides by count() (documents), as the upstream
   * TestDocValuesStatsCollector oracle also does.
   */
  private static double[] exactMeanAndVariance(List<Long> values, int divisor) {
    MathContext mc = MathContext.DECIMAL128;
    BigDecimal sum = BigDecimal.ZERO;
    for (long v : values) {
      sum = sum.add(BigDecimal.valueOf(v));
    }
    BigDecimal n = BigDecimal.valueOf(values.size());
    BigDecimal mean = sum.divide(n, mc);
    BigDecimal sq = BigDecimal.ZERO;
    for (long v : values) {
      BigDecimal d = BigDecimal.valueOf(v).subtract(mean);
      sq = sq.add(d.multiply(d));
    }
    return new double[] {mean.doubleValue(), sq.divide(BigDecimal.valueOf(divisor), mc).doubleValue()};
  }

  private static boolean close(double expected, double actual, double relTol) {
    return Math.abs(expected - actual) <= relTol * Math.abs(expected);
  }

  /** Searcher that puts every segment into its own slice (protected, documented expert hook). */
  private static IndexSearcher oneSegmentPerSlice(IndexReader reader, ExecutorService executor) {
    return new IndexSearcher(reader, executor) {
      @Override
      protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
        return slices(leaves, 1, 1, false);
      }
    };
  }

  private interface SearcherFactory {
    IndexSearcher create(IndexReader reader, ExecutorService executor);
  }

  /**
   * Builds numSegments segments (no merges), each with docsPerSegment docs carrying valuesPerDoc
   * values of the field "ts" (NumericDocValuesField if valuesPerDoc == 0 means single-valued), and
   * checks serial vs. concurrent vs. exact stats.
   */
  private void check(
      boolean sortedNumeric,
      int numSegments,
      int docsPerSegment,
      int valuesPerDoc,
      long base,
      long step,
      SearcherFactory concurrentFactory,
      int minSlices)
      throws Exception {
    List<Long> all = new ArrayList<>();
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        int k = 0;
        for (int s = 0; s < numSegments; s++) {
          for (int d = 0; d < docsPerSegment; d++) {
            Document doc = new Document();
            for (int j = 0; j < (sortedNumeric ? valuesPerDoc : 1); j++) {
              long v = base + (k++) * step + random().nextInt(1_000_000);
              all.add(v);
              if (sortedNumeric) {
                doc.add(new SortedNumericDocValuesField("ts", v));
              } else {
                doc.add(new NumericDocValuesField("ts", v));
              }
            }
            w.addDocument(doc);
          }
          w.commit(); // one segment per batch
        }
      }
      double[] exact = exactMeanAndVariance(all, numSegments * docsPerSegment);

      ExecutorService executor =
          Executors.newFixedThreadPool(4, new NamedThreadFactory("v1-dvstats"));
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(numSegments, reader.leaves().size());

        IndexSearcher serial = new IndexSearcher(reader);
        IndexSearcher concurrent = concurrentFactory.create(reader, executor);
        assertTrue(
            "precondition: need >= " + minSlices + " slices, got " + concurrent.getSlices().length,
            concurrent.getSlices().length >= minSlices);

        double[] s = stats(serial, sortedNumeric);
        double[] c = stats(concurrent, sortedNumeric);
        String report =
            String.format(
                java.util.Locale.ROOT,
                "%s n=%d segments=%d slices=%d%n exact      mean=%s variance=%s%n"
                    + " serial     mean=%s variance=%s count=%d%n"
                    + " concurrent mean=%s variance=%s count=%d",
                sortedNumeric ? "SortedLongDocValuesStats" : "LongDocValuesStats",
                all.size(),
                numSegments,
                concurrent.getSlices().length,
                exact[0],
                exact[1],
                s[0],
                s[1],
                (long) s[2],
                c[0],
                c[1],
                (long) c[2]);
        // Control: the serial (no executor) path must be right, otherwise the oracle is wrong.
        assertTrue(
            "serial path disagrees with exact reference:\n" + report,
            close(exact[0], s[0], 1e-12) && close(exact[1], s[1], 1e-6));
        assertEquals(report, (long) s[2], (long) c[2]); // count is unaffected
        // The claim: the concurrent path must give the same statistics as the serial path.
        assertTrue(
            "concurrent mean/variance differ from serial/exact:\n" + report,
            close(exact[0], c[0], 1e-12) && close(exact[1], c[1], 1e-6));
      } finally {
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
      }
    }
  }

  /** returns {mean, variance, count} */
  private static double[] stats(IndexSearcher searcher, boolean sortedNumeric) throws Exception {
    if (sortedNumeric) {
      Supplier<DocValuesStats.SortedLongDocValuesStats> sup =
          () -> new DocValuesStats.SortedLongDocValuesStats("ts");
      DocValuesStats.SortedLongDocValuesStats st =
          searcher.search(MatchAllDocsQuery.INSTANCE, new DocValuesStatsCollectorManager<>(sup));
      return new double[] {st.mean(), st.variance(), st.valuesCount()};
    } else {
      Supplier<DocValuesStats.LongDocValuesStats> sup =
          () -> new DocValuesStats.LongDocValuesStats("ts");
      DocValuesStats.LongDocValuesStats st =
          searcher.search(MatchAllDocsQuery.INSTANCE, new DocValuesStatsCollectorManager<>(sup));
      return new double[] {st.mean(), st.variance(), st.count()};
    }
  }

  /** Default IndexSearcher(reader, executor) slicing: 6 segments -> slices of 5 + 1 segments. */
  public void testLongDefaultSlicing() throws Exception {
    check(false, 6, 4, 1, BASE_NANOS, HOUR_NANOS, IndexSearcher::new, 2);
  }

  /**
   * 3 one-segment slices of 4 values: no slice's own sum overflows (4 * 1.7e18 < 9.22e18); only the
   * running sum in the reduce does.
   */
  public void testLongRunningReduceSum() throws Exception {
    check(false, 3, 4, 1, BASE_NANOS, HOUR_NANOS, TestDvStatsMergeOverflowV1::oneSegmentPerSlice, 3);
  }

  /** Sorted-numeric variant, 2 values per doc, default slicing. */
  public void testSortedLongDefaultSlicing() throws Exception {
    check(true, 6, 2, 2, BASE_NANOS, HOUR_NANOS, IndexSearcher::new, 2);
  }

  /** Control: same layout and slicing with small values (sum fits in a long) must pass. */
  public void testControlSmallValues() throws Exception {
    check(false, 6, 4, 1, 1_000_000L, 1_000L, IndexSearcher::new, 2);
    check(true, 6, 2, 2, 1_000_000L, 1_000L, IndexSearcher::new, 2);
  }
}
