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
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * TooManyNestedClauses is thrown for "a Query that has more than getMaxClauseCount() total clauses
 * cumulatively in all of its children". Two disjunctions of 512 and 513 term clauses under MUST
 * (1025 clauses in total, default limit 1024) are accepted.
 */
public class TestNestedClauseLimitBoundary extends LuceneTestCase {

  private static BooleanQuery disjunction(int from, int to) {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (int i = from; i < to; i++) {
      b.add(new TermQuery(new Term("f", "t" + i)), BooleanClause.Occur.SHOULD);
    }
    return b.build();
  }

  public void testMaxClauseCountPlusOneNestedClausesAreRejected() throws Exception {
    int max = IndexSearcher.getMaxClauseCount(); // 1024
    Query q =
        new BooleanQuery.Builder()
            .add(disjunction(0, max / 2), BooleanClause.Occur.MUST)
            .add(disjunction(max / 2, max + 1), BooleanClause.Occur.MUST)
            .build();
    IndexSearcher searcher = new IndexSearcher(new MultiReader());
    expectThrows(IndexSearcher.TooManyNestedClauses.class, () -> searcher.rewrite(q));
  }
}
