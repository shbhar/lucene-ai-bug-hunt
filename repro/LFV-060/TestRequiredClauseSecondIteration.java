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
package tiebreak;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
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
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.BulkScorer;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorable;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.search.CheckHits;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Independent check, written outside org.apache.lucene.search (public API only), with a different
 * shape from the 4-clause inputs: five SHOULD clauses whose scores lie in [4, 8), where the bad
 * rounding happens in the SECOND iteration of the required-clause loop.
 *
 * <p>Clause scores (each clause is BoostQuery(ConstantScoreQuery(TermQuery)), so score = max score =
 * boost): e = 2.5, s3 = 1.0, s2 = 0.75, s1 = 0.5 + 2^-22, t = nextUp(2^-51) = 2^-51 + 2^-74. With
 * equal costs the non-essential order is t, s1, s2, s3 and e is the single essential clause. At
 * minCompetitiveScore = nextUp(4f): iteration 1 (without s3) gives (float)(2.5 + ((t + s1) + s2)) =
 * 3.75 + 2^-22, below the minimum, so s3 becomes required (sound). Iteration 2 (without s2) gives
 * (float)((2.5 + 1) + (t + s1)): t + s1 drops 2^-74, the double sum lands exactly on 4 + 2^-22 +
 * 2^-51 (a double tie, rounded to even = 4 + 2^-22), and the float cast of that float midpoint rounds
 * to even = 4.0f, so s2 becomes required. A document with e, s3, s1 and t but not s2 has the exact
 * score 4 + 2^-22 + 2^-51 + 2^-74, above the float midpoint, and the scorer itself computes
 * nextUp(4f) for it.
 */
public class TestRequiredClauseSecondIteration extends LuceneTestCase {

  static final float E = 2.5f;
  static final float S3 = 1.0f;
  static final float S2 = 0.75f;
  static final float S1 = 0.5f + 0x1p-22f;
  static final float T = Math.nextUp(0x1p-51f);
  static final float FOUR = 4.0f;
  static final float NEXT_UP_FOUR = Math.nextUp(4.0f);

  private static Query clause(String term, float boost) {
    return new BoostQuery(new ConstantScoreQuery(new TermQuery(new Term("g", term))), boost);
  }

  /** Clause order e, s3, s2, s1, t. */
  private static Query query() {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    b.add(clause("e", E), BooleanClause.Occur.SHOULD);
    b.add(clause("s3", S3), BooleanClause.Occur.SHOULD);
    b.add(clause("s2", S2), BooleanClause.Occur.SHOULD);
    b.add(clause("s1", S1), BooleanClause.Occur.SHOULD);
    b.add(clause("t", T), BooleanClause.Occur.SHOULD);
    return b.build();
  }

  private static void add(IndexWriter w, String... terms) throws IOException {
    Document d = new Document();
    for (String term : terms) {
      d.add(new StringField("g", term, Field.Store.NO));
    }
    w.addDocument(d);
  }

