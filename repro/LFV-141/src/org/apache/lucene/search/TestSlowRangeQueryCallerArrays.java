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
package org.apache.lucene.search;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongRange;
import org.apache.lucene.document.LongRangeDocValuesField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The *RangeDocValuesField.newSlowIntersectsQuery factories encode the bounds when the query is
 * built (that is what the query matches), but equals/hashCode compare the caller's min/max arrays,
 * which the query keeps by reference. If the caller reuses its arrays for a second query, the two
 * queries match different documents yet are equal, so BooleanQuery de-duplicates them as the same
 * FILTER clause and the query cache serves one query's hits for the other. The points factories
 * (LongRange.newIntersectsQuery) encode into a fresh array and do not have this problem.
 *
 * <p>Query#equals: "a query will be equal to another only if it's an instance of the same class and
 * its document-filtering properties are identical to those of the other instance."
 */
public class TestSlowRangeQueryCallerArrays extends LuceneTestCase {

  private static IndexReader oneDoc(Directory dir) throws IOException {
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(new LongRangeDocValuesField("dv", new long[] {5}, new long[] {10}));
    doc.add(new LongRange("pt", new long[] {5}, new long[] {10}));
    w.addDocument(doc);
    IndexReader r = w.getReader();
    w.close();
    return r;
  }

  public void testReusedArraysInConjunction() throws IOException {
    long[] min = {0};
    long[] max = {3};
    Query noMatch = LongRangeDocValuesField.newSlowIntersectsQuery("dv", min, max); // [0, 3]
    min[0] = 5;
    max[0] = 6;
    Query match = LongRangeDocValuesField.newSlowIntersectsQuery("dv", min, max); // [5, 6]

    // the same pattern with the points factory: two different queries
    long[] pmin = {0};
    long[] pmax = {3};
    Query pNoMatch = LongRange.newIntersectsQuery("pt", pmin, pmax);
    pmin[0] = 5;
    pmax[0] = 6;
    Query pMatch = LongRange.newIntersectsQuery("pt", pmin, pmax);

    try (Directory dir = newDirectory();
        IndexReader reader = oneDoc(dir)) {
      IndexSearcher s = newSearcher(reader);
      s.setQueryCache(null);
      assertEquals("[0,3] does not intersect [5,10]", 0, s.count(noMatch));
      assertEquals("[5,6] intersects [5,10]", 1, s.count(match));
      assertNotEquals("points control", pNoMatch, pMatch);

      Query conjunction =
          new BooleanQuery.Builder()
              .add(match, BooleanClause.Occur.FILTER)
              .add(noMatch, BooleanClause.Occur.FILTER)
              .build();
      assertEquals(
          "[5,6] AND [0,3] must match nothing, the [0,3] clause must not be dropped",
          0,
          s.count(conjunction));
    }
  }

  public void testEqualsReflectsMatchedRange() {
    long[] min = {0};
    long[] max = {3};
    Query q1 = LongRangeDocValuesField.newSlowIntersectsQuery("dv", min, max);
    min[0] = 5;
    max[0] = 6;
    Query q2 = LongRangeDocValuesField.newSlowIntersectsQuery("dv", min, max);
    assertNotEquals("queries that match different ranges must not be equal", q1, q2);
  }

  public void testQueryCacheServesOtherQueryHits() throws IOException {
    long[] min = {0};
    long[] max = {3};
    Query noMatch = LongRangeDocValuesField.newSlowIntersectsQuery("dv", min, max); // [0, 3]
    min[0] = 5;
    max[0] = 6;
    Query match = LongRangeDocValuesField.newSlowIntersectsQuery("dv", min, max); // [5, 6]
    try (Directory dir = newDirectory();
        IndexReader reader = oneDoc(dir)) {
      IndexSearcher s = newSearcher(reader, false);
      s.setQueryCache(new LRUQueryCache(100, 1 << 20, ctx -> true, Float.POSITIVE_INFINITY));
      s.setQueryCachingPolicy(
          new QueryCachingPolicy() {
            @Override
            public void onUse(Query query) {}

            @Override
            public boolean shouldCache(Query query) {
              return true;
            }
          });
      assertEquals(0, s.search(new ConstantScoreQuery(noMatch), 10).scoreDocs.length);
      assertEquals(
          "[5,6] intersects [5,10]; the cached result of [0,3] must not be reused",
          1,
          s.search(new ConstantScoreQuery(match), 10).scoreDocs.length);
    }
  }
}
