import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104HnswScalarQuantizedVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104ScalarQuantizedVectorsFormat;
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
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
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.VectorScorer;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Independent verification of candidate F3: OSQ-quantized MAXIMUM_INNER_PRODUCT / EUCLIDEAN
 * scores become NaN for large-magnitude (but finite) vectors.
 *
 * <p>Each test indexes the same documents twice: once with the default float HNSW format
 * (control) and once with an OSQ quantized format, and compares (a) the per-document score from
 * FloatVectorValues.scorer(query) and (b) the hits of a KnnFloatVectorQuery.
 */
public class TestVerifyQuantizedOverflow extends LuceneTestCase {

  static final String F = "vec";

  static Codec codec(KnnVectorsFormat f) {
    return TestUtil.alwaysKnnVectorsFormat(f);
  }

  static Directory index(KnnVectorsFormat format, VectorSimilarityFunction sim, float[][] docs)
      throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(codec(format))
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            .setRAMBufferSizeMB(256);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < docs.length; i++) {
        Document d = new Document();
        d.add(new KnnFloatVectorField(F, docs[i], sim));
        d.add(new StoredField("id", i));
        w.addDocument(d);
      }
      w.commit();
    }
    return dir;
  }

  /** Score of every doc (in ord order) from the leaf's FloatVectorValues.scorer(query). */
  static List<Float> scorerScores(Directory dir, float[] q) throws IOException {
    List<Float> out = new ArrayList<>();
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals(1, r.leaves().size());
      for (LeafReaderContext ctx : r.leaves()) {
        FloatVectorValues fv = ctx.reader().getFloatVectorValues(F);
        VectorScorer s = fv.scorer(q.clone());
        DocIdSetIterator it = s.iterator();
        while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          out.add(s.score());
        }
      }
    }
    return out;
  }

  static TopDocs knn(Directory dir, float[] q, int k) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      return new IndexSearcher(r).search(new KnnFloatVectorQuery(F, q.clone(), k), k);
    }
  }

  static String hits(TopDocs td) {
    StringBuilder sb = new StringBuilder("totalHits=" + td.totalHits + " [");
    for (ScoreDoc sd : td.scoreDocs) {
      sb.append("doc").append(sd.doc).append('=').append(sd.score).append(' ');
    }
    return sb.append(']').toString();
  }

  static float plainDot(float[] a, float[] b) {
    float s = 0;
    for (int i = 0; i < a.length; i++) s += a[i] * b[i];
    return s;
  }

  // ---------------------------------------------------------------------------------------------
  // MIP, dim 2, a single document x = {3e19, 1}; query q = {1e-19, 0.5}.
  // float score: q.x = 3.0 + 0.5 = 3.5 -> scaleMaxInnerProductScore = 4.5 (finite, moderate).
  // centroid c = x, so c.c and x.c = |x|^2 = 9e38 > Float.MAX_VALUE (3.4e38) -> +Inf each.
  // ---------------------------------------------------------------------------------------------
  public void testMipSingleDocLargeMagnitude() throws Exception {
    VectorSimilarityFunction sim = VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT;
    float[] x = {3e19f, 1f};
    float[] q = {1e-19f, 0.5f};
    float expected = sim.compare(q, x);
    System.out.println("MIP float compare(q,x) = " + expected + " ; plain x.x = " + plainDot(x, x));
    assertEquals(4.5f, expected, 1e-5f);

    try (Directory fdir = index(new Lucene99HnswVectorsFormat(), sim, new float[][] {x})) {
      System.out.println("MIP float  format scorer: " + scorerScores(fdir, q));
      System.out.println("MIP float  format knn   : " + hits(knn(fdir, q, 5)));
    }
    List<String> failures = new ArrayList<>();
    for (KnnVectorsFormat fmt :
        new KnnVectorsFormat[] {
          new Lucene104HnswScalarQuantizedVectorsFormat(), new Lucene104ScalarQuantizedVectorsFormat()
        }) {
      try (Directory qdir = index(fmt, sim, new float[][] {x})) {
        List<Float> s = scorerScores(qdir, q);
        TopDocs td = knn(qdir, q, 5);
        System.out.println("MIP " + fmt.getName() + " scorer: " + s);
        System.out.println("MIP " + fmt.getName() + " knn   : " + hits(td));
        if (!Float.isFinite(s.get(0)) || td.scoreDocs.length != 1) {
          failures.add(fmt.getName() + ": score=" + s + " hits=" + td.scoreDocs.length);
        }
      }
    }
    assertTrue("quantized MIP results wrong: " + failures, failures.isEmpty());
  }

  // ---------------------------------------------------------------------------------------------
  // EUCLIDEAN, dim 64. 32 docs: doc0 has one large spike (-r on dim 0), the rest are small.
  // Every float quantity is finite: |doc0|^2 = r^2 = 1e38 < 3.4e38, every doc-doc and query-doc
  // squared distance <= ~1e38. Query = doc0 (float score 1.0).
  // ---------------------------------------------------------------------------------------------
  public void testEuclideanSpikeAllFloatFinite() throws Exception {
    VectorSimilarityFunction sim = VectorSimilarityFunction.EUCLIDEAN;
    int dim = 64, n = 32;
    float r = 1e19f;
    float[][] docs = new float[n][dim];
    java.util.Random rnd = new java.util.Random(42);
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < dim; j++) docs[i][j] = (float) rnd.nextGaussian();
    }
    docs[0][0] = -r;
    float[] q = docs[0].clone();
    // float-path sanity: every squared distance and |x|^2 finite
    float maxSq = 0;
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        float d = 0;
        for (int k = 0; k < dim; k++) {
          float t = docs[i][k] - docs[j][k];
          d += t * t;
        }
        assertTrue(Float.isFinite(d));
        maxSq = Math.max(maxSq, d);
      }
      assertTrue(Float.isFinite(plainDot(docs[i], docs[i])));
    }
    System.out.println("EUC max pairwise float squared distance = " + maxSq);
    assertEquals(1f, sim.compare(q, docs[0]), 0f);

    try (Directory fdir = index(new Lucene99HnswVectorsFormat(), sim, docs)) {
      System.out.println("EUC float  format scorer[0..3]: " + scorerScores(fdir, q).subList(0, 4));
      System.out.println("EUC float  format knn       : " + hits(knn(fdir, q, 3)));
    }
    List<String> failures = new ArrayList<>();
    for (KnnVectorsFormat fmt :
        new KnnVectorsFormat[] {
          new Lucene104HnswScalarQuantizedVectorsFormat(), new Lucene104ScalarQuantizedVectorsFormat()
        }) {
      try (Directory qdir = index(fmt, sim, docs)) {
        List<Float> s = scorerScores(qdir, q);
        TopDocs td = knn(qdir, q, 3);
        System.out.println("EUC " + fmt.getName() + " scorer[0..3]: " + s.subList(0, 4));
        System.out.println("EUC " + fmt.getName() + " knn       : " + hits(td));
        if (!Float.isFinite(s.get(0)) || td.scoreDocs.length == 0 || td.scoreDocs[0].doc != 0) {
          failures.add(fmt.getName() + ": score0=" + s.get(0) + " " + hits(td));
        }
      }
    }
    assertTrue("quantized EUCLIDEAN results wrong: " + failures, failures.isEmpty());
  }

  // ---------------------------------------------------------------------------------------------
  // Blast radius: 300 ordinary docs (gaussian, dim 16) + ONE outlier doc. Query = an ordinary doc.
  // Compares top-10 of the float format (exact, k >= n so it is exhaustive) with the quantized
  // formats. Informational per similarity; fails if quantized top-10 overlap with float < 5.
  // ---------------------------------------------------------------------------------------------
  public void testOneOutlierDocBlastRadius() throws Exception {
    int dim = 16, n = 300;
    List<String> failures = new ArrayList<>();
    for (VectorSimilarityFunction sim :
        new VectorSimilarityFunction[] {
          VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT, VectorSimilarityFunction.EUCLIDEAN
        }) {
      float[][] docs = new float[n + 1][dim];
      java.util.Random rnd = new java.util.Random(7);
      for (int i = 0; i < n + 1; i++) {
        for (int j = 0; j < dim; j++) docs[i][j] = (float) rnd.nextGaussian();
      }
      // outlier: MIP -> |c| = |x|/301 > 1.85e19 so c.c overflows; EUCLIDEAN -> |x - c|^2 overflows
      float big = sim == VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT ? 6e21f : 2e19f;
      docs[n][0] = big;
      float[] q = docs[3].clone();
      // float ground truth by brute force with the float similarity
      Integer[] order = new Integer[n + 1];
      for (int i = 0; i <= n; i++) order[i] = i;
      final float[][] D = docs;
      java.util.Arrays.sort(order, (a, b) -> Float.compare(sim.compare(q, D[b]), sim.compare(q, D[a])));
      java.util.Set<Integer> truth = new java.util.HashSet<>();
      for (int i = 0; i < 10; i++) truth.add(order[i]);
      System.out.println("BLAST " + sim + " float top-10 truth=" + truth);
      for (KnnVectorsFormat fmt :
          new KnnVectorsFormat[] {
            new Lucene99HnswVectorsFormat(),
            new Lucene104HnswScalarQuantizedVectorsFormat(),
            new Lucene104ScalarQuantizedVectorsFormat()
          }) {
        try (Directory dir = index(fmt, sim, docs)) {
          TopDocs td = knn(dir, q, 10);
          int overlap = 0;
          for (ScoreDoc sd : td.scoreDocs) if (truth.contains(sd.doc)) overlap++;
          List<Float> s = scorerScores(dir, q);
          int nan = 0;
          for (float f : s) if (Float.isNaN(f)) nan++;
          System.out.println(
              "BLAST " + sim + " " + fmt.getName() + " hits=" + td.scoreDocs.length
                  + " overlapWithFloatTop10=" + overlap + " NaNscores=" + nan
                  + " score(doc3)=" + s.get(3) + " score(outlier)=" + s.get(n)
                  + " first3=" + (td.scoreDocs.length > 0 ? hits(td).substring(0, Math.min(120, hits(td).length())) : "[]"));
          if (overlap < 5) failures.add(sim + "/" + fmt.getName() + " overlap=" + overlap);
        }
      }
    }
    assertTrue("outlier doc destroyed quantized kNN: " + failures, failures.isEmpty());
  }

  // ---------------------------------------------------------------------------------------------
  // Amplification (informational): flat quantized format, 130 EUCLIDEAN docs, doc0 = spike (NaN).
  // Exhaustive search scores ords in batches of 64 and only collects a batch if bulkScore's max
  // (Math.max, NaN-propagating) beats minCompetitiveSimilarity: the NaN drops ords 0..63 entirely.
  // ---------------------------------------------------------------------------------------------
  public void testNaNDropsWholeExhaustiveBatch() throws Exception {
    VectorSimilarityFunction sim = VectorSimilarityFunction.EUCLIDEAN;
    int dim = 64, n = 130;
    float[][] docs = new float[n][dim];
    java.util.Random rnd = new java.util.Random(42);
    for (int i = 0; i < n; i++) for (int j = 0; j < dim; j++) docs[i][j] = (float) rnd.nextGaussian();
    docs[0][0] = -1e19f;
    float[] q = docs[0].clone(); // query = the spike doc itself (float score 1.0)
    try (Directory dir = index(new Lucene104ScalarQuantizedVectorsFormat(), sim, docs)) {
      List<Float> s = scorerScores(dir, q);
      TopDocs td = knn(dir, q, 5);
      int nan = 0, finiteFirstBatch = 0;
      for (int i = 0; i < n; i++) {
        if (Float.isNaN(s.get(i))) nan++;
        else if (i < 64) finiteFirstBatch++;
      }
      System.out.println("BATCH NaN scores=" + nan + " finite scores among ords 0..63=" + finiteFirstBatch
          + " score(doc0)=" + s.get(0) + " score(doc1)=" + s.get(1) + " knn: " + hits(td));
      int minDoc = Integer.MAX_VALUE;
      for (ScoreDoc sd : td.scoreDocs) minDoc = Math.min(minDoc, sd.doc);
      System.out.println("BATCH smallest returned doc id = " + minDoc + " (all of ords 0..63 dropped if >= 64)");
      assertTrue("whole first batch dropped: smallest returned doc=" + minDoc, minDoc < 64);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Threshold scan (informational, never fails): dim 1 MIP single doc x = {v}, query {1e-30}.
  // Reports the smallest v (power-of-two grid) for which the quantized score is not finite.
  // ---------------------------------------------------------------------------------------------
  public void testScanMipDim1Threshold() throws Exception {
    VectorSimilarityFunction sim = VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT;
    for (int e = 60; e <= 66; e++) {
      for (float m : new float[] {1.0f, 1.5f, Math.nextDown(2.0f)}) {
        float v = Math.scalb(m, e);
        float[] x = {v};
        float[] q = {1e-19f};
        try (Directory qdir =
            index(new Lucene104ScalarQuantizedVectorsFormat(), sim, new float[][] {x})) {
          float s = scorerScores(qdir, q).get(0);
          System.out.println(
              "SCAN dim1 v=" + v + " (m=" + m + ",2^" + e + ") float=" + sim.compare(q, x)
                  + " quantized=" + s);
        }
      }
    }
  }
}
