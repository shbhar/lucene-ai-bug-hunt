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
import org.apache.lucene.document.LongRangeDocValuesField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.RangeFieldQuery;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.range.LongRangeFacetCounts;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Range facet counters compute FacetResult.value as hits.totalHits() minus the documents that were
 * checked and fell into no range. With a fastMatchQuery, the documents the fast-match query
 * filters out are never checked, so they are neither counted in a range nor subtracted: the total
 * includes them. Adding a fastMatchQuery that only removes documents outside every range (its
 * intended use) changes FacetResult.value although every per-range count stays the same.
 *
 * <p>FacetResult.value: "Total number of documents containing a value for this path".
 * FacetCountsWithFilterQuery.fastMatchQuery: "if specified, we first test this Query to see
 * whether the document should be checked for matching ranges".
 */
public class TestRangeOnRangeFastMatchTotalCount extends LuceneTestCase {

  private static IndexReader index(Directory dir) throws Exception {
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    for (long v : new long[] {0, 10, 20}) {
      Document doc = new Document();
      doc.add(new LongRangeDocValuesField("rr", new long[] {v}, new long[] {v + 1}));
      doc.add(new NumericDocValuesField("v", v));
      w.addDocument(doc);
    }
    IndexReader r = w.getReader();
    w.close();
    return r;
  }

  @SuppressWarnings("deprecation") // MatchAllDocsQuery() keeps the test compilable on 9.x and 10.x
  public void testRangeOnRangeTotalWithFastMatch() throws Exception {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir)) {
      IndexSearcher s = newSearcher(reader);
      FacetsCollector fc = s.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
      LongRange range = new LongRange("r", new long[] {0}, new long[] {5});
      // a fast-match query that accepts exactly the documents whose range intersects [0, 5]
      Query fastMatch = LongRangeDocValuesField.newSlowIntersectsQuery("rr", new long[] {0}, new long[] {5});

      FacetResult plain =
          new LongRangeOnRangeFacetCounts("rr", fc, RangeFieldQuery.QueryType.INTERSECTS, range)
              .getAllChildren("rr");
      FacetResult withFastMatch =
          new LongRangeOnRangeFacetCounts(
                  "rr", fc, RangeFieldQuery.QueryType.INTERSECTS, fastMatch, range)
              .getAllChildren("rr");
      assertEquals("per-range count", 1, plain.labelValues[0].value.intValue());
      assertEquals("per-range count", 1, withFastMatch.labelValues[0].value.intValue());
      assertEquals("total without fastMatchQuery", 1, plain.value.intValue());
      assertEquals(
          "total must not change when a fastMatchQuery removes only documents outside every range",
          plain.value.intValue(),
          withFastMatch.value.intValue());
    }
  }

  /** The same accounting in the classic range facets (facet/range). */
  @SuppressWarnings("deprecation")
  public void testLongRangeFacetTotalWithFastMatch() throws Exception {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir)) {
      IndexSearcher s = newSearcher(reader);
      FacetsCollector fc = s.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
      org.apache.lucene.facet.range.LongRange range =
          new org.apache.lucene.facet.range.LongRange("r", 0, true, 5, true);
      Query fastMatch = org.apache.lucene.document.NumericDocValuesField.newSlowRangeQuery("v", 0, 5);
      FacetResult plain =
          new LongRangeFacetCounts("v", LongValuesSource.fromLongField("v"), fc, range)
              .getAllChildren("v");
      FacetResult withFastMatch =
          new LongRangeFacetCounts("v", LongValuesSource.fromLongField("v"), fc, fastMatch, range)
              .getAllChildren("v");
      assertEquals("per-range count", 1, withFastMatch.labelValues[0].value.intValue());
      assertEquals("total without fastMatchQuery", 1, plain.value.intValue());
      assertEquals(
          "total must not change when a fastMatchQuery removes only documents outside every range",
          plain.value.intValue(),
          withFastMatch.value.intValue());
    }
  }

}
