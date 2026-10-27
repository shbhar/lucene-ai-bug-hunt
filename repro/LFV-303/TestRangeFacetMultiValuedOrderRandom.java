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
package org.apache.lucene.facet.range;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.MultiDoubleValuesSource;
import org.apache.lucene.facet.MultiLongValues;
import org.apache.lucene.facet.MultiLongValuesSource;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Randomized check: multi-valued range facet counts, and the result's total, equal a brute-force
 * count whatever the order of each document's values. Three inputs: a custom source that delivers
 * each document's values in the (random) order they were generated, and raw-bit doubles in a
 * SortedNumeric field read through fromDoubleField and through the field constructor.
 */
public class TestRangeFacetMultiValuedOrderRandom extends LuceneTestCase {

  public void testRandomValueOrder() throws Exception {
    Random random = random();
    int numDocs = atLeast(200);
    long[][] values = new long[numDocs][];
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int d = 0; d < numDocs; d++) {
          values[d] = new long[TestUtil.nextInt(random, 1, 6)];
          Document doc = new Document();
          doc.add(new NumericDocValuesField("id", d));
          for (int j = 0; j < values[d].length; j++) {
            values[d][j] = TestUtil.nextLong(random, -50, 50); // duplicates allowed
            doc.add(new SortedNumericDocValuesField("f", Double.doubleToRawLongBits(values[d][j])));
          }
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        FacetsCollector fc = s.search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
        MultiLongValuesSource custom = new GeneratedOrder(values);
        MultiDoubleValuesSource rawBits = MultiDoubleValuesSource.fromDoubleField("f");
        int iters = atLeast(50);
        for (int iter = 0; iter < iters; iter++) {
          int numRanges = TestUtil.nextInt(random, 1, 5);
          LongRange[] longRanges = new LongRange[numRanges];
          DoubleRange[] doubleRanges = new DoubleRange[numRanges];
          for (int i = 0; i < numRanges; i++) {
            long a = TestUtil.nextLong(random, -60, 60);
            long b = TestUtil.nextLong(random, -60, 60);
            long min = Math.min(a, b);
            long max = Math.max(a, b);
            longRanges[i] = new LongRange("r" + i, min, true, max, true);
            doubleRanges[i] = new DoubleRange("r" + i, min, true, max, true);
          }
          String expected = bruteForce(values, longRanges);
          check("custom source", expected, new LongRangeFacetCounts("f", custom, fc, longRanges));
          check(
              "fromDoubleField over raw bits",
              expected,
              new DoubleRangeFacetCounts("f", rawBits, fc, doubleRanges));
          check(
              "field constructor over raw bits",
              expected,
              new DoubleRangeFacetCounts("f", fc, doubleRanges));
        }
      }
    }
  }

  /** Per range: the documents with at least one value in it; total: those with a value in any. */
  private static String bruteForce(long[][] values, LongRange[] ranges) {
    int[] counts = new int[ranges.length];
    int total = 0;
    for (long[] doc : values) {
      boolean inAnyRange = false;
      for (int i = 0; i < ranges.length; i++) {
        for (long v : doc) {
          if (v >= ranges[i].min && v <= ranges[i].max) {
            counts[i]++;
            inAnyRange = true;
            break;
          }
        }
      }
      if (inAnyRange) {
        total++;
      }
    }
    return "counts " + Arrays.toString(counts) + ", total " + total;
  }

  private static void check(String input, String expected, Facets facets) throws IOException {
    FacetResult res = facets.getAllChildren("f");
    int[] counts = new int[res.labelValues.length];
    for (int i = 0; i < counts.length; i++) {
      counts[i] = res.labelValues[i].value.intValue();
    }
    String got = "counts " + Arrays.toString(counts) + ", total " + res.value;
    if (expected.equals(got) == false) {
      fail(
          input + ", " + res.labelValues.length + " ranges: expected " + expected + ", got " + got);
    }
  }

  /** Delivers each document's values in the order they were generated (looked up by "id"). */
  private static final class GeneratedOrder extends MultiLongValuesSource {
    private final long[][] values;

    GeneratedOrder(long[][] values) {
      this.values = values;
    }

    @Override
    public MultiLongValues getValues(LeafReaderContext ctx) throws IOException {
      NumericDocValues ids = DocValues.getNumeric(ctx.reader(), "id");
      return new MultiLongValues() {
        long[] current;
        int upto;

        @Override
        public long getValueCount() {
          return current.length;
        }

        @Override
        public long nextValue() {
          return current[upto++];
        }

        @Override
        public boolean advanceExact(int doc) throws IOException {
          if (ids.advanceExact(doc) == false) {
            return false;
          }
          current = values[(int) ids.longValue()];
          upto = 0;
          return true;
        }
      };
    }

    @Override
    public boolean isCacheable(LeafReaderContext ctx) {
      return false;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object o) {
      return this == o;
    }

    @Override
    public String toString() {
      return "GeneratedOrder";
    }
  }
}
