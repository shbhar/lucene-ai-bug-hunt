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
package org.apache.lucene.monitor;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.spans.SpanNearQuery;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Monitor with ExplainingMatch.MATCHER must report the same matches as QueryMatch.SIMPLE_MATCHER.
 * A registered query that has a span query inside a FILTER clause is reported as an error instead,
 * because IndexSearcher#explain throws for it.
 */
public class TestExplainingMatchSpanFilter extends LuceneTestCase {

  public void testExplainingMatcherReportsSameMatchesAsSimpleMatcher() throws IOException {
    // The production default of IndexSearcher on main is no query cache; LuceneTestCase installs a
    // randomly caching one for the duration of the test (and restores the default afterwards).
    IndexSearcher.setDefaultQueryCache(null);
    try (Monitor monitor =
        new Monitor(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))) {
      Query brownFox =
          new SpanNearQuery(
              new SpanQuery[] {
                new SpanTermQuery(new Term("body", "brown")),
                new SpanTermQuery(new Term("body", "fox"))
              },
              0,
              true);
      Query filter =
          new BooleanQuery.Builder()
              .add(brownFox, BooleanClause.Occur.SHOULD)
              .add(new TermQuery(new Term("body", "dog")), BooleanClause.Occur.SHOULD)
              .build();
      Query q1 =
          new BooleanQuery.Builder()
              .add(new TermQuery(new Term("body", "quick")), BooleanClause.Occur.MUST)
              .add(filter, BooleanClause.Occur.FILTER)
              .build();
      monitor.register(
          new MonitorQuery("q1", q1), new MonitorQuery("q2", new TermQuery(new Term("body", "quick"))));

      Document doc = new Document();
      doc.add(new TextField("body", "quick brown fox", Field.Store.NO));

      MatchingQueries<QueryMatch> simple = monitor.match(doc, QueryMatch.SIMPLE_MATCHER);
      assertEquals(2, simple.getMatchCount());
      assertTrue(simple.getErrors().isEmpty());

      MatchingQueries<ExplainingMatch> explained = monitor.match(doc, ExplainingMatch.MATCHER);
      assertEquals("errors: " + explained.getErrors(), 0, explained.getErrors().size());
      assertEquals(2, explained.getMatchCount());
    }
  }
}
