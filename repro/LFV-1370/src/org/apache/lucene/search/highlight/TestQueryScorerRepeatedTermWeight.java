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
package org.apache.lucene.search.highlight;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestQueryScorerRepeatedTermWeight extends LuceneTestCase {

  public void testClauseOrderDoesNotChangeBestFragment() throws Exception {
    Query alpha = new TermQuery(new Term("f", "alpha"));
    Query alpha5 = new BoostQuery(alpha, 5f);
    Query beta3 = new BoostQuery(new TermQuery(new Term("f", "beta")), 3f);
    Query q1 = bool(alpha5, beta3, alpha);
    Query q2 = bool(alpha, beta3, alpha5);
    assertEquals(q1, q2);
    // expected: both pick the alpha fragment (alpha weighs max(5, 1)); actual: q1 picks the beta one
    assertEquals(bestFragment(q2), bestFragment(q1));
  }

  private static Query bool(Query... clauses) {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (Query c : clauses) {
      b.add(c, Occur.SHOULD);
    }
    return b.build();
  }

  private static String bestFragment(Query q) throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    Highlighter highlighter = new Highlighter(new QueryScorer(q, "f"));
    highlighter.setTextFragmenter(new SimpleFragmenter(6));
    return highlighter.getBestFragment(analyzer, "f", "alpha beta");
  }
}
