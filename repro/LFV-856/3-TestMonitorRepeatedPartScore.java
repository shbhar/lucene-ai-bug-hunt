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
import org.apache.lucene.index.Term;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent verifier test (public API only): the score that Monitor reports through ScoringMatch
 * / ExplainingMatch for a registered query should be the score that query records against the
 * document. Two oracles: (A) the same Monitor pipeline with a QueryDecomposer that keeps every
 * query whole, and (B) a plain IndexSearcher over a MemoryIndex of the same document.
 */
public class TestMonitorRepeatedPartScore extends LuceneTestCase {

  private static final String F = "body";
  private static final String TEXT = "red green apple";

  private static Query t(String s) {
    return new TermQuery(new Term(F, s));
  }

  private static Query or(Query a, Query b) {
    return new BooleanQuery.Builder().add(a, Occur.SHOULD).add(b, Occur.SHOULD).build();
  }

  private static Query and(Query a, Query b) {
    return new BooleanQuery.Builder().add(a, Occur.MUST).add(b, Occur.MUST).build();
  }

  private static Document doc() {
    Document d = new Document();
    d.add(new TextField(F, TEXT, Field.Store.NO));
    return d;
  }

  /** Decomposer that never splits a query: oracle A. */
  private static final class WholeQueryDecomposer extends QueryDecomposer {
    @Override
    public Set<Query> decompose(Query q) {
      return Collections.singleton(q);
    }
  }

  private static float monitorScore(Query q, QueryDecomposer decomposer) throws IOException {
    Analyzer analyzer = new StandardAnalyzer();
    MonitorConfiguration cfg = new MonitorConfiguration().setQueryDecomposer(decomposer);
    try (Monitor monitor = new Monitor(analyzer, cfg)) {
      monitor.register(new MonitorQuery("q", q));
      MatchingQueries<ScoringMatch> m = monitor.match(doc(), ScoringMatch.DEFAULT_MATCHER);
      ScoringMatch sm = m.matches("q");
      assertNotNull("query must match the document: " + q, sm);
      return sm.getScore();
    }
  }

  private static double explainingScore(Query q, QueryDecomposer decomposer) throws IOException {
    Analyzer analyzer = new StandardAnalyzer();
    MonitorConfiguration cfg = new MonitorConfiguration().setQueryDecomposer(decomposer);
    try (Monitor monitor = new Monitor(analyzer, cfg)) {
      monitor.register(new MonitorQuery("q", q));
      MatchingQueries<ExplainingMatch> m = monitor.match(doc(), ExplainingMatch.MATCHER);
      ExplainingMatch em = m.matches("q");
      assertNotNull("query must match the document: " + q, em);
      return em.getExplanation().getValue().doubleValue();
    }
  }

  /** Oracle B: plain IndexSearcher over a single-document MemoryIndex, BM25. */
  private static float searcherScore(Query q) throws IOException {
    MemoryIndex mi = new MemoryIndex(true, true);
    mi.addField(F, TEXT, new StandardAnalyzer());
    mi.freeze();
    IndexSearcher s = mi.createSearcher();
    s.setSimilarity(new BM25Similarity());
    TopDocs td = s.search(q, 1);
    assertEquals(1, td.scoreDocs.length);
    return td.scoreDocs[0].score;
  }

  private static void check(String label, Query q) throws IOException {
    float whole = monitorScore(q, new WholeQueryDecomposer());
    float plain = searcherScore(q);
    float dflt = monitorScore(q, new QueryDecomposer());
    System.out.println(
        "V1 "
            + label
            + " query=["
            + q
            + "] default-decomposer="
            + dflt
            + " whole-query-decomposer="
            + whole
            + " IndexSearcher="
            + plain);
    // the two oracles must agree with each other first
    assertEquals(label + ": oracle A vs oracle B", plain, whole, 1e-6f * Math.abs(plain));
    // the claim: default decomposition loses the repeated part's contribution
    assertEquals(
        label + ": ScoringMatch score with default QueryDecomposer",
        plain,
        dflt,
        1e-5f * Math.abs(plain));
  }

  public void testControlDistinctParts() throws IOException {
    // (red apple) (green pear): all parts distinct, decomposition must preserve the score
    check("control-distinct", or(or(t("red"), t("apple")), or(t("green"), t("pear"))));
  }

  public void testRepeatedShouldClause() throws IOException {
    check("repeated-should", or(t("apple"), t("apple")));
  }

  public void testRepeatedMustClause() throws IOException {
    check("repeated-must", and(t("apple"), t("apple")));
  }

  public void testSharedTermInTwoNestedDisjunctions() throws IOException {
    check("shared-nested", or(or(t("red"), t("apple")), or(t("green"), t("apple"))));
  }

  public void testQueryParserBuiltQuery() throws Exception {
    Query q = new QueryParser(F, new StandardAnalyzer()).parse("(red apple) (green apple)");
    check("queryparser", q);
  }

  public void testExplainingMatchRepeatedShould() throws IOException {
    Query q = or(t("apple"), t("apple"));
    double whole = explainingScore(q, new WholeQueryDecomposer());
    double dflt = explainingScore(q, new QueryDecomposer());
    System.out.println(
        "V1 explaining-repeated-should default-decomposer="
            + dflt
            + " whole-query-decomposer="
            + whole);
    assertEquals("ExplainingMatch value with default QueryDecomposer", whole, dflt, 1e-5 * whole);
  }
}
