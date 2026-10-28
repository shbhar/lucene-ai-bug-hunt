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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A FILTER clause is "Like MUST except that these clauses do not participate in scoring"
 * (BooleanClause.Occur javadoc). A query registered in a Monitor should get from ScoringMatch and
 * ExplainingMatch the score that IndexSearcher gives it on the same document. Public API only; BM25
 * on both sides; one document, body "apple banana".
 */
public class TestMonitorFilterOnlyScore extends LuceneTestCase {

  private static final float EPS = 1e-6f;

  private static Document doc() {
    Document d = new Document();
    d.add(new TextField("body", "apple banana", Field.Store.NO));
    return d;
  }

  private static Query term(String text) {
    return new TermQuery(new Term("body", text));
  }

  private static BooleanQuery.Builder bool() {
    return new BooleanQuery.Builder();
  }

  /** "#q": a BooleanQuery whose only clause is FILTER q. */
  private static Query filterOnly(Query q) {
    return bool().add(q, Occur.FILTER).build();
  }

  /** The score IndexSearcher gives q on a one-document index of doc(). */
  private static float searcherScore(Query q) throws IOException {
    try (Analyzer analyzer = new StandardAnalyzer();
        Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        w.addDocument(doc());
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Explanation e = new IndexSearcher(reader).explain(q, 0);
        assertTrue("reference: " + q + " must match the document", e.isMatch());
        return e.getValue().floatValue();
      }
    }
  }

  /** Registers q alone in a default Monitor and compares its scores with IndexSearcher's. */
  private static void assertMonitorScoresLikeSearcher(Query q) throws IOException {
    float expected = searcherScore(q);
    boolean matched;
    ScoringMatch sm;
    ExplainingMatch em;
    try (Analyzer analyzer = new StandardAnalyzer();
        Monitor monitor = new Monitor(analyzer)) {
      monitor.register(new MonitorQuery("q", q));
      matched = monitor.match(doc(), QueryMatch.SIMPLE_MATCHER).matches("q") != null;
      sm = monitor.match(doc(), ScoringMatch.DEFAULT_MATCHER).matches("q");
      em = monitor.match(doc(), ExplainingMatch.MATCHER).matches("q");
    }
    String msg =
        q
            + ": IndexSearcher "
            + expected
            + ", ScoringMatch "
            + (sm == null ? "not reported" : sm.getScore())
            + ", ExplainingMatch "
            + (em == null ? "not reported" : em.getExplanation().getValue().floatValue());
    assertTrue("matching is right: " + msg, matched);
    assertNotNull("ExplainingMatch reports every match: " + msg, em);
    assertEquals(
        "ExplainingMatch value, " + msg,
        expected,
        em.getExplanation().getValue().floatValue(),
        EPS);
    // ScoringMatch keeps only scores > 0 (ScoringMatch.java), so a query that scores 0 is not
    // reported; a reported score must be the query's own score.
    if (expected > 0) {
      assertNotNull("ScoringMatch reports a positive score: " + msg, sm);
    }
    if (sm != null) {
      assertEquals("ScoringMatch score, " + msg, expected, sm.getScore(), EPS);
    }
  }

  /** "#body:apple": IndexSearcher gives 0. */
  public void testFilterOnly() throws IOException {
    assertMonitorScoresLikeSearcher(filterOnly(term("apple")));
  }

  /** "(#body:apple) body:banana": the FILTER-only clause should add nothing to body:banana. */
  public void testFilterOnlyNestedInDisjunction() throws IOException {
    assertMonitorScoresLikeSearcher(
        bool()
            .add(filterOnly(term("apple")), Occur.SHOULD)
            .add(term("banana"), Occur.SHOULD)
            .build());
  }

  /** "#body:apple -body:kiwi": IndexSearcher gives 0. */
  public void testFilterOnlyWithExclusion() throws IOException {
    assertMonitorScoresLikeSearcher(
        bool().add(term("apple"), Occur.FILTER).add(term("kiwi"), Occur.MUST_NOT).build());
  }

  /** "(#body:apple)^2.0": IndexSearcher gives 0. */
  public void testBoostedFilterOnly() throws IOException {
    assertMonitorScoresLikeSearcher(new BoostQuery(filterOnly(term("apple")), 2f));
  }

  /** "#(body:apple body:banana)": a FILTER clause over a disjunction, IndexSearcher gives 0. */
  public void testFilterOverDisjunction() throws IOException {
    assertMonitorScoresLikeSearcher(
        filterOnly(
            bool().add(term("apple"), Occur.SHOULD).add(term("banana"), Occur.SHOULD).build()));
  }

  /** Controls (pass today): shapes the decomposer keeps whole, or splits into scoring parts. */
  public void testControls() throws IOException {
    for (Query q :
        List.of(
            bool().add(term("apple"), Occur.MUST).build(),
            bool().add(term("apple"), Occur.FILTER).add(term("banana"), Occur.MUST).build(),
            bool().add(term("apple"), Occur.FILTER).add(term("banana"), Occur.SHOULD).build(),
            bool().add(term("apple"), Occur.FILTER).add(term("banana"), Occur.FILTER).build(),
            bool().add(term("apple"), Occur.SHOULD).add(term("banana"), Occur.SHOULD).build())) {
      assertMonitorScoresLikeSearcher(q);
    }
  }
}
