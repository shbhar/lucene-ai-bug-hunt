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

import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestVectorSimilarityQueryFilterClauseCount extends LuceneTestCase {

  private static Query disjunction(String prefix, int n) {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (int i = 0; i < n; i++) {
      b.add(new TermQuery(new Term("f", prefix + i)), Occur.SHOULD);
    }
    return b.build();
  }

  public void testFilterClausesCountTowardsMaxClauseCount() throws Exception {
    Query query =
        new BooleanQuery.Builder()
            .add(disjunction("a", 600), Occur.MUST)
            .add(
                new FloatVectorSimilarityQuery.Adaptive(
                    "v", new float[] {1, 0}, 0f, disjunction("b", 600)),
                Occur.MUST)
            .build();
    IndexSearcher searcher = new IndexSearcher(new MultiReader());
    // expected: TooManyNestedClauses (1,200 term clauses > default maxClauseCount 1024); actual: the filter's 600 are not counted, no exception
    expectThrows(IndexSearcher.TooManyNestedClauses.class, () -> searcher.search(query, 1));
  }
}
