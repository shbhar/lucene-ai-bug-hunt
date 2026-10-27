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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * QueryAnalyzer builds the presearcher's QueryTree from the terms a query reports to its
 * QueryVisitor. A query that matches no term reports nothing (TermInSetQuery with an empty set,
 * TermRangeQuery with lower > upper, ...). When such a query is a disjunct (the QueryDecomposer
 * registers each disjunct separately) or the whole query, QueryTree.conjunction throws
 * IllegalArgumentException("Cannot build a conjunction with no children"), so Monitor.register
 * rejects a valid query, and every other query in the same register() call is dropped with it.
 *
 * <p>Uses only public API.
 */
public class TestRegisterQueryWithoutTerms extends LuceneTestCase {

  private static final String FIELD = "text";

  public void testDisjunctionWithAnEmptyTermSetCanBeRegistered() throws IOException {
    // text:apple OR id:() -- the id list of the second clause happens to be empty
    Query query =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term(FIELD, "apple")), Occur.SHOULD)
            .add(new TermInSetQuery("id", List.<BytesRef>of()), Occur.SHOULD)
            .build();
    Document doc = new Document();
    doc.add(new TextField(FIELD, "apple", Field.Store.NO));
    try (Monitor monitor = new Monitor(new WhitespaceAnalyzer())) {
      monitor.register(
          new MonitorQuery("other", new TermQuery(new Term(FIELD, "apple"))),
          new MonitorQuery("q", query));
      MatchingQueries<QueryMatch> matches = monitor.match(doc, QueryMatch.SIMPLE_MATCHER);
      assertNotNull(matches.matches("q"));
      assertNotNull(matches.matches("other"));
    }
  }

  public void testQueriesThatMatchNoTermCanBeRegistered() throws IOException {
    Query[] queries = {
      new TermInSetQuery(FIELD, List.<BytesRef>of()),
      TermRangeQuery.newStringRange(FIELD, "m", "c", true, true), // lower > upper
    };
    List<String> rejected = new ArrayList<>();
    try (Monitor monitor = new Monitor(new WhitespaceAnalyzer())) {
      for (Query q : queries) {
        try {
          monitor.register(new MonitorQuery("q", q));
        } catch (IllegalArgumentException e) {
          rejected.add(q + " -> " + e.getMessage());
        }
      }
    }
    assertTrue("Monitor.register rejected valid queries: " + rejected, rejected.isEmpty());
  }
}
