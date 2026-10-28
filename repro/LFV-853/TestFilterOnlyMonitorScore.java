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
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check (public API only): a registered query whose only required clause is
 * Occur.FILTER must not be given a score by the Monitor's scoring matchers. Two oracles: (1) the
 * same query run with IndexSearcher on a one-document index; (2) the same Monitor configured with a
 * QueryDecomposer that does not split anything.
 */
public class TestFilterOnlyMonitorScore extends LuceneTestCase {

  private static final String F = "body";
  private static final String TEXT = "apple banana";

  private static Query term(String t) {
    return new TermQuery(new Term(F, t));
  }

  private static Document doc() {
    Document d = new Document();
    d.add(new TextField(F, TEXT, Field.Store.NO));
    return d;
  }

  /** A decomposer that never splits: the whole registered query is run as-is. */
  private static final class NoSplitDecomposer extends QueryDecomposer {
    @Override
    public Set<Query> decompose(Query q) {
      return Collections.singleton(q);
    }
  }

  private static Monitor monitor(Analyzer a, boolean split) throws IOException {
    MonitorConfiguration cfg = new MonitorConfiguration();
    if (split == false) {
      cfg.setQueryDecomposer(new NoSplitDecomposer());
    }
    return new Monitor(a, cfg);
  }

  /** Oracle 1: score of q on a one-document index, default similarity (BM25). */
  private static float searcherScore(Analyzer a, Query q) throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        w.addDocument(doc());
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        TopDocs td = s.search(q, 1);
        assertEquals("oracle: query must match the document", 1, td.scoreDocs.length);
        float explained = s.explain(q, 0).getValue().floatValue();
        assertEquals(td.scoreDocs[0].score, explained, 1e-6f);
        return td.scoreDocs[0].score;
      }
    }
  }

  private static Double explainValue(Monitor m, Query q) throws IOException {
    m.register(new MonitorQuery("q", q));
    MatchingQueries<ExplainingMatch> res = m.match(doc(), ExplainingMatch.MATCHER);
    ExplainingMatch em = res.matches("q");
    return em == null ? null : em.getExplanation().getValue().doubleValue();
  }

  private static Float scoringValue(Monitor m, Query q) throws IOException {
    m.register(new MonitorQuery("q", q));
    MatchingQueries<ScoringMatch> res = m.match(doc(), ScoringMatch.DEFAULT_MATCHER);
    ScoringMatch sm = res.matches("q");
    return sm == null ? null : sm.getScore();
  }

  private static boolean simpleMatch(Monitor m, Query q) throws IOException {
    m.register(new MonitorQuery("q", q));
    return m.match(doc(), QueryMatch.SIMPLE_MATCHER).matches("q") != null;
  }

  /**
   * Runs q through the stock Monitor and through the no-split Monitor and compares against the
   * IndexSearcher score. Returns a description of any disagreement, or null.
   */
  private static String check(Query q) throws IOException {
    Analyzer a = new StandardAnalyzer();
    float expected = searcherScore(a, q);
    StringBuilder sb = new StringBuilder();
    Double exStock, exNoSplit;
    Float scStock, scNoSplit;
    boolean mStock, mNoSplit;
    try (Monitor m = monitor(a, true)) {
      exStock = explainValue(m, q);
    }
    try (Monitor m = monitor(a, false)) {
      exNoSplit = explainValue(m, q);
    }
    try (Monitor m = monitor(a, true)) {
      scStock = scoringValue(m, q);
    }
    try (Monitor m = monitor(a, false)) {
      scNoSplit = scoringValue(m, q);
    }
    try (Monitor m = monitor(a, true)) {
      mStock = simpleMatch(m, q);
    }
    try (Monitor m = monitor(a, false)) {
      mNoSplit = simpleMatch(m, q);
    }
    String line =
        "query="
            + q
            + " searcherScore="
            + expected
            + " explain(stock)="
            + exStock
            + " explain(noSplit)="
            + exNoSplit
            + " scoring(stock)="
            + scStock
            + " scoring(noSplit)="
            + scNoSplit
            + " simple(stock)="
            + mStock
            + " simple(noSplit)="
            + mNoSplit;
    System.out.println("V1 " + line);
    // the no-split monitor must agree with IndexSearcher (sanity of the oracle)
    assertNotNull("oracle no-split explain must match: " + line, exNoSplit);
    assertEquals("oracle no-split explain: " + line, expected, exNoSplit, 1e-5);
    assertTrue("matching must be unaffected: " + line, mStock && mNoSplit);
    if (exStock == null || Math.abs(exStock - expected) > 1e-5) {
      sb.append("ExplainingMatch value ").append(exStock).append(" != ").append(expected).append("; ");
    }
    // ScoringMatch drops score <= 0 (ScoringMatch.java), so a reported score must be the real one
    if (scStock != null && Math.abs(scStock - expected) > 1e-5) {
      sb.append("ScoringMatch score ").append(scStock).append(" != ").append(expected).append("; ");
    }
    if (scNoSplit != null && Math.abs(scNoSplit - expected) > 1e-5) {
      throw new AssertionError("oracle no-split scoring disagrees: " + line);
    }
    return sb.length() == 0 ? null : sb + line;
  }

  private static void assertAgrees(Query q) throws IOException {
    String err = check(q);
    if (err != null) throw new AssertionError(err);
  }

  // ---- controls (expected to pass on stock code) ----

  public void testControlSingleMust() throws IOException {
    assertAgrees(new BooleanQuery.Builder().add(term("apple"), BooleanClause.Occur.MUST).build());
  }

  public void testControlFilterPlusMust() throws IOException {
    assertAgrees(
        new BooleanQuery.Builder()
            .add(term("apple"), BooleanClause.Occur.FILTER)
            .add(term("banana"), BooleanClause.Occur.MUST)
            .build());
  }

  public void testControlFilterPlusShouldNotDecomposed() throws IOException {
    assertAgrees(
        new BooleanQuery.Builder()
            .add(term("apple"), BooleanClause.Occur.FILTER)
            .add(term("banana"), BooleanClause.Occur.SHOULD)
            .build());
  }

  /** Two FILTER clauses are not decomposed: the stock Monitor scores the query 0, as it should. */
  public void testControlTwoFilterClausesNotDecomposed() throws IOException {
    assertAgrees(
        new BooleanQuery.Builder()
            .add(term("apple"), BooleanClause.Occur.FILTER)
            .add(term("banana"), BooleanClause.Occur.FILTER)
            .build());
  }

  // ---- the claim ----

  public void testDecomposerTurnsFilterIntoScoringQuery() {
    Query q = new BooleanQuery.Builder().add(term("apple"), BooleanClause.Occur.FILTER).build();
    Set<Query> parts = new QueryDecomposer().decompose(q);
    System.out.println("V1 decompose(" + q + ") = " + parts);
    // documents the root cause; not the oracle. A FILTER-only part would be fine; a bare TermQuery
    // (a scoring query) is what the claim says is produced.
    assertFalse(
        "decomposition of a FILTER-only query yields the bare scoring term query: " + parts,
        parts.equals(Collections.singleton(term("apple"))));
  }

  public void testSingleFilterClause() throws IOException {
    assertAgrees(new BooleanQuery.Builder().add(term("apple"), BooleanClause.Occur.FILTER).build());
  }

  public void testSingleFilterClauseWithExclusion() throws IOException {
    assertAgrees(
        new BooleanQuery.Builder()
            .add(term("apple"), BooleanClause.Occur.FILTER)
            .add(term("kiwi"), BooleanClause.Occur.MUST_NOT)
            .build());
  }

  public void testBoostedSingleFilterClause() throws IOException {
    assertAgrees(
        new BoostQuery(
            new BooleanQuery.Builder().add(term("apple"), BooleanClause.Occur.FILTER).build(), 2f));
  }

  public void testNestedFilterOnlyInsideDisjunction() throws IOException {
    Query inner = new BooleanQuery.Builder().add(term("apple"), BooleanClause.Occur.FILTER).build();
    assertAgrees(
        new BooleanQuery.Builder()
            .add(inner, BooleanClause.Occur.SHOULD)
            .add(term("banana"), BooleanClause.Occur.SHOULD)
            .build());
  }
}
