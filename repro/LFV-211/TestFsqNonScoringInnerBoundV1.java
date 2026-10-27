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
package org.apache.lucene.queries.function;

import java.io.IOException;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.search.CheckHits;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Independent check: FunctionScoreQuery(TermQuery, fromLongField) over a numeric doc-values field
 * with a skip index. The score of a hit is the field value (FunctionScoreQuery#score), and the
 * wrapped TermQuery is weighted without scores, so its own max score must not cap the bound.
 *
 * <p>Public API only. One segment, default codec, BM25 (plain IndexSearcher).
 */
public class TestFsqNonScoringInnerBoundV1 extends LuceneTestCase {

  private static final int NUM_DOCS = 10000;
  // even doc: matches both "alpha" and "beta"; lies inside the second 4096-doc skip-index
  // interval [4096, 8191], and inside a full 128-doc postings block of "beta", so that the
  // conjunction's scoring window around it does not reach past the interval
  private static final int BIG_DOC = 5000;
  private static final long BIG_VALUE = 1000;

  private static DirectoryReader buildIndex(Directory dir, boolean skipIndex) throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
    iwc.setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < NUM_DOCS; i++) {
        Document d = new Document();
        d.add(new TextField("body", (i % 2 == 0) ? "alpha beta" : "alpha", Field.Store.NO));
        // values vary from doc to doc, so the skip index closes its level-0 intervals at exactly
        // 4096 docs ([0, 4095], [4096, 8191], [8192, 9999]); a run of equal values would
        // extend an interval
        long v = (i == BIG_DOC) ? BIG_VALUE : 1 + (i % 7);
        d.add(
            skipIndex
                ? NumericDocValuesField.indexedField("val", v)
                : new NumericDocValuesField("val", v));
        w.addDocument(d);
      }
      w.forceMerge(1);
    }
    return DirectoryReader.open(dir);
  }

  private static Query fsq() {
    return new FunctionScoreQuery(
        new TermQuery(new Term("body", "alpha")), DoubleValuesSource.fromLongField("val"));
  }

  private static IndexSearcher searcher(DirectoryReader r) {
    IndexSearcher s = new IndexSearcher(r);
    s.setQueryCache(null);
    return s;
  }

  /** Returns null when every block bound is >= every score in its block, else a message. */
  private static String firstBoundViolation(IndexSearcher s, Query q) throws IOException {
    Weight w = s.createWeight(s.rewrite(q), ScoreMode.TOP_SCORES, 1f);
    for (LeafReaderContext ctx : s.getIndexReader().leaves()) {
      ScorerSupplier ss = w.scorerSupplier(ctx);
      if (ss == null) continue;
      Scorer sc = ss.get(Long.MAX_VALUE);
      DocIdSetIterator it = sc.iterator();
      int doc = it.nextDoc();
      while (doc != DocIdSetIterator.NO_MORE_DOCS) {
        int upTo = sc.advanceShallow(doc);
        float bound = sc.getMaxScore(upTo);
        for (; doc != DocIdSetIterator.NO_MORE_DOCS && doc <= upTo; doc = it.nextDoc()) {
          float score = sc.score();
          if (score > bound) {
            return "doc "
                + doc
                + " score "
                + score
                + " > getMaxScore("
                + upTo
                + ")="
                + bound
                + " (block from advanceShallow("
                + doc
                + "))";
          }
        }
      }
    }
    return null;
  }

  private static void assertTop1Equal(IndexSearcher s, Query q) throws IOException {
    TopDocs complete = s.search(q, new TopScoreDocCollectorManager(1, null, Integer.MAX_VALUE));
    TopDocs topScores = s.search(q, new TopScoreDocCollectorManager(1, null, 1));
    TopDocs plain = s.search(q, 1);
    String msg =
        "COMPLETE top="
            + complete.scoreDocs[0].doc
            + "/"
            + complete.scoreDocs[0].score
            + " TOP_SCORES top="
            + topScores.scoreDocs[0].doc
            + "/"
            + topScores.scoreDocs[0].score
            + " search(q,1) top="
            + plain.scoreDocs[0].doc
            + "/"
            + plain.scoreDocs[0].score;
    System.out.println(q + " :: " + msg);
    assertEquals("sanity: COMPLETE finds the big-value doc", BIG_DOC, complete.scoreDocs[0].doc);
    assertEquals("TOP_SCORES vs COMPLETE: " + msg, complete.scoreDocs[0].doc, topScores.scoreDocs[0].doc);
    assertEquals(
        "TOP_SCORES vs COMPLETE score: " + msg,
        complete.scoreDocs[0].score,
        topScores.scoreDocs[0].score,
        0f);
    assertEquals("search(q,1) vs COMPLETE: " + msg, complete.scoreDocs[0].doc, plain.scoreDocs[0].doc);
  }

  /** Scorer#getMaxScore(upTo) must be >= the score of every doc in [shallow target, upTo]. */
  public void testExposedBoundIsUpperBound() throws IOException {
    try (Directory dir = newDirectory();
        DirectoryReader r = buildIndex(dir, true)) {
      assertNotNull("precondition: skip index present", r.leaves().get(0).reader().getDocValuesSkipper("val"));
      String v = firstBoundViolation(searcher(r), fsq());
      System.out.println("bound check (skip index): " + (v == null ? "OK" : v));
      assertNull(v, v);
    }
  }

  /** FSQ as a SHOULD clause next to a scoring term clause: TOP_SCORES must equal COMPLETE. */
  public void testShouldClauseTopHit() throws IOException {
    try (Directory dir = newDirectory();
        DirectoryReader r = buildIndex(dir, true)) {
      Query q =
          new BooleanQuery.Builder()
              .add(fsq(), Occur.SHOULD)
              .add(new TermQuery(new Term("body", "beta")), Occur.SHOULD)
              .build();
      assertTop1Equal(searcher(r), q);
    }
  }

  /** FSQ as a MUST clause next to a scoring MUST term clause: TOP_SCORES must equal COMPLETE. */
  public void testMustClauseTopHit() throws IOException {
    try (Directory dir = newDirectory();
        DirectoryReader r = buildIndex(dir, true)) {
      Query q =
          new BooleanQuery.Builder()
              .add(fsq(), Occur.MUST)
              .add(new TermQuery(new Term("body", "beta")), Occur.MUST)
              .build();
      assertTop1Equal(searcher(r), q);
    }
  }

  /** Lucene's own TOP_SCORES oracle (test-framework CheckHits) on the plain FSQ. */
  public void testCheckHitsOracleOnFsq() throws IOException {
    try (Directory dir = newDirectory();
        DirectoryReader r = buildIndex(dir, true)) {
      CheckHits.checkTopScores(random(), fsq(), searcher(r));
    }
  }

  /** Control: no skip index -> the bound is +Inf; everything must pass. */
  public void testControlNoSkipIndex() throws IOException {
    try (Directory dir = newDirectory();
        DirectoryReader r = buildIndex(dir, false)) {
      IndexSearcher s = searcher(r);
      assertNull(firstBoundViolation(s, fsq()));
      assertTop1Equal(
          s,
          new BooleanQuery.Builder()
              .add(fsq(), Occur.SHOULD)
              .add(new TermQuery(new Term("body", "beta")), Occur.SHOULD)
              .build());
      assertTop1Equal(
          s,
          new BooleanQuery.Builder()
              .add(fsq(), Occur.MUST)
              .add(new TermQuery(new Term("body", "beta")), Occur.MUST)
              .build());
    }
  }

  /** Control: the FSQ alone at top level (its scorer does not prune) still finds the top hit. */
  public void testControlTopLevelAlone() throws IOException {
    try (Directory dir = newDirectory();
        DirectoryReader r = buildIndex(dir, true)) {
      IndexSearcher s = searcher(r);
      TopDocs complete =
          s.search(fsq(), new TopScoreDocCollectorManager(1, null, Integer.MAX_VALUE));
      TopDocs topScores = s.search(fsq(), new TopScoreDocCollectorManager(1, null, 1));
      System.out.println(
          "top-level alone: COMPLETE="
              + complete.scoreDocs[0].doc
              + " TOP_SCORES="
              + topScores.scoreDocs[0].doc);
      assertEquals(BIG_DOC, complete.scoreDocs[0].doc);
      assertEquals(BIG_DOC, topScores.scoreDocs[0].doc);
    }
  }
}
