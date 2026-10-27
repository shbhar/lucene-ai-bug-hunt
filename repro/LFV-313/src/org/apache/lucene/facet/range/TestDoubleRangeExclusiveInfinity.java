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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.facetset.DimRange;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.NumericUtils;

/**
 * An exclusive DoubleRange bound at an infinity is not excluded: the constructor moves an exclusive
 * min with Math.nextUp and an exclusive max with Math.nextAfter(max, -Infinity), and both return
 * the infinity unchanged. So (+Inf, +Inf] and [-Inf, -Inf) are accepted (no "matches nothing"
 * IllegalArgumentException, unlike LongRange at Long.MAX_VALUE/MIN_VALUE) and they match the
 * infinity itself, in accept(), in the drill-down query and in the facet count. The same
 * adjustment is copied in rangeonrange.DoubleRange and in facetset.DimRange.fromDoubles/fromFloats
 * (whose fromLongs counterpart throws for an exclusive Long.MAX_VALUE min).
 */
public class TestDoubleRangeExclusiveInfinity extends LuceneTestCase {

  public void testLongRangeRejectsTheAnalogousRange() {
    expectThrows(
        IllegalArgumentException.class,
        () -> new LongRange("> MAX", Long.MAX_VALUE, false, Long.MAX_VALUE, true));
    expectThrows(
        IllegalArgumentException.class,
        () -> new LongRange("< MIN", Long.MIN_VALUE, true, Long.MIN_VALUE, false));
  }

  public void testExclusiveMinAtPositiveInfinity() throws Exception {
    // "value > +Infinity" can match no double
    DoubleRange range;
    try {
      range = new DoubleRange("> +Inf", Double.POSITIVE_INFINITY, false, Double.POSITIVE_INFINITY, true);
    } catch (IllegalArgumentException expected) {
      return; // rejecting the empty range, as LongRange does, is also correct
    }
    assertFalse(range + " must not accept +Infinity", range.accept(Double.POSITIVE_INFINITY));
    assertEquals(0, countDocs(Double.POSITIVE_INFINITY, range));
  }

  public void testExclusiveMaxAtNegativeInfinity() throws Exception {
    // "value < -Infinity" can match no double
    DoubleRange range;
    try {
      range = new DoubleRange("< -Inf", Double.NEGATIVE_INFINITY, true, Double.NEGATIVE_INFINITY, false);
    } catch (IllegalArgumentException expected) {
      return;
    }
    assertFalse(range + " must not accept -Infinity", range.accept(Double.NEGATIVE_INFINITY));
    assertEquals(0, countDocs(Double.NEGATIVE_INFINITY, range));
  }

  public void testDimRangeFromDoublesAndFloats() {
    // control: the long factory rejects "> Long.MAX_VALUE"
    expectThrows(
        IllegalArgumentException.class,
        () -> DimRange.fromLongs(Long.MAX_VALUE, false, Long.MAX_VALUE, true));
    DimRange d;
    try {
      d = DimRange.fromDoubles(Double.POSITIVE_INFINITY, false, Double.POSITIVE_INFINITY, true);
    } catch (IllegalArgumentException expected) {
      d = null;
    }
    if (d != null) {
      long inf = NumericUtils.doubleToSortableLong(Double.POSITIVE_INFINITY);
      assertFalse(d + " (> +Inf) must not contain +Infinity", d.min() <= inf && inf <= d.max());
    }
    DimRange f;
    try {
      f = DimRange.fromFloats(Float.NEGATIVE_INFINITY, true, Float.NEGATIVE_INFINITY, false);
    } catch (IllegalArgumentException expected) {
      f = null;
    }
    if (f != null) {
      long inf = NumericUtils.floatToSortableInt(Float.NEGATIVE_INFINITY);
      assertFalse(f + " (< -Inf) must not contain -Infinity", f.min() <= inf && inf <= f.max());
    }
  }

  /** facet count of a one-document index; also checks the drill-down query agrees */
  private static int countDocs(double docValue, DoubleRange range) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new DoubleDocValuesField("f", docValue));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        FacetsCollector fc = s.search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
        int facet =
            new DoubleRangeFacetCounts("f", fc, range).getAllChildren("f").labelValues[0]
                .value.intValue();
        int drillDown = s.count(range.getQuery(null, DoubleValuesSource.fromDoubleField("f")));
        assertEquals(drillDown, facet);
        return facet;
      }
    }
  }
}
