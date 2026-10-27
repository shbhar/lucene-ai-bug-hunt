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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * MaxScoreBulkScorer#partitionScorers decides that a non-essential clause is required by comparing
 * {@code (float) (maxRequiredScore + maxScoreSums[firstRequiredScorer - 2])} with the minimum
 * competitive score, without {@code MathUtil.sumUpperBound}. The bound and the document score add
 * the same clause scores in different orders, so with 4 or more SHOULD clauses the bound can round
 * one float ulp below the score of a document that does not match the clause. That document is
 * then skipped although its score equals the minimum competitive score.
 *
 * <p>Variant of TestMaxScoreRequiredClauseRounding for the 9.x API (used only for the 9.8.0 / 9.9.0
 * bracket); the test bodies are identical except for how the top-1 collectors are created.
 *
 * <p>Public API only. Every clause is {@code BoostQuery(ConstantScoreQuery(TermQuery))}, so a
 * clause's score and maximum score both equal its boost.
 */
public class TestMaxScoreRequiredClauseRounding9x extends LuceneTestCase {

  private static Query clause(String term, float boost) {
    return new BoostQuery(new ConstantScoreQuery(new TermQuery(new Term("f", term))), boost);
  }

  private static Query disjunction(String[] terms, float[] boosts) {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (int i = 0; i < terms.length; i++) {
      b.add(clause(terms[i], boosts[i]), BooleanClause.Occur.SHOULD);
    }
    return b.build();
  }

  private static void addDoc(IndexWriter w, String... terms) throws IOException {
    Document d = new Document();
    for (String t : terms) {
      d.add(new StringField("f", t, Field.Store.NO));
    }
    w.addDocument(d);
  }

