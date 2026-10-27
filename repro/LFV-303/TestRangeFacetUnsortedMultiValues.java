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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.MultiDoubleValues;
import org.apache.lucene.facet.MultiDoubleValuesSource;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Multi-valued range facet counting drops matches when a document's values do not reach the
 * counter in ascending order. Neither MultiDoubleValues nor MultiLongValues promises any order, and
 * even Lucene's own MultiDoubleValuesSource.fromDoubleField (raw IEEE bits in a SortedNumeric
 * field) delivers two negative values in descending order.
 *
 * <p>Every test has one document and checks each range count against the document's values and
 * against the range's own drill-down query (DoubleRange#getQuery), which matches the document.
 */
public class TestRangeFacetUnsortedMultiValues extends LuceneTestCase {

  private static final DoubleRange[] RANGES = {
    new DoubleRange("[-10, -2]", -10.0, true, -2.0, true),
    new DoubleRange("[-2, 0]", -2.0, true, 0.0, true)
  };

  /**
   * One document with values -1.0 and -5.0, indexed as raw bits (the encoding fromDoubleField
   * decodes with Double::longBitsToDouble). -5.0 is in the first range, -1.0 in the second.
   */
  private static Directory indexRawBitsNegatives(Directory dir) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new SortedNumericDocValuesField("f", Double.doubleToRawLongBits(-1.0)));
      doc.add(new SortedNumericDocValuesField("f", Double.doubleToRawLongBits(-5.0)));
      w.addDocument(doc);
    }
    return dir;
  }

  private static void assertCounts(IndexSearcher s, FacetResult res, MultiDoubleValuesSource src)
      throws IOException {
    for (int i = 0; i < RANGES.length; i++) {
      int drillDown = s.count(RANGES[i].getQuery(null, src));
      assertEquals("drill-down of " + RANGES[i], 1, drillDown);
      assertEquals(
          "facet count of " + RANGES[i] + " (drill-down finds " + drillDown + ")",
          drillDown,
          res.labelValues[i].value.intValue());
    }
  }

  public void testFromDoubleFieldRawBits() throws Exception {
    try (Directory dir = indexRawBitsNegatives(newDirectory());
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      FacetsCollector fc = s.search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
      MultiDoubleValuesSource src = MultiDoubleValuesSource.fromDoubleField("f");
      FacetResult res = new DoubleRangeFacetCounts("f", src, fc, RANGES).getAllChildren("f");
      assertCounts(s, res, src); // [-10, -2] gets 0 on main
    }
  }

  public void testFieldConstructorRawBits() throws Exception {
    // the DoubleRangeFacetCounts(String, ...) constructor reads the SortedNumeric field itself and
    // maps each raw value with NumericUtils.sortableDoubleBits
    try (Directory dir = indexRawBitsNegatives(newDirectory());
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      FacetsCollector fc = s.search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
      FacetResult res = new DoubleRangeFacetCounts("f", fc, RANGES).getAllChildren("f");
      assertCounts(s, res, MultiDoubleValuesSource.fromDoubleField("f"));
    }
  }

  /**
   * A user-defined source, e.g. the distances from a query point to each of a document's
   * locations, has no reason to be sorted. Overlapping "&lt; 1 km / &lt; 2 km / &lt; 5 km" ranges.
   */
  public void testCustomSourceOverlappingRanges() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(new Document());
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        FacetsCollector fc = s.search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
        MultiDoubleValuesSource distances = new FixedValues(3.0, 0.5);
        DoubleRange[] ranges = {
          new DoubleRange("< 1 km", 0.0, true, 1.0, false),
          new DoubleRange("< 2 km", 0.0, true, 2.0, false),
          new DoubleRange("< 5 km", 0.0, true, 5.0, false)
        };
        FacetResult res = new DoubleRangeFacetCounts("d", distances, fc, ranges).getAllChildren("d");
        for (int i = 0; i < ranges.length; i++) {
          int drillDown = s.count(ranges[i].getQuery(null, distances));
          assertEquals("drill-down of " + ranges[i], 1, drillDown);
          // "< 1 km" and "< 2 km" get 0 on main: the 0.5 km value is skipped
          assertEquals("facet count of " + ranges[i], drillDown, res.labelValues[i].value.intValue());
        }
      }
    }
  }

  /** Returns the same values, in the given order, for every document. */
  private static final class FixedValues extends MultiDoubleValuesSource {
    private final double[] values;

    FixedValues(double... values) {
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
      return java.util.Arrays.hashCode(values);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof FixedValues f && java.util.Arrays.equals(values, f.values);
    }

    @Override
    public String toString() {
      return "FixedValues" + java.util.Arrays.toString(values);
    }
  }
}
