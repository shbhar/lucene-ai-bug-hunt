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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DoubleRange#accept (and therefore DoubleRange#getQuery, the drill-down query) compares with
 * primitive {@code >=}/{@code <=}, where -0.0 == +0.0. DoubleRangeFacetCounts counts in
 * NumericUtils.doubleToSortableLong space, where -0.0 < +0.0. A document whose value is a zero of
 * the opposite sign to an inclusive zero bound is accepted by the range and found by its drill-down
 * query, but not counted by the facet (and a range [+0.0, -0.0], which DoubleRange accepts, makes
 * the facet constructor throw).
 */
public class TestDoubleRangeSignedZero extends LuceneTestCase {

  private static void check(double docValue, DoubleRange range) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new DoubleDocValuesField("f", docValue));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        FacetsCollector fc = s.search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
        assertTrue(range + " accepts " + docValue, range.accept(docValue));
        int drillDown = s.count(range.getQuery(null, DoubleValuesSource.fromDoubleField("f")));
        assertEquals("drill-down count", 1, drillDown);
        int fieldCount =
            new DoubleRangeFacetCounts("f", fc, range).getAllChildren("f").labelValues[0]
                .value.intValue();
        int sourceCount =
            new DoubleRangeFacetCounts("f", DoubleValuesSource.fromDoubleField("f"), fc, range)
                .getAllChildren("f").labelValues[0].value.intValue();
        assertEquals("facet count (field constructor) vs drill-down", drillDown, fieldCount);
        assertEquals("facet count (DoubleValuesSource) vs drill-down", drillDown, sourceCount);
      }
    }
  }

  /** -0.0 is what e.g. Math.ceil(-0.25) or -1 * 0.0 produce. */
  public void testNegativeZeroValueInRangeStartingAtZero() throws Exception {
    assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(Math.ceil(-0.25)));
    check(-0.0, new DoubleRange("[0, 1]", 0.0, true, 1.0, true)); // facet count 0 on main
  }

  public void testPositiveZeroValueInRangeEndingAtNegativeZero() throws Exception {
    check(0.0, new DoubleRange("[-1, -0.0]", -1.0, true, -0.0, true)); // facet count 0 on main
  }

  public void testRangeFromPositiveZeroToNegativeZero() throws Exception {
    // the DoubleRange constructor accepts it (0.0 > -0.0 is false) and the range accepts both
    // zeros, but DoubleRangeFacetCounts throws IllegalArgumentException("... matches nothing")
    check(0.0, new DoubleRange("[0.0, -0.0]", 0.0, true, -0.0, true));
  }
}
