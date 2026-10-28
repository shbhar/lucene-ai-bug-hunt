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
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The monitor package javadoc says that ScoringMatch.matchWithSimilarity "returns the set of
 * matching queries, with the score that each one records against a Document", and ExplainingMatch
 * is "similar to ScoringMatch, but include[s] the full Explanation".
 *
 * <p>The default QueryDecomposer splits a DisjunctionMaxQuery into its disjuncts, and
 * ScoringMatch/ExplainingMatch resolve() add the parts' scores, so a registered
 * DisjunctionMaxQuery is reported with the SUM of its matching disjuncts instead of max + tie *
 * others. Reference: the same query searched on a one-document index of the same document (the
 * Monitor itself matches a single document against a MemoryIndex). Public API only.
 */
public class TestMonitorDisMaxScore extends LuceneTestCase {

  private static final Analyzer ANALYZER = new WhitespaceAnalyzer();
  private static final BM25Similarity SIM = new BM25Similarity();

  private static Document doc() {
    Document doc = new Document();
    doc.add(new TextField("title", "apple", Field.Store.NO));
    doc.add(new TextField("body", "apple pie recipe", Field.Store.NO));
    return doc;
  }

  /** The score the query records against the document on a one-document index. */
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

  private static Query dismax(float tie) {
    return new DisjunctionMaxQuery(
        List.of(new TermQuery(new Term("title", "apple")), new TermQuery(new Term("body", "apple"))),
        tie);
  }

  /** The usual multi-field DisjunctionMaxQuery (tie 0): reported as the sum, not the max. */
  public void testDisMaxTieZero() throws IOException {
    assertScores(dismax(0f));
  }

  /** tie 0.1, the value TestQueryDecomposer uses. */
  public void testDisMaxTieOneTenth() throws IOException {
    assertScores(dismax(0.1f));
  }

  /** Control: tie 1 scores like a disjunction, so the summed parts are right. Passes. */
  public void testDisMaxTieOneControl() throws IOException {
    assertScores(dismax(1f));
  }

  /** Control: the equivalent BooleanQuery disjunction is reported correctly. Passes. */
  public void testBooleanDisjunctionControl() throws IOException {
    assertScores(
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("title", "apple")), BooleanClause.Occur.SHOULD)
            .add(new TermQuery(new Term("body", "apple")), BooleanClause.Occur.SHOULD)
            .build());
  }
}
