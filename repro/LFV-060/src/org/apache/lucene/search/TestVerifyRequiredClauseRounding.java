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
import org.apache.lucene.util.MathUtil;

/**
 * Independent verification (own numbers, own index layout) of the claim that the required-clause
 * test in MaxScoreBulkScorer.partitionScorers (line 540 on main 7cecf79523) can drop a document
 * whose score is >= minCompetitiveScore.
 *
 * <p>Clauses (all ConstantScore, so score == boost == max score):
 *
 * <pre>
 *   E = 3.0f                      (becomes the single essential clause)
 *   A = 1 + 2^-22  (0x1.000004p0)
 *   J = 2.0f                      (best non-essential clause; the doc under test does NOT match it)
 *   T = 2^-51 + 2^-70 (0x1.00002p-51)
 * </pre>
 *
 * Sorted by maxScore/cost the scorer gets allScorers = [T, A, J, E]. A doc matching {E, A, T}
 * is scored by MaxScoreBulkScorer as ((E + A) + T) in double = 4 + 2^-22 + 2^-50, which casts to
 * the float 4 + 2^-21 (= Math.nextUp(4f)). The required-clause bound at line 540 is E + (T + A) =
 * 4 + 2^-22 in double (a float tie that rounds to even), which casts to 4.0f.
 */
public class TestVerifyRequiredClauseRounding extends LuceneTestCase {

  static final float E = 3.0f;
  static final float A = 0x1.000004p0f; // 1 + 2^-22
  static final float J = 2.0f;
  static final float T = 0x1.00002p-51f; // 2^-51 + 2^-70
  static final float FOUR_UP = Math.nextUp(4.0f); // 0x1.000002p2 = 4 + 2^-21

  private static Query clause(String term, float boost) {
    return new BoostQuery(new ConstantScoreQuery(new TermQuery(new Term("f", term))), boost);
  }

  private static Query query() {
    // clause order in the BooleanQuery is deliberately not the sorted order
    return query("ejat");
  }

  /** order: a permutation of "eajt" giving the BooleanQuery clause order. */
  private static Query query(String order) {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (char c : order.toCharArray()) {
      float boost =
          switch (c) {
            case 'e' -> E;
            case 'a' -> A;
            case 'j' -> J;
            case 't' -> T;
            default -> throw new AssertionError();
          };
      b.add(clause(String.valueOf(c), boost), BooleanClause.Occur.SHOULD);
    }
    return b.build();
  }

  private static void permutations(String prefix, String rest, List<String> out) {
    if (rest.isEmpty()) {
      out.add(prefix);
    }
    for (int i = 0; i < rest.length(); i++) {
      permutations(
          prefix + rest.charAt(i), rest.substring(0, i) + rest.substring(i + 1), out);
    }
  }

  private static void addDoc(IndexWriter w, String... terms) throws IOException {
    Document d = new Document();
    for (String t : terms) {
      d.add(new StringField("f", t, Field.Store.NO));
    }
    w.addDocument(d);
  }

  private static IndexSearcher searcher(DirectoryReader r) {
    IndexSearcher s = new IndexSearcher(r); // no executor, single slice
    s.setQueryCache(null);
    return s;
  }

  /** Pure arithmetic: the two summation orders and what line 540 vs the fixed test would decide. */
  public void testArithmetic() {
    double docOrder = ((double) E + A) + T; // essential first, then non-essential i = n-2 .. 0
    double maxScoreSums1 = (0d + T) + A; // maxScoreSums[1], accumulated in partitionScorers
    double bound540 = (double) E + maxScoreSums1; // line 536-538
    System.out.println("docOrder   = " + Double.toHexString(docOrder) + " -> (float) "
        + Float.toHexString((float) docOrder));
    System.out.println("bound(540) = " + Double.toHexString(bound540) + " -> (float) "
        + Float.toHexString((float) bound540));
    double fixed = MathUtil.sumUpperBound(bound540, 4);
    System.out.println("sumUpperBound(bound,4) = " + Double.toHexString(fixed) + " -> (float) "
        + Float.toHexString((float) fixed));
    assertEquals(FOUR_UP, (float) docOrder, 0f);
    assertEquals(4.0f, (float) bound540, 0f); // < FOUR_UP: line 540 makes J required
    assertEquals(FOUR_UP, (float) fixed, 0f); // >= FOUR_UP: fixed test would not
  }

