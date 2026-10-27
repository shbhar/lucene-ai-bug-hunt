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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.MultiDoubleValues;
import org.apache.lucene.facet.MultiDoubleValuesSource;
import org.apache.lucene.facet.MultiLongValues;
import org.apache.lucene.facet.MultiLongValuesSource;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Multi-valued range facet counts must not depend on the order in which a document's values arrive:
 * a range counts every document with at least one value in it, which is exactly what the range's
 * own drill-down query matches. MultiLongValues and MultiDoubleValues promise no order.
 *
 * <p>Every test indexes one document and compares each range's facet count, and the result's total,
 * with the drill-down count of the same range over the same values.
 */
public class TestRangeFacetMultiValuedOrder extends LuceneTestCase {

  private static final LongRange[] LONG_RANGES = {
    new LongRange("[0, 10)", 0, true, 10, false), new LongRange("[10, 100)", 10, true, 100, false)
  };

  private static final DoubleRange[] DISTANCE_RANGES = {
    new DoubleRange("< 1 km", 0.0, true, 1.0, false),
    new DoubleRange("< 2 km", 0.0, true, 2.0, false),
    new DoubleRange("< 5 km", 0.0, true, 5.0, false)
  };

  private static final DoubleRange[] NEGATIVE_RANGES = {
    new DoubleRange("[-10, -2]", -10.0, true, -2.0, true),
    new DoubleRange("[-2, 0]", -2.0, true, 0.0, true)
  };

