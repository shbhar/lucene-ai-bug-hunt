import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104HnswScalarQuantizedVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104ScalarQuantizedVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.VectorScorer;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.VectorUtil;
import org.apache.lucene.util.quantization.OptimizedScalarQuantizer;
import org.apache.lucene.util.quantization.OptimizedScalarQuantizer.QuantizationResult;
import org.apache.lucene.util.quantization.QuantizedByteVectorValues.ScalarEncoding;

/**
 * Independent verification of candidate F7: in Lucene104ScalarQuantizedVectorScorer.quantizedScore
 * (lines 305-308 on main 7cecf79523) the MIP/COSINE tail is evaluated as {@code score + ((qAdd +
 * xAdd) - centroidDP)}, so qAdd = q.c can be absorbed by xAdd = x.c when |x.c| >> |q.c|.
 */
public class TestF7Verify extends LuceneTestCase {

  static final String F = "v";
  static final VectorSimilarityFunction MIP = VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT;

  private static IndexWriterConfig iwc(KnnVectorsFormat fmt) {
    return new IndexWriterConfig()
        .setCodec(TestUtil.alwaysKnnVectorsFormat(fmt))
        .setMergePolicy(NoMergePolicy.INSTANCE)
        .setMaxBufferedDocs(1_000_000)
        .setRAMBufferSizeMB(256);
  }

  private static void index(Directory dir, KnnVectorsFormat fmt, List<float[]> docs)
      throws IOException {
    try (IndexWriter w = new IndexWriter(dir, iwc(fmt))) {
      for (float[] v : docs) {
        Document d = new Document();
        d.add(new KnnFloatVectorField(F, v, MIP));
        w.addDocument(d);
      }
      w.commit();
    }
  }