  private static IndexWriter newWriter(Directory dir) throws IOException {
    return new IndexWriter(
        dir,
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE));
  }

  private static IndexSearcher newPlainSearcher(DirectoryReader r) {
    IndexSearcher s = new IndexSearcher(r); // no executor: one slice
    s.setQueryCache(null);
    return s;
  }

  /**
   * Drives the bulk scorer of every leaf with a fixed minimum competitive score and returns the
   * collected "doc:score" pairs (score in hex).
   */
  private static List<String> collectWithMinScore(IndexSearcher s, Query q, float minScore)
      throws IOException {
    List<String> hits = new ArrayList<>();
    Weight weight = s.createWeight(s.rewrite(q), ScoreMode.TOP_SCORES, 1f);
    for (LeafReaderContext ctx : s.getIndexReader().leaves()) {
      BulkScorer bs = weight.bulkScorer(ctx);
      assertEquals(
          "precondition: the disjunction runs on MaxScoreBulkScorer",
          "MaxScoreBulkScorer",
          bs.getClass().getSimpleName());
      bs.score(
          new LeafCollector() {
            Scorable scorer;

            @Override
            public void setScorer(Scorable scorer) throws IOException {
              this.scorer = scorer;
              scorer.setMinCompetitiveScore(minScore);
            }

            @Override
            public void collect(int doc) throws IOException {
              hits.add((ctx.docBase + doc) + ":" + Float.toHexString(scorer.score()));
            }
          },
          ctx.reader().getLiveDocs(),
          0,
          DocIdSetIterator.NO_MORE_DOCS);
    }
    return hits;
  }

  /**
   * Scorable#setMinCompetitiveScore: the iterator "may safely ignore all documents whose score is
   * less than the given minScore". A document whose score equals minScore must still be collected.
   *
   * <p>Clause scores e = 0.75, a = 0.25 + 2^-24, t = 2^-53 + 2^-76, j = 0.5. Doc 0 matches e, a and
   * t (not j); its score is nextUp(1f) both as explain() computes it and as MaxScoreBulkScorer
   * collects it. With minScore = nextUp(1f), the required-clause bound for "every clause except j"
   * is (float) (0.75 + (t + a)) = 1.0f, which is less than minScore, so j becomes required and doc 0
   * is skipped. The real sum of the maxima without j, 1 + 2^-24 + 2^-53 + 2^-76, is above minScore's
   * rounding midpoint, so the bound is too low.
   */
  public void testDocScoringExactlyMinCompetitiveScoreIsCollected() throws Exception {
    String[] terms = {"e", "a", "t", "j"};
    float[] boosts = {0.75f, 0.25f + 0x1p-24f, 0x1p-53f + 0x1p-76f, 0.5f};
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = newWriter(dir)) {
      addDoc(w, "e", "a", "t"); // doc 0: the document under test
      addDoc(w, "j"); // doc 1: gives clause j a document (max score 0.5)
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals("precondition: one segment", 1, r.leaves().size());
        IndexSearcher s = newPlainSearcher(r);
        Query q = disjunction(terms, boosts);
        float docScore = Math.nextUp(1.0f);

        assertEquals(
            "precondition: explain(doc 0)", docScore, s.explain(q, 0).getValue().floatValue(), 0f);
        assertEquals(
            "control: minScore = 1.0f collects doc 0 with score nextUp(1f)",
            List.of("0:" + Float.toHexString(docScore)),
            collectWithMinScore(s, q, 1.0f));

        assertEquals(
            "doc 0 scores "
                + Float.toHexString(docScore)
                + " (scorer and explain), which is not less than minCompetitiveScore "
                + Float.toHexString(docScore)
                + ", so it must not be skipped",
            List.of("0:" + Float.toHexString(docScore)),
            collectWithMinScore(s, q, docScore));
      }
    }
  }

  /**
   * End to end: the top hit of a plain IndexSearcher#search(query, 1) must not depend on
   * totalHitsThreshold, which only controls how accurately hits are counted
   * (TopScoreDocCollectorManager javadoc). Clause scores found by an SMT solver. Docs 0..1099 match
   * e and a; doc 5000 matches e, a and t and scores one float ulp higher; doc 6000 matches j. With
   * the default threshold (1000) the collector raises the minimum competitive score to nextUp(score
   * of doc 0), which is the score of doc 5000, and the required-clause test then skips doc 5000.
   * With a threshold above the hit count (still TOP_SCORES, but no minimum competitive score is
   * ever set) the same searcher returns doc 5000.
   *
   * <p>COMPLETE (exhaustive) search and explain() add clause scores in their own orders; for this
   * clause order (e, a, t, j) they also rank doc 5000 first, which the preconditions check.
   */
  public void testTopHitDoesNotDependOnTotalHitsThreshold() throws Exception {
    String[] terms = {"e", "a", "t", "j"};
    float[] boosts = {0x1.dfff4ep0f, 0x1.ab0p-16f, 0x1.00008p-53f, 0x1.00fe54p0f};
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = newWriter(dir)) {
      for (int i = 0; i < 1100; i++) {
        addDoc(w, "e", "a"); // more hits than the default totalHitsThreshold (1000)
      }
      for (int i = 1100; i < 5000; i++) {
        addDoc(w, "x");
      }
      addDoc(w, "e", "a", "t"); // doc 5000
      for (int i = 5001; i < 6000; i++) {
        addDoc(w, "x");
      }
      addDoc(w, "j"); // doc 6000
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals("precondition: one segment", 1, r.leaves().size());
        IndexSearcher s = newPlainSearcher(r);
        Query q = disjunction(terms, boosts);

        float explain0 = s.explain(q, 0).getValue().floatValue();
        float explain5000 = s.explain(q, 5000).getValue().floatValue();
        assertTrue(
            "precondition: explain(5000) = "
                + Float.toHexString(explain5000)
                + " > explain(0) = "
                + Float.toHexString(explain0),
            explain5000 > explain0);

        // TopScoreDocCollectorManager is not in 9.8.0 / 9.9.0: same collectors via create()
        TopScoreDocCollector complete = TopScoreDocCollector.create(1, Integer.MAX_VALUE);
        s.search(q, complete);
        ScoreDoc exhaustive = complete.topDocs().scoreDocs[0];
        assertEquals("precondition: exhaustive (COMPLETE) top-1", 5000, exhaustive.doc);

        // TOP_SCORES, every hit counted accurately: 1102 hits <= 10000, so no pruning
        TopScoreDocCollector accurateCollector = TopScoreDocCollector.create(1, 10_000);
        s.search(q, accurateCollector);
        TopDocs accurate = accurateCollector.topDocs();
        assertEquals(
            "precondition: TOP_SCORES with totalHitsThreshold 10000 top-1",
            5000,
            accurate.scoreDocs[0].doc);

        TopDocs pruned = s.search(q, 1); // TOP_SCORES, default totalHitsThreshold = 1000
        String msg =
            "search(q, 1) top-1 = doc "
                + pruned.scoreDocs[0].doc
                + " ("
                + Float.toHexString(pruned.scoreDocs[0].score)
                + ", totalHits "
                + pruned.totalHits
                + "); with totalHitsThreshold 10000 top-1 = doc "
                + accurate.scoreDocs[0].doc
                + " ("
                + Float.toHexString(accurate.scoreDocs[0].score)
                + ", totalHits "
                + accurate.totalHits
                + "); exhaustive top-1 = doc "
                + exhaustive.doc
                + " ("
                + Float.toHexString(exhaustive.score)
                + "); explain(0) = "
                + Float.toHexString(explain0)
                + ", explain(5000) = "
                + Float.toHexString(explain5000);
        assertEquals(msg, accurate.scoreDocs[0].doc, pruned.scoreDocs[0].doc);
        assertEquals(msg, accurate.scoreDocs[0].score, pruned.scoreDocs[0].score, 0f);
      }
    }
  }
}
