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
package org.apache.lucene.monitorverify;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.monitor.ExplainingMatch;
import org.apache.lucene.monitor.MatchingQueries;
import org.apache.lucene.monitor.Monitor;
import org.apache.lucene.monitor.MonitorConfiguration;
import org.apache.lucene.monitor.MonitorQuery;
import org.apache.lucene.monitor.QueryDecomposer;
import org.apache.lucene.monitor.ScoringMatch;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: the score that Monitor reports for a registered DisjunctionMaxQuery (default
 * configuration, ScoringMatch.DEFAULT_MATCHER = BM25, and ExplainingMatch.MATCHER) compared with
 * the score that IndexSearcher gives the same query on a one-document index of the same document.
 * Public API only (test is in a different package from org.apache.lucene.monitor).
 */
public class TestMonitorDisMaxScore extends LuceneTestCase {

  private static final float EPS = 1e-6f;

  private static Document doc() {
    Document d = new Document();
    // different term frequencies, so the two disjunct scores differ (one-doc BM25: dl == avgdl)
    d.add(new TextField("title", "apple apple", Field.Store.NO));
    d.add(new TextField("body", "apple banana cherry date", Field.Store.NO));
    return d;
  }

  private static Query term(String field, String value) {
    return new TermQuery(new Term(field, value));
  }

  private static DisjunctionMaxQuery dismax(float tie, Query... disjuncts) {
    return new DisjunctionMaxQuery(Arrays.asList(disjuncts), tie);
  }