  /** Scores returned through the public search API (KnnFloatVectorQuery top-k). */
  private static float knnTop1Score(Directory dir, float[] q) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      TopDocs td = new IndexSearcher(r).search(new KnnFloatVectorQuery(F, q.clone(), 1), 1);
      return td.scoreDocs[0].score;
    }
  }

  /** Scores for every doc in ordinal order through FloatVectorValues.scorer (quantized scorer). */
  private static float[] scorerScores(Directory dir, float[] q) throws IOException {
    List<Float> out = new ArrayList<>();
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals(1, r.leaves().size());
      for (LeafReaderContext ctx : r.leaves()) {
        FloatVectorValues fv = ctx.reader().getFloatVectorValues(F);
        VectorScorer s = fv.scorer(q.clone());
        DocIdSetIterator it = s.iterator();
        for (int d = it.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = it.nextDoc()) {
          out.add(s.score());
        }
      }
    }
    float[] a = new float[out.size()];
    for (int i = 0; i < a.length; i++) a[i] = out.get(i);
    return a;
  }

  // ------------------------------------------------------------------------------------------
  // 1. The reported symptom, through the public search API, default encoding (UNSIGNED_BYTE).
  // ------------------------------------------------------------------------------------------
  public void testOneDocSegmentMipScoreViaKnnQuery() throws Exception {
    float[] doc = {3000f, 4000f};
    float[] q = {3e-4f, 4e-4f};
    float expected = MIP.compare(q, doc);
    try (Directory dir = new ByteBuffersDirectory()) {
      index(dir, new Lucene104HnswScalarQuantizedVectorsFormat(), List.of(doc));
      float got = knnTop1Score(dir, q);
      float rescored;
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        VectorScorer rs = r.leaves().get(0).reader().getFloatVectorValues(F).rescorer(q.clone());
        rs.iterator().nextDoc();
        rescored = rs.score();
      }
      System.out.println(
          "[T1] HNSW-OSQ(UNSIGNED_BYTE) 1-doc: float MIP score=" + expected + " quantized=" + got
              + " rescorer()=" + rescored);
      assertEquals("quantized MIP score of the only doc (== centroid)", expected, got, 1e-3f);
    }
  }

  // ------------------------------------------------------------------------------------------
  // 2. Same input across every encoding, flat and HNSW (report-only; asserts nothing).
  // ------------------------------------------------------------------------------------------
  public void testAllEncodingsReport() throws Exception {
    float[] doc = {3000f, 4000f};
    float[] q = {3e-4f, 4e-4f};
    float expected = MIP.compare(q, doc);
    for (ScalarEncoding enc : ScalarEncoding.values()) {
      for (boolean hnsw : new boolean[] {false, true}) {
        KnnVectorsFormat fmt =
            hnsw
                ? new Lucene104HnswScalarQuantizedVectorsFormat(enc, 16, 100)
                : new Lucene104ScalarQuantizedVectorsFormat(enc);
        try (Directory dir = new ByteBuffersDirectory()) {
          index(dir, fmt, List.of(doc));
          System.out.println(
              String.format(
                  Locale.ROOT,
                  "[T2] %-24s %-5s float=%.7f knn=%.7f scorer=%.7f",
                  enc,
                  hnsw ? "hnsw" : "flat",
                  expected,
                  knnTop1Score(dir, q),
                  scorerScores(dir, q)[0]));
        }
      }
    }
  }

  // ------------------------------------------------------------------------------------------
  // Model of quantizedScore for UNSIGNED_BYTE (8-bit doc, 8-bit query) built only from public
  // APIs, with the tail evaluated in both associations.
  // ------------------------------------------------------------------------------------------
  static final class Model {
    final float[] centroid;
    final float cDP;
    final List<byte[]> docCodes = new ArrayList<>();
    final List<QuantizationResult> docCorr = new ArrayList<>();
    final int dim;

    Model(List<float[]> docs) {
      dim = docs.get(0).length;
      float[] sums = new float[dim];
      for (float[] v : docs) for (int i = 0; i < dim; i++) sums[i] += v[i]; // writer:777
      centroid = new float[dim];
      for (int i = 0; i < dim; i++) centroid[i] = sums[i] / docs.size(); // writer:791
      cDP = VectorUtil.dotProduct(centroid, centroid); // writer:172
      OptimizedScalarQuantizer osq = new OptimizedScalarQuantizer(MIP);
      for (float[] v : docs) {
        byte[] dst = new byte[dim];
        docCorr.add(osq.scalarQuantize(v.clone(), dst, (byte) 8, centroid));
        docCodes.add(dst);
      }
    }

    /** returns {currentOrderScore, reassociatedScore} after scaleMaxInnerProductScore */
    float[][] score(float[] query) {
      OptimizedScalarQuantizer osq = new OptimizedScalarQuantizer(MIP);
      byte[] qc = new byte[dim];
      QuantizationResult qr = osq.scalarQuantize(query.clone(), qc, (byte) 8, centroid);
      float s = 1f / 255f;
      float[][] out = new float[2][docCodes.size()];
      for (int o = 0; o < docCodes.size(); o++) {
        QuantizationResult xr = docCorr.get(o);
        float qcDist = VectorUtil.uint8DotProduct(qc, docCodes.get(o));
        float x1 = xr.quantizedComponentSum();
        float ax = xr.lowerInterval();
        float lx = (xr.upperInterval() - ax) * s;
        float ay = qr.lowerInterval();
        float ly = (qr.upperInterval() - ay) * s;
        float y1 = qr.quantizedComponentSum();
        float est = ax * ay * dim + ay * lx * x1 + ax * ly * y1 + lx * ly * qcDist;
        float cur = est;
        cur += qr.additionalCorrection() + xr.additionalCorrection() - cDP; // Scorer:305-308
        float alt = est;
        alt += qr.additionalCorrection() + (xr.additionalCorrection() - cDP); // proposed
        out[0][o] = VectorUtil.scaleMaxInnerProductScore(cur);
        out[1][o] = VectorUtil.scaleMaxInnerProductScore(alt);
      }
      return out;
    }
  }

  // ------------------------------------------------------------------------------------------
  // 3. Root cause: the model with the current association reproduces the real score bit-exactly,
  //    and the re-association yields the float score.
  // ------------------------------------------------------------------------------------------
  public void testRootCauseModelMatchesRealScorer() throws Exception {
    float[] doc = {3000f, 4000f};
    float[] q = {3e-4f, 4e-4f};
    Model m = new Model(List.of(doc));
    float[][] ms = m.score(q);
    float qAdd = new OptimizedScalarQuantizer(MIP).scalarQuantize(q.clone(), new byte[2], (byte) 8, m.centroid).additionalCorrection();
    System.out.println(
        "[T3] qAdd=" + qAdd + " xAdd=" + m.docCorr.get(0).additionalCorrection() + " cDP=" + m.cDP
            + " ulp(xAdd)=" + Math.ulp(m.docCorr.get(0).additionalCorrection())
            + " (qAdd+xAdd)=" + (qAdd + m.docCorr.get(0).additionalCorrection())
            + " model(current)=" + ms[0][0] + " model(reassoc)=" + ms[1][0]);
    try (Directory dir = new ByteBuffersDirectory()) {
      index(dir, new Lucene104ScalarQuantizedVectorsFormat(), List.of(doc));
      float real = scorerScores(dir, q)[0];
      assertEquals(Float.floatToIntBits(ms[0][0]), Float.floatToIntBits(real));
    }
    assertEquals(3.0f, ms[0][0], 0f);
    assertEquals(MIP.compare(q, doc), ms[1][0], 1e-6f);
  }

  // ------------------------------------------------------------------------------------------
  // 4. Realistic dimension: a 1-doc segment at dim 768/1024 with |x| ~ 5000. Does the proposed
  //    re-association make it exact? xAdd (scalar loop, OSQ:202-203) and cDP
  //    (VectorUtil.dotProduct, maybe SIMD) are accumulated in different orders.
  // ------------------------------------------------------------------------------------------
  public void testHighDimOneDocSegmentReport() throws Exception {
    Random rnd = new Random(42);
    for (int dim : new int[] {2, 64, 768, 1024}) {
      for (double docNorm : new double[] {1, 100, 5000}) {
        float[] doc = randUnit(rnd, dim, docNorm);
        float[] q = randUnit(rnd, dim, 1e-3);
        // make q correlated with doc so the true score is not ~0
        for (int i = 0; i < dim; i++) q[i] = (float) (q[i] * 0.3 + doc[i] * (1e-3 / docNorm));
        Model m = new Model(List.of(doc));
        float[][] ms = m.score(q);
        float real;
        try (Directory dir = new ByteBuffersDirectory()) {
          index(dir, new Lucene104ScalarQuantizedVectorsFormat(), List.of(doc));
          real = scorerScores(dir, q)[0];
        }
        float exp = MIP.compare(q, doc);
        System.out.println(
            String.format(
                Locale.ROOT,
                "[T4] dim=%4d |x|=%6.0f xAdd-cDP=%+.3e float=%.6f real=%.6f (err %+.2e) modelCur=%.6f reassoc=%.6f (err %+.2e) modelCur==real:%s",
                dim,
                docNorm,
                (double) (m.docCorr.get(0).additionalCorrection() - m.cDP),
                exp,
                real,
                real - exp,
                ms[0][0],
                ms[1][0],
                ms[1][0] - exp,
                Float.floatToIntBits(ms[0][0]) == Float.floatToIntBits(real)));
      }
    }
  }

  // ------------------------------------------------------------------------------------------
  // 5. Multi-doc segment: does the association matter compared to the quantization error?
  //    Large common offset (|mean| ~ 5000), spread s, query norm ~1.
  // ------------------------------------------------------------------------------------------
  public void testMultiDocSegmentErrorBudgetReport() throws Exception {
    Random rnd = new Random(7);
    int dim = 128, n = 500;
    for (double spread : new double[] {1000, 10, 0.1, 1e-3}) {
      float[] mean = randUnit(rnd, dim, 5000);
      List<float[]> docs = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        float[] v = randUnit(rnd, dim, spread);
        for (int k = 0; k < dim; k++) v[k] += mean[k];
        docs.add(v);
      }
      Model m = new Model(docs);
      float[] realScores;
      try (Directory dir = new ByteBuffersDirectory()) {
        index(dir, new Lucene104ScalarQuantizedVectorsFormat(), docs);
        float[] q = randUnit(rnd, dim, 1);
        realScores = scorerScores(dir, q);
        float[][] ms = m.score(q);
        double eCur = 0, eAlt = 0;
        int same = 0;
        for (int i = 0; i < n; i++) {
          float exp = MIP.compare(q, docs.get(i));
          double ed = VectorUtil.dotProduct(q, docs.get(i));
          double denom = Math.sqrt(VectorUtil.dotProduct(q, q) * VectorUtil.dotProduct(docs.get(i), docs.get(i)));
          eCur += Math.abs(rawDot(ms[0][i]) - ed) / denom;
          eAlt += Math.abs(rawDot(ms[1][i]) - ed) / denom;
          if (Float.floatToIntBits(ms[0][i]) == Float.floatToIntBits(realScores[i])) same++;
        }
        System.out.println(
            String.format(
                Locale.ROOT,
                "[T5] dim=%d n=%d |mean|=5000 spread=%g : mean |err(dot)|/(|q||x|) current=%.3e reassoc=%.3e ; model==real for %d/%d docs",
                dim, n, spread, eCur / n, eAlt / n, same, n));
      }
    }
  }


  static double rawDot(float scaled) {
    return scaled >= 1f ? scaled - 1.0 : 1.0 - 1.0 / scaled;
  }

  // ------------------------------------------------------------------------------------------
  // 6. Reachability sweep through the real scorer: one-doc segment, dim 768, cos(q,x)=0.5,
  //    relative error of the quantized raw dot product vs the float raw dot product.
  // ------------------------------------------------------------------------------------------
  public void testOneDocRelativeErrorSweepReport() throws Exception {
    Random rnd = new Random(3);
    int dim = 768;
    for (double xn : new double[] {1, 10, 100, 1000, 5000, 20000}) {
      StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "[T6] |x|=%6.0f :", xn));
      float[] doc = randUnit(rnd, dim, xn);
      for (double qn : new double[] {1e-3, 1e-1, 1, 10, 100}) {
        // q = qn * (0.5 * xhat + sqrt(0.75) * orthogonal unit)
        float[] o = randUnit(rnd, dim, 1);
        double d = 0;
        for (int i = 0; i < dim; i++) d += o[i] * (doc[i] / xn);
        double on = 0;
        double[] oo = new double[dim];
        for (int i = 0; i < dim; i++) {
          oo[i] = o[i] - d * doc[i] / xn;
          on += oo[i] * oo[i];
        }
        on = Math.sqrt(on);
        float[] q = new float[dim];
        for (int i = 0; i < dim; i++)
          q[i] = (float) (qn * (0.5 * doc[i] / xn + Math.sqrt(0.75) * oo[i] / on));
        float real;
        try (Directory dir = new ByteBuffersDirectory()) {
          index(dir, new Lucene104ScalarQuantizedVectorsFormat(), List.of(doc));
          real = scorerScores(dir, q)[0];
        }
        double exp = VectorUtil.dotProduct(q, doc);
        double rel = Math.abs(rawDot(real) - exp) / Math.abs(exp);
        sb.append(String.format(Locale.ROOT, " |q|=%g rel=%.1e;", qn, rel));
      }
      System.out.println(sb);
    }
  }

  private static float[] randUnit(Random rnd, int dim, double norm) {
    double[] g = new double[dim];
    double s = 0;
    for (int i = 0; i < dim; i++) {
      g[i] = rnd.nextGaussian();
      s += g[i] * g[i];
    }
    s = Math.sqrt(s);
    float[] v = new float[dim];
    for (int i = 0; i < dim; i++) v[i] = (float) (g[i] / s * norm);
    return v;
  }
}