  private static IndexWriter writer(Directory dir) throws IOException {
    return new IndexWriter(
        dir,
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE));
  }

  private static IndexSearcher searcher(DirectoryReader r) {
    IndexSearcher s = new IndexSearcher(r);
    s.setQueryCache(null);
    return s;
  }

  /** True iff the exact real sum of the given floats rounds (to nearest) to nextUp(4f). */
  private static boolean exactSumRoundsToNextUpFour(float... v) {
    BigDecimal sum = BigDecimal.ZERO;
    for (float f : v) {
      sum = sum.add(new BigDecimal((double) f));
    }
    BigDecimal lowMid = new BigDecimal(4.0).add(new BigDecimal(0x1p-22)); // 4 + ulp/2
    BigDecimal highMid = new BigDecimal(4.0).add(new BigDecimal(0x1p-21 + 0x1p-22));
    return sum.compareTo(lowMid) > 0 && sum.compareTo(highMid) < 0;
  }

  /** TOP_SCORES collector that sets a fixed minimum competitive score and records doc:score. */
  private static final class FixedMinScoreCollector extends SimpleCollector {
    final List<String> hits = new ArrayList<>();
    final float minScore;
    Scorable scorer;
    int docBase;

    FixedMinScoreCollector(float minScore) {
      this.minScore = minScore;
    }

    @Override
    protected void doSetNextReader(LeafReaderContext context) {
      docBase = context.docBase;
    }

    @Override
    public void setScorer(Scorable scorer) throws IOException {
      this.scorer = scorer;
      scorer.setMinCompetitiveScore(minScore);
    }

    @Override
    public void collect(int doc) throws IOException {
      hits.add((docBase + doc) + ":" + Float.toHexString(scorer.score()));
    }

    @Override
    public ScoreMode scoreMode() {
      return ScoreMode.TOP_SCORES;
    }
  }

  /** Runs IndexSearcher#search with a FixedMinScoreCollector per slice; returns doc:score. */
  private static List<String> collectAtMinScore(IndexSearcher s, Query q, float minScore)
      throws IOException {
    return s.search(
        q,
        new CollectorManager<FixedMinScoreCollector, List<String>>() {
          @Override
          public FixedMinScoreCollector newCollector() {
            return new FixedMinScoreCollector(minScore);
          }

          @Override
          public List<String> reduce(Collection<FixedMinScoreCollector> collectors) {
            List<String> all = new ArrayList<>();
            for (FixedMinScoreCollector c : collectors) {
              all.addAll(c.hits);
            }
            return all;
          }
        });
  }

  public void testDocScoringExactlyMinCompetitiveScoreSecondIteration() throws Exception {
    assertTrue("precondition: exact sum of e+s3+s1+t rounds to nextUp(4f)",
        exactSumRoundsToNextUpFour(E, S3, S1, T));
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir)) {
      add(w, "e", "s3", "s1", "t"); // doc 0: no s2
      add(w, "s2"); // doc 1: gives s2 a posting
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals("precondition: one segment", 1, r.leaves().size());
        IndexSearcher s = searcher(r);
        Query q = query();
        Weight weight = s.createWeight(s.rewrite(q), ScoreMode.TOP_SCORES, 1f);
        BulkScorer bs = weight.bulkScorer(r.leaves().get(0));
        String bulkScorerClass = bs.getClass().getSimpleName();
        assertEquals("precondition: bulk scorer", "MaxScoreBulkScorer", bulkScorerClass);

        float explain0 = s.explain(q, 0).getValue().floatValue();
        String expected = "0:" + Float.toHexString(NEXT_UP_FOUR);
        // control: one float below the doc's score, the same scorer collects it at nextUp(4f)
        assertEquals("control at minScore 4.0f", List.of(expected), collectAtMinScore(s, q, FOUR));
        // sanity: one float above the doc's score, skipping it is allowed
        assertEquals(
            "sanity at minScore nextUp(nextUp(4f))",
            List.of(),
            collectAtMinScore(s, q, Math.nextUp(NEXT_UP_FOUR)));

        assertEquals(
            "doc 0 scores "
                + Float.toHexString(NEXT_UP_FOUR)
                + " (control run; explain = "
                + Float.toHexString(explain0)
                + "), not less than minCompetitiveScore "
                + Float.toHexString(NEXT_UP_FOUR)
                + ", so it must be collected",
            List.of(expected),
            collectAtMinScore(s, q, NEXT_UP_FOUR));
      }
    }
  }

  /** Index for the end-to-end tests (one segment). */
  private static void buildEndToEndIndex(IndexWriter w) throws IOException {
    for (int i = 0; i < 1200; i++) {
      add(w, "e", "s3", "s1"); // exact score 4 + 2^-22 -> 4.0f (tie to even), any order
    }
    for (int i = 1200; i < 4096; i++) {
      add(w, "x");
    }
    add(w, "e", "s3", "s1"); // doc 4096: 4.0f
    add(w, "e", "s3", "s1", "t"); // doc 4097: exact sum rounds to nextUp(4f)
    for (int i = 4098; i < 6000; i++) {
      add(w, "x");
    }
    for (int i = 6000; i <= 7200; i++) {
      add(w, "s2"); // 1201 docs: cost puts s2 between s1 and s3 in the ratio order
    }
  }

  /** Lucene's own test-framework oracle: TOP_SCORES top hits must equal COMPLETE top hits. */
  public void testUpstreamCheckTopScoresOracle() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir)) {
      buildEndToEndIndex(w);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals("precondition: one segment", 1, r.leaves().size());
        CheckHits.checkTopScores(random(), query(), searcher(r));
      }
    }
  }

  public void testSearchTopOneSecondIteration() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir)) {
      buildEndToEndIndex(w);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals("precondition: one segment", 1, r.leaves().size());
        IndexSearcher s = searcher(r);
        Query q = query();

        TopDocs accurate = s.search(q, new TopScoreDocCollectorManager(1, 10_000));
        TopDocs complete = s.search(q, new TopScoreDocCollectorManager(1, Integer.MAX_VALUE));
        TopDocs pruned = s.search(q, 1);
        float explain0 = s.explain(q, 0).getValue().floatValue();
        float explain4097 = s.explain(q, 4097).getValue().floatValue();
        String msg =
            "search(q,1) top-1 = doc "
                + pruned.scoreDocs[0].doc
                + " ("
                + Float.toHexString(pruned.scoreDocs[0].score)
                + ", "
                + pruned.totalHits
                + "); TopScoreDocCollectorManager(1, 10000) top-1 = doc "
                + accurate.scoreDocs[0].doc
                + " ("
                + Float.toHexString(accurate.scoreDocs[0].score)
                + ", "
                + accurate.totalHits
                + "); COMPLETE top-1 = doc "
                + complete.scoreDocs[0].doc
                + " ("
                + Float.toHexString(complete.scoreDocs[0].score)
                + "); explain(0) = "
                + Float.toHexString(explain0)
                + ", explain(4097) = "
                + Float.toHexString(explain4097);
        assertEquals("precondition (unpruned TOP_SCORES): " + msg, 4097, accurate.scoreDocs[0].doc);
        assertEquals("precondition: " + msg, NEXT_UP_FOUR, accurate.scoreDocs[0].score, 0f);
        assertEquals(msg, accurate.scoreDocs[0].doc, pruned.scoreDocs[0].doc);
        assertEquals(msg, accurate.scoreDocs[0].score, pruned.scoreDocs[0].score, 0f);
      }
    }
  }
}
