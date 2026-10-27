import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.FieldStats;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.SynonymQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermStats;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.BasicStats;
import org.apache.lucene.search.similarities.Distribution;
import org.apache.lucene.search.similarities.DistributionLL;
import org.apache.lucene.search.similarities.DistributionSPL;
import org.apache.lucene.search.similarities.IBSimilarity;
import org.apache.lucene.search.similarities.Lambda;
import org.apache.lucene.search.similarities.LambdaDF;
import org.apache.lucene.search.similarities.LambdaTTF;
import org.apache.lucene.search.similarities.Normalization;
import org.apache.lucene.search.similarities.NormalizationH1;
import org.apache.lucene.search.similarities.NormalizationH2;
import org.apache.lucene.search.similarities.NormalizationH3;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.search.AssertingIndexSearcher;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Independent verification: IBSimilarity + DistributionLL returning -0.0f.
 *
 * <p>Every test asserts the behaviour expected if LL behaved like post-#16139 SPL (score == +0.0f,
 * raw bits 0). Tests named control* are expected to pass on main. All inputs are fixed; no test
 * depends on the random seed.
 */
public class VerifyLLNegZeroTest extends LuceneTestCase {

  static final long NEG_ZERO_D = Double.doubleToRawLongBits(-0.0d);
  static final int NEG_ZERO_F = Float.floatToRawIntBits(-0.0f);

  static String bits(float f) {
    return String.format("%s (0x%08x)", f, Float.floatToRawIntBits(f));
  }

  static BasicStats stats() {
    BasicStats s = new BasicStats("f", 1.0);
    s.setNumberOfDocuments(100);
    s.setNumberOfFieldTokens(1000);
    s.setAvgFieldLength(10);
    s.setDocFreq(10);
    s.setTotalTermFreq(20);
    return s;
  }

  // ---------------------------------------------------------------- 1. Distribution level

  /** DistributionLL.score(tfn = 0) == -log(lambda/lambda) == -log(1.0) == -0.0 for every lambda. */
  public void testDistributionLLTfnZero() {
    Distribution ll = new DistributionLL();
    List<String> neg = new ArrayList<>();
    for (double lambda : new double[] {1e-9, 0.0909, 0.5, Math.nextDown(1f), 1.5, 1e6}) {
      for (double tfn : new double[] {0.0, Double.MIN_VALUE, 1e-17}) {
        double s = ll.score(stats(), tfn, lambda);
        System.out.printf("LL.score(tfn=%s, lambda=%s) = %s rawbits=0x%016x%n", tfn, lambda, s,
            Double.doubleToRawLongBits(s));
        if (Double.doubleToRawLongBits(s) == NEG_ZERO_D) neg.add("tfn=" + tfn + ",lambda=" + lambda);
      }
    }
    assertTrue("DistributionLL returned -0.0 for " + neg, neg.isEmpty());
  }

  /** Control: post-#16139 DistributionSPL returns +0.0 at tfn == 0. */
  public void testControlDistributionSPLTfnZero() {
    Distribution spl = new DistributionSPL();
    for (double lambda : new double[] {1e-9, 0.0909, 0.5, Math.nextDown(1f), 1.5, 1e6}) {
      double s = spl.score(stats(), 0.0, lambda);
      System.out.printf("SPL.score(tfn=0, lambda=%s) = %s rawbits=0x%016x%n", lambda, s,
          Double.doubleToRawLongBits(s));
      assertEquals(0L, Double.doubleToRawLongBits(s));
    }
  }

  // ---------------------------------------------------------------- 2. SimScorer level

  record Case(String name, Normalization norm, float freq) {}

  static List<Case> cases() {
    List<Case> cs = new ArrayList<>();
    cs.add(new Case("H1(c=0) freq=1", new NormalizationH1(0f), 1f));
    cs.add(new Case("H2(c=0) freq=1", new NormalizationH2(0f), 1f));
    cs.add(new Case("H3(mu=0) freq=1", new NormalizationH3(0f), 1f));
    cs.add(new Case("H1(c=MIN_VALUE) freq=1", new NormalizationH1(Float.MIN_VALUE), 1f));
    cs.add(new Case("H1(c=1e-20) freq=5", new NormalizationH1(1e-20f), 5f));
    // default hyper-parameters; freq is legal per SimScorer#score javadoc ("finite and positive")
    cs.add(new Case("H1() default freq=MIN_VALUE", new NormalizationH1(), Float.MIN_VALUE));
    cs.add(new Case("H2() default freq=1e-20", new NormalizationH2(), 1e-20f));
    cs.add(new Case("H3() default freq=MIN_VALUE", new NormalizationH3(), Float.MIN_VALUE));
    cs.add(new Case("H1() default freq=1 (realistic)", new NormalizationH1(), 1f));
    return cs;
  }

