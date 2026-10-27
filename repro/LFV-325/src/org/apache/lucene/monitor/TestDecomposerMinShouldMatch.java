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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A BooleanQuery with minimumNumberShouldMatch=1 requires at least one SHOULD clause to match; with
 * no SHOULD clause (or only SHOULD clauses that match nothing) it matches no document
 * (BooleanQuery.rewrite: "SHOULD clause count less than minimumNumberShouldMatch"). The default
 * QueryDecomposer only keeps such a query whole when minimumNumberShouldMatch > 1 or when the
 * decomposed SHOULD clauses are non-empty, otherwise it replaces the query by its single required
 * clause, so Monitor.match reports the query for every document matching that clause.
 *
 * <p>Uses only public API (Monitor, BooleanQuery, MemoryIndex).
 */
public class TestDecomposerMinShouldMatch extends LuceneTestCase {

  private static final String FIELD = "text";

  private static Query term(String t) {
    return new TermQuery(new Term(FIELD, t));
  }

  public void testMonitorDoesNotReportQueriesThatDoNotMatch() throws IOException {
    List<Query> queries = new ArrayList<>();
    // +text:alpha with minimumNumberShouldMatch=1 and no SHOULD clause
    queries.add(new BooleanQuery.Builder().add(term("alpha"), Occur.MUST).setMinimumNumberShouldMatch(1).build());
    // #text:alpha -text:omega with minimumNumberShouldMatch=1 and no SHOULD clause
    queries.add(
        new BooleanQuery.Builder()
            .add(term("alpha"), Occur.FILTER)
            .add(term("omega"), Occur.MUST_NOT)
            .setMinimumNumberShouldMatch(1)
            .build());
    // +text:alpha with one SHOULD clause that is a pure-negative (match-nothing) BooleanQuery
    queries.add(
        new BooleanQuery.Builder()
            .add(term("alpha"), Occur.MUST)
            .add(new BooleanQuery.Builder().add(term("omega"), Occur.MUST_NOT).build(), Occur.SHOULD)
            .setMinimumNumberShouldMatch(1)
            .build());

    Analyzer analyzer = new WhitespaceAnalyzer();
    Document doc = new Document();
    doc.add(new TextField(FIELD, "alpha beta", Field.Store.NO));
    List<String> wrong = new ArrayList<>();
    try (Monitor monitor = new Monitor(analyzer)) {
      for (int i = 0; i < queries.size(); i++) {
        Query q = queries.get(i);
        // ground truth: the query does not match the document
        assertEquals(q.toString(), 0, MemoryIndex.fromDocument(doc, analyzer).createSearcher().count(q));
        monitor.register(new MonitorQuery("q" + i, q));
      }
      MatchingQueries<QueryMatch> matches = monitor.match(doc, QueryMatch.SIMPLE_MATCHER);
      for (int i = 0; i < queries.size(); i++) {
        if (matches.matches("q" + i) != null) {
          wrong.add(queries.get(i).toString());
        }
      }
    }
    assertTrue("Monitor.match reported queries that do not match the document: " + wrong, wrong.isEmpty());
  }
}
