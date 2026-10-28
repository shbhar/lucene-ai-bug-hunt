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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * QueryDecomposer collects the parts of a query in a Set, so a sub-query that occurs in two
 * clauses (two nested disjunctions sharing a term, a repeated term, a repeated MUST clause) is kept
 * once. BooleanQuery scores every clause (BooleanQuery#rewrite merges repeated SHOULD/MUST clauses
 * into one clause with the summed boost), so the query's score counts the shared part twice, while
 * ScoringMatch/ExplainingMatch add up the distinct parts and count it once.
 *
 * <p>Reference: the same query searched on a one-document index of the same document. Public API
 * only.
 */
public class TestMonitorRepeatedSubqueryScore extends LuceneTestCase {

  private static final Analyzer ANALYZER = new WhitespaceAnalyzer();
  private static final BM25Similarity SIM = new BM25Similarity();

  private static Document doc() {
    Document doc = new Document();
    doc.add(new TextField("body", "red green apple", Field.Store.NO));
    return doc;
  }

  private static float oneDocScore(Query q) throws IOException {
    IndexSearcher searcher = MemoryIndex.fromDocument(doc(), ANALYZER).createSearcher();
    searcher.setSimilarity(SIM);
    TopDocs td = searcher.search(q, 1);
    assertEquals("query must match the document", 1, td.scoreDocs.length);
    return td.scoreDocs[0].score;
  }

  private static void assertScores(Query q) throws IOException {
    float expected = oneDocScore(q);
    try (Monitor monitor = new Monitor(ANALYZER)) {
      monitor.register(new MonitorQuery("q", q));
      ScoringMatch sm = monitor.match(doc(), ScoringMatch.matchWithSimilarity(SIM)).matches("q");
      ExplainingMatch em = monitor.match(doc(), ExplainingMatch.MATCHER).matches("q");
      assertNotNull("ScoringMatch must report the matching query " + q, sm);
      assertNotNull("ExplainingMatch must report the matching query " + q, em);
      assertEquals("ScoringMatch score of " + q, expected, sm.getScore(), 1e-6f);
      assertEquals(
          "ExplainingMatch value of " + q,
          expected,
          em.getExplanation().getValue().floatValue(),
          1e-6f);
    }
  }

  private static Query t(String s) {
    return new TermQuery(new Term("body", s));
  }

  private static Query or(Query a, Query b) {
    return new BooleanQuery.Builder()
        .add(a, BooleanClause.Occur.SHOULD)
        .add(b, BooleanClause.Occur.SHOULD)
        .build();
  }

  /** "(red apple) (green apple)": two nested disjunctions that share the term apple. */
  public void testNestedDisjunctionsSharingATerm() throws IOException {
    assertScores(or(or(t("red"), t("apple")), or(t("green"), t("apple"))));
  }

  /** "apple apple" */
  public void testRepeatedShouldClause() throws IOException {
    assertScores(or(t("apple"), t("apple")));
  }

  /** "+apple +apple" */
  public void testRepeatedMustClause() throws IOException {
    assertScores(
        new BooleanQuery.Builder()
            .add(t("apple"), BooleanClause.Occur.MUST)
            .add(t("apple"), BooleanClause.Occur.MUST)
            .build());
  }

  /** Control: "(red apple) (green pie)", no shared part. Passes. */
  public void testNestedDisjunctionsDistinctControl() throws IOException {
    assertScores(or(or(t("red"), t("apple")), or(t("green"), t("pie"))));
  }
}