  /** A document with two locations, 3.0 km and 0.5 km from the user (overlapping ranges). */
  public void testDistancesFromCustomSource() throws Exception {
    MultiDoubleValuesSource distances = new FixedDoubles(3.0, 0.5);
    try (Directory dir = newDirectory()) {
      index(dir, new Document());
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Facets facets = new DoubleRangeFacetCounts("d", distances, hits(s), DISTANCE_RANGES);
        assertCounts(s, facets, "d", drillDowns(DISTANCE_RANGES, distances), new int[] {1, 1, 1});
      }
    }
  }

  /** Custom long values {30, 5} with non-overlapping ranges: no floating point, no encoding. */
  public void testLongValuesFromCustomSource() throws Exception {
    MultiLongValuesSource values = new FixedLongs(30, 5);
    try (Directory dir = newDirectory()) {
      index(dir, new Document());
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Facets facets = new LongRangeFacetCounts("x", values, hits(s), LONG_RANGES);
        assertCounts(s, facets, "x", drillDowns(LONG_RANGES, values), new int[] {1, 1});
      }
    }
  }

  /** Values {500, 5}: 5 is in [0, 10), 500 in no range. The document drops out of the total. */
  public void testDocumentDropsOutOfTotal() throws Exception {
    MultiLongValuesSource values = new FixedLongs(500, 5);
    try (Directory dir = newDirectory()) {
      index(dir, new Document());
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Facets facets = new LongRangeFacetCounts("x", values, hits(s), LONG_RANGES);
        assertCounts(s, facets, "x", drillDowns(LONG_RANGES, values), new int[] {1, 0});
      }
    }
  }

  /**
   * Doubles -1.0 and -5.0 indexed as raw bits (the DoubleDocValuesField encoding, which
   * fromDoubleField decodes). SortedNumeric sorts the raw bits as signed longs, which returns -1.0
   * before -5.0.
   */
  public void testRawBitDoublesFromDoubleField() throws Exception {
    MultiDoubleValuesSource source = MultiDoubleValuesSource.fromDoubleField("f");
    try (Directory dir = newDirectory()) {
      index(dir, rawBitDoubles(-1.0, -5.0));
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Facets facets = new DoubleRangeFacetCounts("f", source, hits(s), NEGATIVE_RANGES);
        assertCounts(s, facets, "f", drillDowns(NEGATIVE_RANGES, source), new int[] {1, 1});
      }
    }
  }

  /** The same document through the constructor that reads the field itself. */
  public void testRawBitDoublesFieldConstructor() throws Exception {
    MultiDoubleValuesSource source = MultiDoubleValuesSource.fromDoubleField("f");
    try (Directory dir = newDirectory()) {
      index(dir, rawBitDoubles(-1.0, -5.0));
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Facets facets = new DoubleRangeFacetCounts("f", hits(s), NEGATIVE_RANGES);
        assertCounts(s, facets, "f", drillDowns(NEGATIVE_RANGES, source), new int[] {1, 1});
      }
    }
  }

  /** Control: the same values in ascending order are counted correctly today. */
  public void testAscendingOrderControl() throws Exception {
    MultiDoubleValuesSource distances = new FixedDoubles(0.5, 3.0);
    MultiLongValuesSource values = new FixedLongs(5, 30);
    try (Directory dir = newDirectory()) {
      index(dir, rawBitDoubles(-5.0, 0.0)); // one negative value: delivered ascending
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        FacetsCollector fc = hits(s);
        assertCounts(
            s,
            new DoubleRangeFacetCounts("d", distances, fc, DISTANCE_RANGES),
            "d",
            drillDowns(DISTANCE_RANGES, distances),
            new int[] {1, 1, 1});
        assertCounts(
            s,
            new LongRangeFacetCounts("x", values, fc, LONG_RANGES),
            "x",
            drillDowns(LONG_RANGES, values),
            new int[] {1, 1});
        assertCounts(
            s,
            new DoubleRangeFacetCounts("f", fc, NEGATIVE_RANGES),
            "f",
            drillDowns(NEGATIVE_RANGES, MultiDoubleValuesSource.fromDoubleField("f")),
            new int[] {1, 1});
      }
    }
  }

  private static void index(Directory dir, Document doc) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(doc);
    }
  }

  private static Document rawBitDoubles(double... values) {
    Document doc = new Document();
    for (double v : values) {
      doc.add(new SortedNumericDocValuesField("f", Double.doubleToRawLongBits(v)));
    }
    return doc;
  }

  private static FacetsCollector hits(IndexSearcher s) throws IOException {
    return s.search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
  }

  private static Query[] drillDowns(DoubleRange[] ranges, MultiDoubleValuesSource source) {
    return Arrays.stream(ranges).map(r -> r.getQuery(null, source)).toArray(Query[]::new);
  }

  private static Query[] drillDowns(LongRange[] ranges, MultiLongValuesSource source) {
    return Arrays.stream(ranges).map(r -> r.getQuery(null, source)).toArray(Query[]::new);
  }

  /**
   * Checks that the drill-down counts are the expected ones, then that the facet counts equal them
   * and the result's total is 1 (the single document has a value in at least one range).
   */
  private static void assertCounts(
      IndexSearcher s, Facets facets, String dim, Query[] drillDowns, int[] expected)
      throws IOException {
    int[] drillDown = new int[drillDowns.length];
    for (int i = 0; i < drillDowns.length; i++) {
      drillDown[i] = s.count(drillDowns[i]);
    }
    assertArrayEquals("drill-down counts", expected, drillDown);
    FacetResult res = facets.getAllChildren(dim);
    int[] facet = new int[res.labelValues.length];
    for (int i = 0; i < facet.length; i++) {
      facet[i] = res.labelValues[i].value.intValue();
    }
    String want = "counts " + Arrays.toString(drillDown) + ", total 1";
    String got = "counts " + Arrays.toString(facet) + ", total " + res.value;
    if (want.equals(got) == false) {
      fail("facet result vs drill-down: expected " + want + ", got " + got);
    }
  }

  /** Returns the same values, in the given order, for every document. */
  private static final class FixedDoubles extends MultiDoubleValuesSource {
    private final double[] values;

    FixedDoubles(double... values) {
      this.values = values;
    }

    @Override
    public MultiDoubleValues getValues(LeafReaderContext ctx) {
      return new MultiDoubleValues() {
        int upto;

        @Override
        public long getValueCount() {
          return values.length;
        }

        @Override
        public double nextValue() {
          return values[upto++];
        }

        @Override
        public boolean advanceExact(int doc) {
          upto = 0;
          return true;
        }
      };
    }

    @Override
    public boolean isCacheable(LeafReaderContext ctx) {
      return true;
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(values);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof FixedDoubles f && Arrays.equals(values, f.values);
    }

    @Override
    public String toString() {
      return "FixedDoubles" + Arrays.toString(values);
    }
  }

  /** Returns the same values, in the given order, for every document. */
  private static final class FixedLongs extends MultiLongValuesSource {
    private final long[] values;

    FixedLongs(long... values) {
      this.values = values;
    }

    @Override
    public MultiLongValues getValues(LeafReaderContext ctx) {
      return new MultiLongValues() {
        int upto;

        @Override
        public long getValueCount() {
          return values.length;
        }

        @Override
        public long nextValue() {
          return values[upto++];
        }

        @Override
        public boolean advanceExact(int doc) {
          upto = 0;
          return true;
        }
      };
    }

    @Override
    public boolean isCacheable(LeafReaderContext ctx) {
      return true;
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(values);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof FixedLongs f && Arrays.equals(values, f.values);
    }

    @Override
    public String toString() {
      return "FixedLongs" + Arrays.toString(values);
    }
  }
}
