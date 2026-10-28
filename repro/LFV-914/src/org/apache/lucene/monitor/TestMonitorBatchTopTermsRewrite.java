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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.CommonTermsQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Monitor.match(Document[]) should report, for every document of the batch, the queries that
 * Monitor.match(Document) reports for that document alone. A registered FuzzyQuery with its
 * default settings (maxExpansions = 50, top-terms rewrite) is rewritten once over the terms of the
 * whole batch, so a document whose only matching term is not among the batch's 50 closest terms
 * silently loses the match. Uses only public monitor API.
 */
public class TestMonitorBatchTopTermsRewrite extends LuceneTestCase {

  private static Document doc(String text) {
    Document d = new Document();
    d.add(new TextField("f", text, Field.Store.NO));
    return d;
  }

  /** Default FuzzyQuery: 50 neighbours one edit away push out a document two edits away. */
  public void testDefaultFuzzyQueryInBatch() throws IOException {
    Analyzer analyzer = new StandardAnalyzer();
    try (Monitor monitor = new Monitor(analyzer)) {
      monitor.register(new MonitorQuery("fuzzy", new FuzzyQuery(new Term("f", "abcdef"))));

      List<Document> batch = new ArrayList<>();
      batch.add(doc("abcdxy")); // two edits from abcdef: matches the default FuzzyQuery
      // 50 documents, each with a different term one edit from abcdef
      for (char c = 'a'; c <= 'z' && batch.size() < 51; c++) {
        if (c != 'f') batch.add(doc("abcde" + c));
      }
      for (char c = 'a'; c <= 'z' && batch.size() < 51; c++) {
        if (c != 'e') batch.add(doc("abcd" + c + "f"));
      }
      assertEquals(51, batch.size());

      MatchingQueries<QueryMatch> alone = monitor.match(batch.get(0), QueryMatch.SIMPLE_MATCHER);
      assertNotNull("document 0 alone matches the fuzzy query", alone.matches("fuzzy"));

      MultiMatchingQueries<QueryMatch> inBatch =
          monitor.match(batch.toArray(new Document[0]), QueryMatch.SIMPLE_MATCHER);
      assertEquals(0, inBatch.getErrors().size());
      for (int i = 1; i < batch.size(); i++) {
        assertNotNull("neighbour " + i + " matches", inBatch.matches("fuzzy", i));
      }
      assertNotNull(
          "document 0 matches the fuzzy query alone, so it should also match it in a batch",
          inBatch.matches("fuzzy", 0));
    }
  }

  /** The same with an explicit maxExpansions of 1 and a batch of two documents. */
  public void testFuzzyQueryMaxExpansionsOneInTwoDocumentBatch() throws IOException {
    Analyzer analyzer = new StandardAnalyzer();
    try (Monitor monitor = new Monitor(analyzer)) {
      monitor.register(
          new MonitorQuery("fuzzy", new FuzzyQuery(new Term("f", "abcdef"), 2, 0, 1, true)));
      Document far = doc("abcdxy"); // two edits
      Document near = doc("abcdeg"); // one edit
      assertNotNull(monitor.match(far, QueryMatch.SIMPLE_MATCHER).matches("fuzzy"));
      assertNotNull(monitor.match(near, QueryMatch.SIMPLE_MATCHER).matches("fuzzy"));

      MultiMatchingQueries<QueryMatch> inBatch =
          monitor.match(new Document[] {far, near}, QueryMatch.SIMPLE_MATCHER);
      assertNotNull(inBatch.matches("fuzzy", 1));
      assertNotNull(
          "document 0 matches alone, so it should also match in the batch",
          inBatch.matches("fuzzy", 0));
    }
  }
  /**
   * CommonTermsQuery splits its terms by document frequency relative to maxDoc. Alone, 'ab' is a
   * low-frequency term (df 1 of 1 is not above ceil(0.5 * 1)), so the query matches. In a batch of
   * two copies of the same document, 'ab' becomes high-frequency (df 2 > ceil(0.5 * 2)) and only
   * the absent low-frequency term 'abc' is required, so neither copy matches.
   */
  public void testCommonTermsQueryInBatchOfTwoIdenticalDocuments() throws IOException {
    Analyzer analyzer = new StandardAnalyzer();
    try (Monitor monitor = new Monitor(analyzer)) {
      CommonTermsQuery q =
          new CommonTermsQuery(BooleanClause.Occur.MUST, BooleanClause.Occur.SHOULD, 0.5f);
      q.add(new Term("f", "ab"));
      q.add(new Term("f", "abc"));
      monitor.register(new MonitorQuery("common", q));
      Document d = doc("ab");
      assertNotNull(monitor.match(d, QueryMatch.SIMPLE_MATCHER).matches("common"));
      MultiMatchingQueries<QueryMatch> inBatch =
          monitor.match(new Document[] {doc("ab"), doc("ab")}, QueryMatch.SIMPLE_MATCHER);
      assertNotNull(
          "a document that matches alone should match in a batch of two copies of itself",
          inBatch.matches("common", 0));
    }
  }
}
