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
package org.apache.lucene.facet.rangeonrange;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleRangeDocValuesField;
import org.apache.lucene.document.RangeFieldQuery;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Same root cause as TestRangeFieldSignedZeroBounds, seen through range-on-range faceting: a facet
 * range [0.0, -0.0] counts a document indexed with [0.0, -0.0] as WITHIN and CONTAINS but not as
 * INTERSECTS.
 */
public class TestRangeOnRangeSignedZeroBounds extends LuceneTestCase {

  private static int count(FacetsCollector fc, RangeFieldQuery.QueryType type) throws Exception {
    DoubleRange range = new DoubleRange("z", new double[] {0.0}, new double[] {-0.0});
    FacetResult r = new DoubleRangeOnRangeFacetCounts("r", fc, type, range).getAllChildren("r");
    return r.labelValues[0].value.intValue();
  }

  private static IndexReader readerWith(Directory dir, DoubleRangeDocValuesField field)
      throws Exception {
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(field);
    w.addDocument(doc);
    IndexReader reader = w.getReader();
    w.close();
    return reader;
  }

  @SuppressWarnings("deprecation") // MatchAllDocsQuery() keeps the test compilable on 9.x and 10.x
  public void testFacetRangeIntersectsItself() throws Exception {
    DoubleRangeDocValuesField field;
    try {
      field = new DoubleRangeDocValuesField("r", new double[] {0.0}, new double[] {-0.0});
    } catch (IllegalArgumentException rejected) {
      return; // rejecting the inverted range is a consistent outcome
    }
    try (Directory dir = newDirectory();
        IndexReader reader = readerWith(dir, field)) {
      IndexSearcher s = newSearcher(reader);
      FacetsCollector fc = s.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
      int within, contains, intersects;
      try {
        within = count(fc, RangeFieldQuery.QueryType.WITHIN);
        contains = count(fc, RangeFieldQuery.QueryType.CONTAINS);
        intersects = count(fc, RangeFieldQuery.QueryType.INTERSECTS);
      } catch (IllegalArgumentException rejected) {
        return; // rejecting the inverted facet range is a consistent outcome
      }
      assertEquals("WITHIN", 1, within);
      assertEquals("CONTAINS", 1, contains);
      assertEquals("a document WITHIN and CONTAINS a facet range must INTERSECT it", 1, intersects);
    }
  }
}