  /** Score of q for the single document, from a plain one-document index with BM25. */
  private static float referenceScore(Query q) throws IOException {
    try (Analyzer a = new StandardAnalyzer();
        Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(a);
      iwc.setSimilarity(new BM25Similarity());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc());
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        s.setSimilarity(new BM25Similarity());
        TopDocs td = s.search(q, 1);
        assertEquals("reference index must match " + q, 1, td.scoreDocs.length);
        float searchScore = td.scoreDocs[0].score;
        float explainScore = s.explain(q, td.scoreDocs[0].doc).getValue().floatValue();
        assertEquals("reference explain == search score for " + q, searchScore, explainScore, EPS);
        return searchScore;
      }
    }
  }

  private static Monitor newMonitor(Analyzer a, QueryDecomposer decomposer) throws IOException {
    if (decomposer == null) {
      return new Monitor(a);
    }
    return new Monitor(a, new MonitorConfiguration().setQueryDecomposer(decomposer));
  }

  private static float monitorScore(Query q, QueryDecomposer decomposer) throws IOException {
    try (Analyzer a = new StandardAnalyzer();
        Monitor m = newMonitor(a, decomposer)) {
      m.register(new MonitorQuery("q", q));
      MatchingQueries<ScoringMatch> res = m.match(doc(), ScoringMatch.DEFAULT_MATCHER);
      ScoringMatch sm = res.matches("q");
      assertNotNull("monitor must match " + q, sm);
      return sm.getScore();
    }
  }

  private static float monitorExplainValue(Query q) throws IOException {
    try (Analyzer a = new StandardAnalyzer();
        Monitor m = new Monitor(a)) {
      m.register(new MonitorQuery("q", q));
      MatchingQueries<ExplainingMatch> res = m.match(doc(), ExplainingMatch.MATCHER);
      ExplainingMatch em = res.matches("q");
      assertNotNull("monitor must match " + q, em);
      return em.getExplanation().getValue().floatValue();
    }
  }

  private static void assertMonitorScoreIsQueryScore(Query q) throws IOException {
    float expected = referenceScore(q);
    float actual = monitorScore(q, null);
    assertEquals("ScoringMatch score of " + q, expected, actual, EPS);
  }

  // ---------- controls (expected to pass) ----------

  /** Oracle sanity: each disjunct alone gets the same score from Monitor and the reference. */
  public void testControlSingleTermsAgree() throws IOException {
    assertMonitorScoreIsQueryScore(term("title", "apple"));
    assertMonitorScoreIsQueryScore(term("body", "apple"));
    float t = referenceScore(term("title", "apple"));
    float b = referenceScore(term("body", "apple"));
    assertTrue("disjunct scores must differ for the test to be meaningful", Math.abs(t - b) > 1e-3);
  }

  /** A BooleanQuery disjunction scores as a sum, so the sum-resolve is right here. */
  public void testControlBooleanDisjunction() throws IOException {
    Query q =
        new BooleanQuery.Builder()
            .add(term("title", "apple"), BooleanClause.Occur.SHOULD)
            .add(term("body", "apple"), BooleanClause.Occur.SHOULD)
            .build();
    assertMonitorScoreIsQueryScore(q);
  }

  /** tie == 1: DisMax score equals the sum, so the result is right. */
  public void testControlDisMaxTieOne() throws IOException {
    assertMonitorScoreIsQueryScore(dismax(1.0f, term("title", "apple"), term("body", "apple")));
  }

  /** Only one disjunct matches: max == sum, so the result is right. */
  public void testControlDisMaxOneDisjunctMatches() throws IOException {
    assertMonitorScoreIsQueryScore(dismax(0.0f, term("title", "apple"), term("body", "zebra")));
  }

  /**
   * Root attribution with public API only: a QueryDecomposer that keeps a DisjunctionMaxQuery
   * whole makes the Monitor report the correct score.
   */
  public void testControlNoDisMaxSplitGivesCorrectScore() throws IOException {
    QueryDecomposer keepDisMax =
        new QueryDecomposer() {
          @Override
          public Set<Query> decompose(Query q) {
            if (q instanceof DisjunctionMaxQuery) {
              return Collections.singleton(q);
            }
            return super.decompose(q);
          }
        };
    Query q = dismax(0.0f, term("title", "apple"), term("body", "apple"));
    assertEquals(
        "ScoringMatch score with non-splitting decomposer for " + q,
        referenceScore(q),
        monitorScore(q, keepDisMax),
        EPS);
  }

  // ---------- claim checks (fail if the bug is present) ----------

  public void testDisMaxTieZero() throws IOException {
    Query q = dismax(0.0f, term("title", "apple"), term("body", "apple"));
    float t = referenceScore(term("title", "apple"));
    float b = referenceScore(term("body", "apple"));
    // the reference agrees with DisjunctionMaxQuery's documented formula max + tie * others
    assertEquals(Math.max(t, b), referenceScore(q), EPS);
    assertMonitorScoreIsQueryScore(q);
  }

  public void testDisMaxTieHalf() throws IOException {
    Query q = dismax(0.5f, term("title", "apple"), term("body", "apple"));
    float t = referenceScore(term("title", "apple"));
    float b = referenceScore(term("body", "apple"));
    assertEquals(Math.max(t, b) + 0.5f * Math.min(t, b), referenceScore(q), EPS);
    assertMonitorScoreIsQueryScore(q);
  }

  public void testDisMaxNestedInBooleanShould() throws IOException {
    Query q =
        new BooleanQuery.Builder()
            .add(dismax(0.0f, term("title", "apple"), term("body", "apple")), BooleanClause.Occur.SHOULD)
            .add(term("body", "banana"), BooleanClause.Occur.SHOULD)
            .build();
    assertMonitorScoreIsQueryScore(q);
  }

  /**
   * DisjunctionMaxQuery's class javadoc example: for "albino elephant" over two fields, a document
   * with "albino" in one field and "elephant" in another must score higher than a document with
   * "albino" in both fields. Monitor.match on a batch of the two documents (one shared index).
   */
  public void testAlbinoElephantBatchRanking() throws IOException {
    Document a = new Document(); // albino in both fields
    a.add(new TextField("title", "albino", Field.Store.NO));
    a.add(new TextField("body", "albino", Field.Store.NO));
    Document b = new Document(); // albino in one field, elephant in the other
    b.add(new TextField("title", "albino", Field.Store.NO));
    b.add(new TextField("body", "elephant", Field.Store.NO));
    Query q =
        new BooleanQuery.Builder()
            .add(dismax(0.0f, term("title", "albino"), term("body", "albino")), BooleanClause.Occur.SHOULD)
            .add(
                dismax(0.0f, term("title", "elephant"), term("body", "elephant")),
                BooleanClause.Occur.SHOULD)
            .build();

    float refA, refB;
    try (Analyzer an = new StandardAnalyzer();
        Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(an);
      iwc.setSimilarity(new BM25Similarity());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(a);
        w.addDocument(b);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        s.setSimilarity(new BM25Similarity());
        refA = s.explain(q, 0).getValue().floatValue();
        refB = s.explain(q, 1).getValue().floatValue();
      }
    }
    assertTrue("reference: B (" + refB + ") must outscore A (" + refA + ")", refB > refA + 1e-3f);

    float monA, monB;
    try (Analyzer an = new StandardAnalyzer();
        Monitor m = new Monitor(an)) {
      m.register(new MonitorQuery("q", q));
      org.apache.lucene.monitor.MultiMatchingQueries<ScoringMatch> res =
          m.match(new Document[] {a, b}, ScoringMatch.DEFAULT_MATCHER);
      monA = res.matches("q", 0).getScore();
      monB = res.matches("q", 1).getScore();
    }
    assertTrue(
        "Monitor ScoringMatch: B (" + monB + ") must outscore A (" + monA + "); reference A=" + refA
            + " B=" + refB,
        monB > monA + 1e-3f);
    assertEquals("doc A score", refA, monA, EPS);
    assertEquals("doc B score", refB, monB, EPS);
  }

  public void testExplainingMatchDisMaxTieZero() throws IOException {
    Query q = dismax(0.0f, term("title", "apple"), term("body", "apple"));
    assertEquals("ExplainingMatch value of " + q, referenceScore(q), monitorExplainValue(q), EPS);
  }
}