  float simScore(Distribution d, Lambda l, Case c) {
    Similarity sim = new IBSimilarity(d, l, c.norm());
    // field: maxDoc=100, docCount=100, sumTTF=1000, sumDocFreq=500; term: df=10, ttf=20
    FieldStats fs = new FieldStats("f", 100, 100, 1000, 500);
    TermStats ts = new TermStats(new BytesRef("t"), 10, 20);
    // norm=1 is the most favourable legal norm (shortest doc)
    return sim.scorer(1f, fs, ts).score(c.freq(), 1L);
  }

  public void testSimScorerLL() {
    List<String> neg = new ArrayList<>();
    for (Lambda l : new Lambda[] {new LambdaDF(), new LambdaTTF()}) {
      for (Case c : cases()) {
        float ll = simScore(new DistributionLL(), l, c);
        float spl = simScore(new DistributionSPL(), l, c);
        System.out.printf("IB-%s %-34s LL=%-26s SPL=%s%n", l, c.name(), bits(ll), bits(spl));
        if (Float.floatToRawIntBits(ll) == NEG_ZERO_F) neg.add(l + ":" + c.name());
      }
    }
    assertTrue("IB(LL) SimScorer returned -0.0f for " + neg, neg.isEmpty());
  }

  // ---------------------------------------------------------------- index helpers

  static Directory index(Similarity sim, String[][] docs) throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
    iwc.setSimilarity(sim);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (String[] d : docs) {
        Document doc = new Document();
        doc.add(new TextField("f", d[0], Field.Store.NO));
        if (d[1] != null) doc.add(new TextField("g", d[1], Field.Store.NO));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  /** Returns the first non-null failure message from Lucene's own AssertingScorer, or null. */
  String assertingScore(Similarity sim, Query q) throws Exception {
    try (Directory dir = index(sim, new String[][] {{"a b c", null}, {"a a d", null}, {"e f", null}});
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new AssertingIndexSearcher(random(), r);
      s.setSimilarity(sim);
      Weight w = s.createWeight(s.rewrite(q), ScoreMode.COMPLETE, 1f);
      for (LeafReaderContext ctx : r.leaves()) {
        Scorer sc = w.scorer(ctx); // AssertingWeight -> AssertingScorer
        if (sc == null) continue;
        DocIdSetIterator it = sc.iterator();
        for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
          try {
            float score = sc.score();
            System.out.println("  " + q + " doc=" + doc + " score=" + bits(score));
          } catch (AssertionError e) {
            System.out.println("  " + q + " doc=" + doc + " AssertingScorer: " + e + " at "
                + e.getStackTrace()[0]);
            return "AssertingScorer rejected score at doc " + doc + ": " + e;
          }
        }
      }
    }
    return null;
  }

  /** Legal config c=0: plain TermQuery scored by IB(LL, D, H1(0)). */
  public void testAssertingScorerTermQueryC0() throws Exception {
    assertTrue("run with -ea", AssertingIndexSearcher.class.desiredAssertionStatus());
    Similarity sim = new IBSimilarity(new DistributionLL(), new LambdaDF(), new NormalizationH1(0f));
    String fail = assertingScore(sim, new TermQuery(new Term("f", "a")));
    assertNull(fail, fail);
  }

  /** Default hyper-parameters; tiny but legal SynonymQuery term boost (must be in (0,1]). */
  public void testAssertingScorerSynonymTinyBoostDefaultParams() throws Exception {
    Similarity sim = new IBSimilarity(new DistributionLL(), new LambdaDF(), new NormalizationH1());
    Query q = new SynonymQuery.Builder("f").addTerm(new Term("f", "a"), 1e-20f).build();
    String fail = assertingScore(sim, q);
    assertNull(fail, fail);
  }

  /** Control: same two scenarios with SPL pass AssertingScorer (SPL clamps to +0.0). */
  public void testControlAssertingScorerSPL() throws Exception {
    Similarity sim =
        new IBSimilarity(new DistributionSPL(), new LambdaDF(), new NormalizationH1(0f));
    assertNull(assertingScore(sim, new TermQuery(new Term("f", "a"))));
  }