  /** Runs the bulk scorer with a fixed minCompetitiveScore and records (doc, score). */
  private static List<String> collect(IndexSearcher s, Query q, float minScore, int[] fRSOut)
      throws IOException {
    List<String> hits = new ArrayList<>();
    Weight weight = s.createWeight(s.rewrite(q), ScoreMode.TOP_SCORES, 1f);
    for (LeafReaderContext ctx : s.getIndexReader().leaves()) {
      BulkScorer bs = weight.bulkScorer(ctx);
      assertTrue("expected MaxScoreBulkScorer, got " + bs.getClass(), bs instanceof MaxScoreBulkScorer);
      MaxScoreBulkScorer ms = (MaxScoreBulkScorer) bs;
      bs.score(
          new LeafCollector() {
            Scorable scorer;

            @Override
            public void setScorer(Scorable scorer) throws IOException {
              this.scorer = scorer;
              if (minScore > 0) {
                scorer.setMinCompetitiveScore(minScore);
              }
            }

            @Override
            public void collect(int doc) throws IOException {
              hits.add(doc + ":" + Float.toHexString(scorer.score()));
            }
          },
          null,
          0,
          DocIdSetIterator.NO_MORE_DOCS);
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < ms.allScorers.length; i++) {
        sb.append(" [").append(i).append("] max=")
            .append(Float.toHexString(ms.allScorers[i].maxWindowScore))
            .append(" sums=").append(Double.toHexString(ms.maxScoreSums[i]));
      }
      System.out.println("  minScore=" + Float.toHexString(minScore)
          + " firstEssentialScorer=" + ms.firstEssentialScorer
          + " firstRequiredScorer=" + ms.firstRequiredScorer + sb);
      fRSOut[0] = ms.firstRequiredScorer;
    }
    return hits;
  }

  /**
   * Scorable#setMinCompetitiveScore: "its iterator may safely ignore all documents whose score is
   * less than the given minScore".
   */
  public void testSetMinCompetitiveScoreContract() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      addDoc(w, "e", "a", "t"); // doc 0 : the doc under test
      addDoc(w, "j"); // doc 1 : gives clause J its max score
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(1, r.leaves().size());
        IndexSearcher s = searcher(r);
        Query q = query();
        int[] fRS = new int[1];

        // minScore = 4.0f : same partition (E essential, T/A/J non-essential), no required clause
        List<String> low = collect(s, q, 4.0f, fRS);
        System.out.println("  collected with minScore=4.0f       : " + low);
        assertEquals(3, fRS[0]); // == allScorers.length - 1: only the essential clause is required
        assertEquals(List.of("0:" + Float.toHexString(FOUR_UP)), low);
        float explained = s.explain(q, 0).getValue().floatValue();
        System.out.println("  explain(doc 0) = " + Float.toHexString(explained));

        // minScore = nextUp(4f) == doc 0's score as this scorer computes it
        List<String> high = collect(s, q, FOUR_UP, fRS);
        System.out.println("  collected with minScore=0x1.000002p2: " + high);
        assertEquals(
            "doc 0 has score 0x1.000002p2 (>= minCompetitiveScore 0x1.000002p2) but was skipped;"
                + " firstRequiredScorer=" + fRS[0],
            List.of("0:" + Float.toHexString(FOUR_UP)),
            high);
      }
    }
  }

  /**
   * End-to-end through IndexSearcher.search(q, 1): doc X (score nextUp(4f) under this scorer's own
   * summation order) loses to doc 0 (score 4.0f) because, after TopScoreDocCollector raises the
   * min competitive score to nextUp(4f), the required-clause test drops X.
   */
  public void testTopOneViaIndexSearcher() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      final int numY = 1200; // > default totalHitsThreshold of 1000
      for (int i = 0; i < numY; i++) addDoc(w, "e", "a"); // score float(3 + 1 + 2^-22) = 4.0f
      while (w.getDocStats().maxDoc < 5000) addDoc(w, "zzz"); // push X past the 1st inner window
      final int x = 5000;
      addDoc(w, "e", "a", "t"); // doc X
      for (int i = 0; i < numY; i++) addDoc(w, "j"); // J cost ~ E/A cost, keeps ratio order T<A<J<E
      w.forceMerge(1);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(1, r.leaves().size());
        IndexSearcher s = searcher(r);
        Query q = query();

        // X scored in isolation by the very same MaxScoreBulkScorer (min score 4.0f => same
        // E-essential partition, no required clause): the score it would have had.
        float xScoreSameScorer = Float.NaN;
        int[] fRS = new int[1];
        for (String h : collect(s, q, 4.0f, fRS)) {
          String[] p = h.split(":");
          if (Integer.parseInt(p[0]) == x) xScoreSameScorer = Float.parseFloat(p[1]);
        }
        float xExplain = s.explain(q, x).getValue().floatValue();
        float yExplain = s.explain(q, 0).getValue().floatValue();

        TopDocs pruned = s.search(q, 1); // TopScoreDocCollectorManager(1, 1000): TOP_SCORES
        TopDocs complete = s.search(q, new TopScoreDocCollectorManager(1, Integer.MAX_VALUE));
        String msg =
            "pruned top-1 = doc " + pruned.scoreDocs[0].doc + " ("
                + Float.toHexString(pruned.scoreDocs[0].score) + ", totalHits=" + pruned.totalHits
                + "); COMPLETE top-1 = doc " + complete.scoreDocs[0].doc + " ("
                + Float.toHexString(complete.scoreDocs[0].score) + "); X=" + x
                + " scored by MaxScoreBulkScorer@minScore=4f: " + Float.toHexString(xScoreSameScorer)
                + "; explain(X)=" + Float.toHexString(xExplain)
                + "; explain(0)=" + Float.toHexString(yExplain);
        System.out.println("  " + msg);
        assertEquals(FOUR_UP, xScoreSameScorer, 0f);
        assertEquals(4.0f, yExplain, 0f);
        assertEquals(msg, x, pruned.scoreDocs[0].doc);
      }
    }
  }

  /**
   * Informational sweep over all 24 BooleanQuery clause orders on the same index as
   * testTopOneViaIndexSearcher. Asserts only what is specific to the line-540 bug: the pruned
   * top-1 equals what the same MaxScoreBulkScorer computes for X without the extra required clause.
   * Prints the COMPLETE (BooleanScorer) top-1 and explain(X), whose summation orders differ.
   */
  public void testClauseOrderSweep() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      final int numY = 1200;
      for (int i = 0; i < numY; i++) addDoc(w, "e", "a");
      while (w.getDocStats().maxDoc < 5000) addDoc(w, "zzz");
      final int x = 5000;
      addDoc(w, "e", "a", "t");
      for (int i = 0; i < numY; i++) addDoc(w, "j");
      w.forceMerge(1);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = searcher(r);
        List<String> orders = new ArrayList<>();
        permutations("", "eajt", orders);
        int prunedIsX = 0, completeIsX = 0, prunedNeComplete = 0;
        List<String> bad = new ArrayList<>();
        for (String order : orders) {
          Query q = query(order);
          ScoreDoc pruned = s.search(q, 1).scoreDocs[0];
          ScoreDoc complete =
              s.search(q, new TopScoreDocCollectorManager(1, Integer.MAX_VALUE)).scoreDocs[0];
          float ex = s.explain(q, x).getValue().floatValue();
          System.out.println(
              "  order=" + order + " pruned=" + pruned.doc + "/" + Float.toHexString(pruned.score)
                  + " complete=" + complete.doc + "/" + Float.toHexString(complete.score)
                  + " explain(X)=" + Float.toHexString(ex));
          if (pruned.doc == x) prunedIsX++;
          else bad.add(order);
          if (complete.doc == x) completeIsX++;
          if (pruned.doc != complete.doc) prunedNeComplete++;
        }
        System.out.println(
            "  SUMMARY: pruned top-1 == X in " + prunedIsX + "/24; COMPLETE top-1 == X in "
                + completeIsX + "/24; pruned != COMPLETE in " + prunedNeComplete + "/24");
        assertTrue("orders where the pruned top-1 is not X: " + bad, bad.isEmpty());
      }
    }
  }
}
