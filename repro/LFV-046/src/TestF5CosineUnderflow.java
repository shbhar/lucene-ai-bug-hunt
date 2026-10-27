import java.util.Arrays;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent verification of candidate F5: float COSINE returns a non-finite / out-of-range score
 * for a legal, non-zero vector whose float32 squared norm underflows.
 *
 * <p>Every vector used here is accepted by KnnFloatVectorField (finite, not all-zero).
 */
public class TestF5CosineUnderflow extends LuceneTestCase {

  static final VectorSimilarityFunction COS = VectorSimilarityFunction.COSINE;

  // a legal non-zero vector: 1e-30^2 = 1e-60 < 2^-150, so fma(x, x, 0f) rounds to +0f
  static final float[] TINY = {1e-30f, 0f};
  static final float[] QUERY = {0.6f, 0.8f};

  /** Calls COSINE.compare and turns Lucene's own internal assertion into a test failure message. */
  static float score(float[] q, float[] d) {
    try {
      return COS.compare(q, d);
    } catch (AssertionError e) {
      throw new AssertionError(
          "VectorUtil.cosine's internal 'assert Float.isFinite(r)' fired for q="
              + Arrays.toString(q)
              + " d="
              + Arrays.toString(d),
          e);
    }
  }

  static void assertValidCosineScore(String what, float s) {
    System.out.println("  " + what + " -> " + s);
    assertTrue(
        what + ": COSINE score must be a finite value in [0,1] but was " + s,
        Float.isFinite(s) && s >= 0f && s <= 1f);
  }

  /** 1. The field accepts the vector (precondition of the claim). */
  public void testFieldAcceptsTinyVector() {
    KnnFloatVectorField f = new KnnFloatVectorField("v", TINY, COS);
    assertNotNull(f);
    System.out.println("testFieldAcceptsTinyVector: KnnFloatVectorField accepted " + Arrays.toString(TINY));
  }

  /** 2. Positive dot product: +Infinity. */
  public void testPositiveDotGivesInfinity() {
    System.out.println("testPositiveDotGivesInfinity:");
    assertValidCosineScore("compare({0.6,0.8},{1e-30,0})", score(QUERY, TINY));
  }

  /** 3. Orthogonal query: 0/0 = NaN (Math.max(NaN, 0) stays NaN). */
  public void testOrthogonalGivesNaN() {
    System.out.println("testOrthogonalGivesNaN:");
    assertValidCosineScore("compare({0,1},{1e-30,0})", score(new float[] {0f, 1f}, TINY));
  }

  /** 4. A tiny vector against itself: dot product also underflows, 0/0 = NaN. */
  public void testTinySelfSimilarityIsNaN() {
    System.out.println("testTinySelfSimilarityIsNaN:");
    assertValidCosineScore("compare({1e-30,0},{1e-30,0})", score(TINY, TINY));
  }

  /** 5. Finite but > 1: squared norm rounds DOWN in the subnormal range. */
  public void testSubnormalNormGivesScoreAboveOne() {
    // x^2 = 1.49 * 2^-149 is rounded to 1 * 2^-149, so |x| / sqrt(fl(x^2)) ~= sqrt(1.49) ~= 1.22
    float x = (float) Math.sqrt(1.49 * Math.scalb(1.0, -149));
    float[] doc = {x, 0f};
    float[] q = {1f, 0f};
    System.out.println("testSubnormalNormGivesScoreAboveOne: x=" + x + " fma(x,x,0)=" + Math.fma(x, x, 0f));
    float sExact = COS.compare(q, q); // identical vector
    System.out.println("  compare({1,0},{1,0}) -> " + sExact);
    assertValidCosineScore("compare({1,0},{" + x + ",0})", score(q, doc));
  }