  // ---------------------------------------------------------------- 4. ranking tie-break

  /**
   * TopScoreDocCollector javadoc: "Hits are sorted by score descending and then (when the scores
   * are tied) docID ascending." doc0 matches only the LL term (score -0.0f); doc1 also matches a
   * zero-boosted optional clause (score -0.0 + 0.0 = +0.0f). -0.0f == +0.0f, so docID order is
   * expected: [0, 1]. DocScoreEncoder uses floatToSortableInt, which orders -0.0 below +0.0.
   */
  List<Integer> tieOrder(Distribution d) throws Exception {
    Similarity sim = new IBSimilarity(d, new LambdaDF(), new NormalizationH1(0f));
    try (Directory dir = index(sim, new String[][] {{"a", null}, {"a", "x"}, {"z", null}});
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r); // single slice, no executor
      s.setSimilarity(sim);
      Query q =
          new BooleanQuery.Builder()
              .add(new TermQuery(new Term("f", "a")), Occur.MUST)
              .add(new BoostQuery(new ConstantScoreQuery(new TermQuery(new Term("g", "x"))), 0f),
                  Occur.SHOULD)
              .build();
      TopDocs td = s.search(q, new TopScoreDocCollectorManager(10, Integer.MAX_VALUE));
      List<Integer> order = new ArrayList<>();
      for (ScoreDoc sd : td.scoreDocs) {
        System.out.println("  " + d + " doc=" + sd.doc + " score=" + bits(sd.score));
        order.add(sd.doc);
      }
      System.out.println("  " + d + " collector order=" + order + " (scores equal by ==: "
          + (td.scoreDocs.length == 2 && td.scoreDocs[0].score == td.scoreDocs[1].score) + ")");
      return order;
    }
  }

  public void testTieBreakByDocIdLL() throws Exception {
    assertEquals(List.of(0, 1), tieOrder(new DistributionLL()));
  }

  public void testControlTieBreakByDocIdSPL() throws Exception {
    assertEquals(List.of(0, 1), tieOrder(new DistributionSPL()));
  }

  // ---------------------------------------------------------------- 5. slicing dependence

  /**
   * Same query, doc0 (-0.0f) and doc1 (+0.0f) in two different segments. With one slice the
   * collector heap (DocScoreEncoder) orders them; with one slice per segment TopDocs.merge
   * compares with {@code <}/{@code >} (treats them as tied) and breaks the tie by doc id.
   */
  List<Integer> slicedOrder(boolean onePerSegment) throws Exception {
    Similarity sim = new IBSimilarity(new DistributionLL(), new LambdaDF(), new NormalizationH1(0f));
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
    iwc.setSimilarity(sim);
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      String[][] docs = {{"a", null}, {"a", "x"}, {"z", null}};
      for (String[] d : docs) {
        Document doc = new Document();
        doc.add(new TextField("f", d[0], Field.Store.NO));
        if (d[1] != null) doc.add(new TextField("g", d[1], Field.Store.NO));
        w.addDocument(doc);
        w.commit(); // one segment per doc
      }
    }
    ExecutorService ex = Executors.newFixedThreadPool(2);
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s =
          new IndexSearcher(r, ex) {
            @Override
            protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
              return onePerSegment
                  ? IndexSearcher.slices(leaves, 1, 1, false)
                  : IndexSearcher.slices(leaves, 1000, 1000, false);
            }
          };
      s.setSimilarity(sim);
      Query q =
          new BooleanQuery.Builder()
              .add(new TermQuery(new Term("f", "a")), Occur.MUST)
              .add(new BoostQuery(new ConstantScoreQuery(new TermQuery(new Term("g", "x"))), 0f),
                  Occur.SHOULD)
              .build();
      TopDocs td = s.search(q, new TopScoreDocCollectorManager(10, Integer.MAX_VALUE));
      List<Integer> order = new ArrayList<>();
      for (ScoreDoc sd : td.scoreDocs) order.add(sd.doc);
      System.out.println("  segments=" + r.leaves().size() + " slices=" + s.getSlices().length
          + " order=" + order);
      return order;
    } finally {
      ex.shutdown();
      dir.close();
    }
  }

  public void testResultOrderIndependentOfSlicing() throws Exception {
    assertEquals(slicedOrder(true), slicedOrder(false));
  }
}
