import java.util.Random;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104Codec;
import org.apache.lucene.codecs.lucene104.Lucene104HnswScalarQuantizedVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104ScalarQuantizedVectorsFormat;
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.VectorUtil;
import org.apache.lucene.util.quantization.QuantizedByteVectorValues.ScalarEncoding;

/**
 * Independent verification of candidate F2: a COSINE field whose (unit-normalized) vectors sum to
 * exactly zero makes the Lucene104 OSQ writer throw from VectorUtil.l2normalize(centroid).
 *
 * <p>Deterministic: ByteBuffersDirectory, explicit codec / merge policy / SerialMergeScheduler; no
 * newIndexWriterConfig() randomization.
 */
public class TestOsqZeroCentroidVerify extends LuceneTestCase {

  private static final String F = "vec";

  private static Codec codecFor(KnnVectorsFormat fmt) {
    return new Lucene104Codec() {
      @Override
      public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
        return fmt;
      }
    };
  }

  private static IndexWriterConfig iwc(KnnVectorsFormat fmt, MergePolicy mp) {
    IndexWriterConfig c = new IndexWriterConfig();
    c.setCodec(codecFor(fmt));
    c.setMergePolicy(mp);
    c.setMergeScheduler(new SerialMergeScheduler());
    c.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    c.setRAMBufferSizeMB(256);
    c.setCommitOnClose(false);
    return c;
  }

  private static void add(IndexWriter w, String id, float[] v, VectorSimilarityFunction sim)
      throws Exception {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new KnnFloatVectorField(F, v, sim));
    w.addDocument(d);
  }

  private static String describe(Throwable t) {
    StackTraceElement top = null;
    for (StackTraceElement e : t.getStackTrace()) {
      if (e.getClassName().contains("Lucene104ScalarQuantizedVectorsWriter")) {
        top = e;
        break;
      }
    }
    return t.getClass().getSimpleName() + ": " + t.getMessage() + " @ " + top;
  }

  private static Throwable rootOf(Throwable t) {
    while (t.getCause() != null && t.getCause() != t) {
      t = t.getCause();
    }
    return t;
  }

  private static int numDocs(Directory dir) throws Exception {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      return r.numDocs();
    }
  }

  // --- 1. one flush, {1,0} and {-1,0}, every scalar encoding, flat and HNSW variants ---------
  public void testFlushAntipodalPairAllEncodings() throws Exception {
    for (ScalarEncoding enc : ScalarEncoding.values()) {
      for (boolean hnsw : new boolean[] {false, true}) {
        KnnVectorsFormat fmt =
            hnsw
                ? new Lucene104HnswScalarQuantizedVectorsFormat(enc, 16, 100)
                : new Lucene104ScalarQuantizedVectorsFormat(enc);
        try (Directory dir = new ByteBuffersDirectory()) {
          IndexWriter w = new IndexWriter(dir, iwc(fmt, NoMergePolicy.INSTANCE));
          w.commit(); // empty commit so the dir is openable afterwards
          add(w, "a", new float[] {1f, 0f}, VectorSimilarityFunction.COSINE);
          add(w, "b", new float[] {-1f, 0f}, VectorSimilarityFunction.COSINE);
          Throwable thrown = null;
          try {
            w.commit();
          } catch (Throwable t) {
            thrown = t;
          }
          System.out.println(
              "[flush] enc="
                  + enc
                  + " hnsw="
                  + hnsw
                  + " thrown="
                  + (thrown == null ? "none" : describe(rootOf(thrown)))
                  + " writerOpen="
                  + w.isOpen()
                  + " tragic="
                  + (w.getTragicException() == null
                      ? "null"
                      : rootOf(w.getTragicException()).getMessage()));
          assertNotNull("commit should have thrown (enc=" + enc + ")", thrown);
          assertTrue(rootOf(thrown) instanceof IllegalArgumentException);
          assertEquals("Cannot normalize a zero-length vector", rootOf(thrown).getMessage());
          if (w.isOpen()) {
            w.rollback();
          }
          assertEquals("buffered docs are lost", 0, numDocs(dir));
        }
      }
    }
  }

  // --- 2. realistic dimension: v and -v, 768d -----------------------------------------------
  public void testFlush768dVectorAndItsNegation() throws Exception {
    Random r = new Random(42);
    float[] v = new float[768];
    for (int i = 0; i < v.length; i++) v[i] = (float) r.nextGaussian();
    float[] neg = new float[v.length];
    for (int i = 0; i < v.length; i++) neg[i] = -v[i];
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w =
          new IndexWriter(
              dir, iwc(new Lucene104HnswScalarQuantizedVectorsFormat(), NoMergePolicy.INSTANCE));
      add(w, "a", v, VectorSimilarityFunction.COSINE);
      add(w, "b", neg, VectorSimilarityFunction.COSINE);
      IllegalArgumentException e = expectThrows(IllegalArgumentException.class, w::commit);
      System.out.println("[768d] " + describe(e) + " writerOpen=" + w.isOpen());
      assertEquals("Cannot normalize a zero-length vector", e.getMessage());
      if (w.isOpen()) w.rollback();
    }
  }

  // --- 3. symmetric set of 4 one-hot +/- vectors, plus scaled magnitudes -----------------------
  public void testFlushSignedOneHotSetWithMixedMagnitudes() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w =
          new IndexWriter(
              dir, iwc(new Lucene104HnswScalarQuantizedVectorsFormat(), NoMergePolicy.INSTANCE));
      add(w, "a", new float[] {3f, 0f, 0f}, VectorSimilarityFunction.COSINE);
      add(w, "b", new float[] {-7f, 0f, 0f}, VectorSimilarityFunction.COSINE);
      add(w, "c", new float[] {0f, 0.5f, 0f}, VectorSimilarityFunction.COSINE);
      add(w, "d", new float[] {0f, -2f, 0f}, VectorSimilarityFunction.COSINE);
      IllegalArgumentException e = expectThrows(IllegalArgumentException.class, w::commit);
      System.out.println("[onehot] " + describe(e));
      if (w.isOpen()) w.rollback();
    }
  }

  // --- 4. controls: same data succeeds with the non-quantized default format, and with
  //        DOT_PRODUCT on the quantized format (where a zero centroid is NOT normalized) --------
  public void testControlsSucceed() throws Exception {
    float[][] docs = {{1f, 0f}, {-1f, 0f}};
    Object[][] cases = {
      {new Lucene99HnswVectorsFormat(), VectorSimilarityFunction.COSINE},
      {new Lucene104HnswScalarQuantizedVectorsFormat(), VectorSimilarityFunction.DOT_PRODUCT},
      {new Lucene104ScalarQuantizedVectorsFormat(), VectorSimilarityFunction.DOT_PRODUCT},
      {new Lucene104HnswScalarQuantizedVectorsFormat(), VectorSimilarityFunction.EUCLIDEAN},
    };
    for (Object[] c : cases) {
      KnnVectorsFormat fmt = (KnnVectorsFormat) c[0];
      VectorSimilarityFunction sim = (VectorSimilarityFunction) c[1];
      try (Directory dir = new ByteBuffersDirectory()) {
        try (IndexWriter w = new IndexWriter(dir, iwc(fmt, NoMergePolicy.INSTANCE))) {
          for (int i = 0; i < docs.length; i++) add(w, "d" + i, docs[i], sim);
          w.commit();
        }
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          IndexSearcher s = new IndexSearcher(r);
          float[] q = VectorUtil.l2normalize(new float[] {0.6f, 0.8f});
          TopDocs td = s.search(new KnnFloatVectorQuery(F, q, 2), 2);
          StringBuilder sb = new StringBuilder();
          for (ScoreDoc sd : td.scoreDocs) {
            sb.append(s.storedFields().document(sd.doc).get("id")).append('=').append(sd.score).append(' ');
          }
          System.out.println("[control] fmt=" + fmt.getName() + " sim=" + sim + " hits: " + sb
              + " (float expected: d0=" + sim.compare(q, docs[0]) + " d1=" + sim.compare(q, docs[1]) + ")");
          assertEquals(2, td.scoreDocs.length);
        }
      }
    }
  }

  // --- 5. merge of two 1-doc segments: weighted mean of stored segment centroids (Writer:630) --
  public void testMergeOfAntipodalSegmentsIsTragicAndPersistent() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w =
          new IndexWriter(dir, iwc(new Lucene104HnswScalarQuantizedVectorsFormat(), NoMergePolicy.INSTANCE));
      add(w, "a", new float[] {0.6f, 0.8f}, VectorSimilarityFunction.COSINE);
      w.commit();
      add(w, "b", new float[] {-0.6f, -0.8f}, VectorSimilarityFunction.COSINE);
      w.commit();
      w.close();
      assertEquals(2, numDocs(dir));

      for (int attempt = 1; attempt <= 2; attempt++) {
        LogDocMergePolicy mp = new LogDocMergePolicy();
        mp.setMinMergeDocs(1);
        IndexWriter w2 =
            new IndexWriter(dir, iwc(new Lucene104HnswScalarQuantizedVectorsFormat(), mp));
        Throwable thrown = null;
        try {
          w2.forceMerge(1);
        } catch (Throwable t) {
          thrown = t;
        }
        System.out.println(
            "[merge attempt "
                + attempt
                + "] thrown="
                + (thrown == null ? "none" : thrown.getClass().getSimpleName() + " / root " + describe(rootOf(thrown)))
                + " writerOpen="
                + w2.isOpen()
                + " tragic="
                + (w2.getTragicException() == null ? "null" : rootOf(w2.getTragicException()).getMessage()));
        assertNotNull(thrown);
        assertEquals("Cannot normalize a zero-length vector", rootOf(thrown).getMessage());
        assertFalse("tragic event closes the writer", w2.isOpen());
        assertNotNull(w2.getTragicException());
        // committed data still intact
        assertEquals(2, numDocs(dir));
      }
    }
  }

  // --- 6. same through the "recalculate" merge path (Writer:654), triggered by deletions -----
  public void testMergeAfterDeleteLeavesAntipodalPair() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w =
          new IndexWriter(dir, iwc(new Lucene104HnswScalarQuantizedVectorsFormat(), NoMergePolicy.INSTANCE));
      add(w, "a", new float[] {1f, 0f}, VectorSimilarityFunction.COSINE);
      add(w, "b", new float[] {-1f, 0f}, VectorSimilarityFunction.COSINE);
      add(w, "c", new float[] {0f, 1f}, VectorSimilarityFunction.COSINE);
      w.commit(); // centroid (0,1)/3 -> fine
      w.deleteDocuments(new Term("id", "c"));
      w.commit();
      w.close();
      assertEquals(2, numDocs(dir));

      LogDocMergePolicy mp = new LogDocMergePolicy();
      IndexWriter w2 = new IndexWriter(dir, iwc(new Lucene104HnswScalarQuantizedVectorsFormat(), mp));
      Throwable thrown = null;
      try {
        w2.forceMergeDeletes(true);
      } catch (Throwable t) {
        thrown = t;
      }
      System.out.println(
          "[delete+merge] thrown="
              + (thrown == null ? "none" : describe(rootOf(thrown)))
              + " writerOpen="
              + w2.isOpen());
      assertNotNull(thrown);
      assertEquals("Cannot normalize a zero-length vector", rootOf(thrown).getMessage());
      assertFalse(w2.isOpen());
    }
  }

  // --- 7. the weighted-mean merge path (Writer:630) is only reached when the per-segment reader
  //        unwraps to Lucene104ScalarQuantizedVectorsReader, i.e. the FLAT format. Mirrored
  //        multi-doc segments (seg2 = elementwise negation of seg1) have exactly negated stored
  //        centroids, so the count-weighted mean is exactly zero. ----------------------------------
  public void testMergeOfMirroredMultiDocSegments() throws Exception {
    float[][] seg1 = {{1f, 0f, 0.25f}, {0.8f, 0.6f, -0.1f}, {0.3f, -0.2f, 0.9f}};
    for (boolean hnsw : new boolean[] {false, true}) {
      try (Directory dir = new ByteBuffersDirectory()) {
        IndexWriter w = new IndexWriter(dir, iwc(fmt(hnsw), NoMergePolicy.INSTANCE));
        for (int i = 0; i < seg1.length; i++) add(w, "p" + i, seg1[i], VectorSimilarityFunction.COSINE);
        w.commit();
        for (int i = 0; i < seg1.length; i++) {
          float[] n = new float[seg1[i].length];
          for (int j = 0; j < n.length; j++) n[j] = -seg1[i][j];
          add(w, "n" + i, n, VectorSimilarityFunction.COSINE);
        }
        w.commit();
        w.close();
        LogDocMergePolicy mp = new LogDocMergePolicy();
        mp.setMinMergeDocs(1);
        IndexWriter w2 = new IndexWriter(dir, iwc(fmt(hnsw), mp));
        Throwable thrown = null;
        try {
          w2.forceMerge(1);
        } catch (Throwable t) {
          thrown = t;
        }
        System.out.println(
            "[mirrored 3+3 segs] hnsw=" + hnsw + " thrown=" + (thrown == null ? "none" : describe(rootOf(thrown)))
                + " writerOpen=" + w2.isOpen());
        if (hnsw == false) {
          // flat: stored centroids are reused -> exact cancellation at Writer:630
          assertNotNull(thrown);
          assertEquals("Cannot normalize a zero-length vector", rootOf(thrown).getMessage());
        } else if (w2.isOpen()) {
          w2.close();
        }
      }
    }
  }

  // --- 7b. two 1-doc segments with the FLAT format: Writer:630 path ---------------------------
  public void testMergeOfAntipodalSegmentsFlatFormat() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, iwc(fmt(false), NoMergePolicy.INSTANCE));
      add(w, "a", new float[] {0.6f, 0.8f}, VectorSimilarityFunction.COSINE);
      w.commit();
      add(w, "b", new float[] {-0.6f, -0.8f}, VectorSimilarityFunction.COSINE);
      w.commit();
      w.close();
      LogDocMergePolicy mp = new LogDocMergePolicy();
      mp.setMinMergeDocs(1);
      IndexWriter w2 = new IndexWriter(dir, iwc(fmt(false), mp));
      Throwable thrown = expectThrows(Throwable.class, () -> w2.forceMerge(1));
      System.out.println("[flat 1+1 merge] " + describe(rootOf(thrown)) + " writerOpen=" + w2.isOpen());
      assertEquals("Cannot normalize a zero-length vector", rootOf(thrown).getMessage());
      assertFalse(w2.isOpen());
    }
  }

  private static KnnVectorsFormat fmt(boolean hnsw) {
    return hnsw ? new Lucene104HnswScalarQuantizedVectorsFormat() : new Lucene104ScalarQuantizedVectorsFormat();
  }

  // --- 8. near-antipodal (not exact) pair is fine: shows the trigger is exact cancellation ---
  public void testNearAntipodalIsFine() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, iwc(new Lucene104HnswScalarQuantizedVectorsFormat(), NoMergePolicy.INSTANCE))) {
        add(w, "a", new float[] {1f, 0f}, VectorSimilarityFunction.COSINE);
        add(w, "b", new float[] {-1f, 1e-6f}, VectorSimilarityFunction.COSINE);
        w.commit();
      }
      assertEquals(2, numDocs(dir));
      System.out.println("[near-antipodal] {1,0},{-1,1e-6}: commit OK");
    }
  }
}