  /** 6. End to end with the default codec: kNN top hit for {0.6,0.8} must be the identical vector. */
  public void testKnnTopHitIsIdenticalVector() throws Exception {
    System.out.println("testKnnTopHitIsIdenticalVector (codec=" + Codec.forName("Lucene104").getName() + "):");
    try (Directory dir = newDir()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(Codec.forName("Lucene104"));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        add(w, "exact", QUERY);
        add(w, "near", new float[] {0.8f, 0.6f});
        add(w, "tiny", TINY);
        for (int i = 0; i < 20; i++) {
          float a = (float) (i * Math.PI / 40); // angles in [0, pi/2)
          add(w, "r" + i, new float[] {(float) Math.cos(a), (float) Math.sin(a)});
        }
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        TopDocs td = s.search(new KnnFloatVectorQuery("v", QUERY, 3), 3);
        for (ScoreDoc sd : td.scoreDocs) {
          System.out.println(
              "  hit id=" + s.storedFields().document(sd.doc).get("id") + " score=" + sd.score);
        }
        String top = s.storedFields().document(td.scoreDocs[0].doc).get("id");
        assertTrue("top score must be finite, was " + td.scoreDocs[0].score, Float.isFinite(td.scoreDocs[0].score));
        assertEquals("top hit for {0.6,0.8} must be the identical vector", "exact", top);
      }
    }
  }

  /**
   * 7. Many identical tiny vectors (each pair has NaN similarity) plus normal ones, two segments,
   * force-merged, then CheckIndex. Observational: prints what happens.
   */
  public void testTinyDuplicatesMergeAndCheckIndex() throws Exception {
    System.out.println("testTinyDuplicatesMergeAndCheckIndex:");
    try (Directory dir = newDir()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(Codec.forName("Lucene104"))
              .setMergeScheduler(new SerialMergeScheduler());
      Throwable failure = null;
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int seg = 0; seg < 2; seg++) {
          for (int i = 0; i < 100; i++) {
            float a = (float) (i * Math.PI / 100);
            add(w, "n" + seg + "_" + i, new float[] {(float) Math.cos(a), (float) Math.sin(a)});
            add(w, "t" + seg + "_" + i, new float[] {1e-30f, 1e-30f});
          }
          w.commit();
        }
        w.forceMerge(1);
        w.commit();
      } catch (Throwable t) {
        failure = t;
        System.out.println("  indexing/merge threw: " + t);
      }
      if (failure == null) {
        System.out.println("  merge completed");
        try (CheckIndex ci = new CheckIndex(dir)) {
          ci.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS);
          java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
          ci.setInfoStream(new java.io.PrintStream(bos, true, "UTF-8"));
          CheckIndex.Status st = ci.checkIndex();
          System.out.println("  CheckIndex clean=" + st.clean);
          for (String line : bos.toString("UTF-8").split("\n")) {
            if (line.contains("vector") || line.contains("FAILED") || line.contains("ERROR")) {
              System.out.println("    | " + line.trim());
            }
          }
          assertTrue("CheckIndex must be clean", st.clean);
        }
      } else {
        throw new AssertionError("indexing or merge failed", failure);
      }
    }
  }

  static float[] gaussian(java.util.Random rnd, int dim) {
    float[] v = new float[dim];
    for (int i = 0; i < dim; i++) {
      v[i] = (float) rnd.nextGaussian();
    }
    return v;
  }

  /**
   * 8. Real HNSW graph (3000 docs, so the tiny-segment brute-force shortcut is NOT taken), one tiny
   * doc with all components 1e-30. For 200 random Gaussian queries, count how often the tiny doc is
   * the top hit, and compare with exact (brute-force) float cosine ranking.
   */
  public void testHnswGraphTinyDocTopHitRate() throws Exception {
    final int dim = 16, n = 3000, nq = 200;
    java.util.Random rnd = new java.util.Random(42);
    float[] tiny = new float[dim];
    Arrays.fill(tiny, 1e-30f);
    float[][] docs = new float[n][];
    try (Directory dir = newDir()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig().setCodec(Codec.forName("Lucene104")).setRAMBufferSizeMB(256);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < n; i++) {
          docs[i] = (i == 1234) ? tiny : gaussian(rnd, dim);
          add(w, (i == 1234) ? "tiny" : ("d" + i), docs[i]);
        }
        w.forceMerge(1);
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        IndexSearcher s = new IndexSearcher(r);
        int tinyTop = 0, posDot = 0, nonFiniteTop = 0, exactTopMismatch = 0;
        for (int q = 0; q < nq; q++) {
          float[] qv = gaussian(rnd, dim);
          double dot = 0;
          for (float x : qv) dot += x;
          if (dot > 0) posDot++;
          TopDocs td = s.search(new KnnFloatVectorQuery("v", qv, 10), 10);
          String top = s.storedFields().document(td.scoreDocs[0].doc).get("id");
          if (top.equals("tiny")) tinyTop++;
          if (!Float.isFinite(td.scoreDocs[0].score)) nonFiniteTop++;
          // exact best among NORMAL docs, in double precision
          int best = -1;
          double bestCos = -2;
          for (int i = 0; i < n; i++) {
            if (i == 1234) continue;
            double d = 0, nq2 = 0, nd2 = 0;
            for (int j = 0; j < dim; j++) {
              d += (double) qv[j] * docs[i][j];
              nq2 += (double) qv[j] * qv[j];
              nd2 += (double) docs[i][j] * docs[i][j];
            }
            double c = d / Math.sqrt(nq2 * nd2);
            if (c > bestCos) {
              bestCos = c;
              best = i;
            }
          }
          if (!top.equals("d" + best)) exactTopMismatch++;
        }
        System.out.println(
            "testHnswGraphTinyDocTopHitRate: queries="
                + nq
                + " withPositiveDotToTiny="
                + posDot
                + " tinyDocWasTopHit="
                + tinyTop
                + " nonFiniteTopScore="
                + nonFiniteTop
                + " topHitNotTrueNearestNormalDoc="
                + exactTopMismatch);
        assertEquals("the tiny doc should never be the top hit", 0, tinyTop);
      }
    }
  }

  /**
   * 9. Many identical tiny vectors in a real HNSW graph (NaN pairwise similarity), two segments
   * force-merged, then CheckIndex.
   */
  public void testHnswGraphManyTinyDocsMergeCheckIndex() throws Exception {
    final int dim = 16;
    java.util.Random rnd = new java.util.Random(7);
    float[] tiny = new float[dim];
    Arrays.fill(tiny, 1e-30f);
    try (Directory dir = newDir()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(Codec.forName("Lucene104"))
              .setRAMBufferSizeMB(256)
              .setMergeScheduler(new SerialMergeScheduler());
      Throwable failure = null;
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int seg = 0; seg < 2; seg++) {
          for (int i = 0; i < 1500; i++) {
            add(w, "s" + seg + "_" + i, (i % 10 == 0) ? tiny : gaussian(rnd, dim));
          }
          w.commit();
        }
        w.forceMerge(1);
        w.commit();
      } catch (Throwable t) {
        failure = t;
      }
      System.out.println(
          "testHnswGraphManyTinyDocsMergeCheckIndex: indexing+merge "
              + (failure == null ? "completed" : "threw " + failure));
      if (failure != null) {
        throw new AssertionError("indexing or merge failed", failure);
      }
      try (CheckIndex ci = new CheckIndex(dir)) {
        ci.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        ci.setInfoStream(new java.io.PrintStream(bos, true, "UTF-8"));
        CheckIndex.Status st = ci.checkIndex();
        System.out.println("  CheckIndex clean=" + st.clean);
        for (String line : bos.toString("UTF-8").split("\n")) {
          if (line.contains("test: vectors") || line.contains("FAILED") || line.contains("ERROR")) {
            System.out.println("    | " + line.trim());
          }
        }
        assertTrue("CheckIndex must be clean", st.clean);
      }
    }
  }

  /**
   * 10. NaN amplification: a query orthogonal to the tiny doc gives it NaN; bulkScore() returns
   * Math.max over the batch = NaN, and "NaN > minCompetitiveSimilarity" is false, so the whole
   * batch is dropped. Expect the identical vector {0,1} as top hit.
   */
  public void testOrthogonalQueryReturnsIdenticalVector() throws Exception {
    try (Directory dir = newDir()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(Codec.forName("Lucene104")))) {
        add(w, "exact01", new float[] {0f, 1f});
        add(w, "tiny", TINY);
        add(w, "d10", new float[] {1f, 0f});
        add(w, "d0707", new float[] {0.7071f, 0.7071f});
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        TopDocs td = s.search(new KnnFloatVectorQuery("v", new float[] {0f, 1f}, 4), 4);
        System.out.println("testOrthogonalQueryReturnsIdenticalVector: hits=" + td.scoreDocs.length);
        for (ScoreDoc sd : td.scoreDocs) {
          System.out.println("  hit id=" + s.storedFields().document(sd.doc).get("id") + " score=" + sd.score);
        }
        assertTrue("kNN for {0,1} returned no hits at all", td.scoreDocs.length > 0);
        assertEquals("exact01", s.storedFields().document(td.scoreDocs[0].doc).get("id"));
      }
    }
  }

  static float[] unitGaussian(java.util.Random rnd, int dim) {
    float[] v = gaussian(rnd, dim);
    double n = 0;
    for (float x : v) n += (double) x * x;
    n = Math.sqrt(n);
    for (int i = 0; i < dim; i++) v[i] = (float) (v[i] / n);
    return v;
  }

  /** recall@k of kNN against exact double-precision cosine ground truth over the normal docs. */
  private double recallWithOptionalBlackHole(boolean withBlackHole, int n, int nq, boolean[] sawNonEmpty)
      throws Exception {
    final int dim = 64, k = 10;
    java.util.Random rnd = new java.util.Random(99);
    float[][] docs = new float[n][];
    for (int i = 0; i < n; i++) docs[i] = unitGaussian(rnd, dim);
    float[] hole = new float[dim];
    Arrays.fill(hole, Float.MIN_VALUE); // finite, non-zero: accepted by KnnFloatVectorField
    float[][] queries = new float[nq][];
    for (int q = 0; q < nq; q++) queries[q] = unitGaussian(rnd, dim);
    try (Directory dir = newDir()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(Codec.forName("Lucene104")).setRAMBufferSizeMB(256);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < n; i++) {
          if (withBlackHole && i == n / 2) add(w, "hole", hole);
          add(w, "d" + i, docs[i]);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        long found = 0;
        sawNonEmpty[0] = true;
        for (int q = 0; q < nq; q++) {
          // exact top-k among normal docs
          Integer[] idx = new Integer[n];
          double[] c = new double[n];
          for (int i = 0; i < n; i++) {
            idx[i] = i;
            double d = 0;
            for (int j = 0; j < dim; j++) d += (double) queries[q][j] * docs[i][j];
            c[i] = d;
          }
          Arrays.sort(idx, (x, y) -> Double.compare(c[y], c[x]));
          java.util.Set<String> truth = new java.util.HashSet<>();
          for (int i = 0; i < k; i++) truth.add("d" + idx[i]);
          TopDocs td = s.search(new KnnFloatVectorQuery("v", queries[q], k), k);
          if (td.scoreDocs.length == 0) sawNonEmpty[0] = false;
          for (ScoreDoc sd : td.scoreDocs) {
            if (truth.contains(s.storedFields().document(sd.doc).get("id"))) found++;
          }
        }
        return found / (double) (nq * k);
      }
    }
  }

  /** 11. One Float.MIN_VALUE doc: recall impact, graph (3000 docs) and exhaustive (200 docs). */
  public void testMinValueDocRecallImpact() throws Exception {
    float[] hole = new float[64];
    Arrays.fill(hole, Float.MIN_VALUE);
    new KnnFloatVectorField("v", hole, COS); // precondition: accepted
    float[] q = unitGaussian(new java.util.Random(1), 64);
    System.out.println("testMinValueDocRecallImpact: compare(q, hole)=" + COS.compare(q, hole));
    boolean[] ne = new boolean[1];
    double gBase = recallWithOptionalBlackHole(false, 3000, 100, ne);
    double gHole = recallWithOptionalBlackHole(true, 3000, 100, ne);
    double eBase = recallWithOptionalBlackHole(false, 200, 100, ne);
    double eHole = recallWithOptionalBlackHole(true, 200, 100, ne);
    boolean eHoleAllNonEmpty = ne[0];
    System.out.println("  HNSW graph (3000 docs): recall@10 without=" + gBase + " with one MIN_VALUE doc=" + gHole);
    System.out.println("  exhaustive (200 docs):  recall@10 without=" + eBase + " with one MIN_VALUE doc=" + eHole
        + " (every query returned >=1 hit: " + eHoleAllNonEmpty + ")");
    assertTrue("one legal doc must not cut exhaustive recall: " + eBase + " -> " + eHole, eHole >= eBase - 0.01);
    assertTrue("one legal doc must not cut graph recall: " + gBase + " -> " + gHole, gHole >= gBase - 0.05);
  }

  /**
   * 12. CheckIndex on a 50-doc segment (one 64-ordinal exhaustive batch) whose doc 0 is the
   * MIN_VALUE vector (doc 0 is always sampled by CheckIndex).
   */
  public void testCheckIndexWithMinValueDoc() throws Exception {
    java.util.Random rnd = new java.util.Random(5);
    float[] hole = new float[64];
    Arrays.fill(hole, Float.MIN_VALUE);
    try (Directory dir = newDir()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(Codec.forName("Lucene104")))) {
        add(w, "hole", hole);
        for (int i = 0; i < 49; i++) add(w, "d" + i, unitGaussian(rnd, 64));
      }
      try (CheckIndex ci = new CheckIndex(dir)) {
        ci.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        ci.setInfoStream(new java.io.PrintStream(bos, true, "UTF-8"));
        CheckIndex.Status st = ci.checkIndex();
        System.out.println("testCheckIndexWithMinValueDoc: CheckIndex clean=" + st.clean);
        for (String line : bos.toString("UTF-8").split("\n")) {
          if (line.contains("test: vectors") || line.contains("FAILED") || line.contains("nearest") || line.contains("CheckIndexException")) {
            System.out.println("    | " + line.trim());
          }
        }
        assertTrue("CheckIndex must be clean for an index of legal vectors", st.clean);
      }
    }
  }

  /** -Df5.dir=mmap selects MMapDirectory (MemorySegment-backed scorers); default ByteBuffersDirectory. */
  private Directory newDir() throws java.io.IOException {
    if ("mmap".equals(System.getProperty("f5.dir"))) {
      return new org.apache.lucene.store.MMapDirectory(createTempDir("f5"));
    }
    return new ByteBuffersDirectory();
  }

  private static void add(IndexWriter w, String id, float[] v) throws Exception {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new KnnFloatVectorField("v", v, COS));
    w.addDocument(d);
  }
}
