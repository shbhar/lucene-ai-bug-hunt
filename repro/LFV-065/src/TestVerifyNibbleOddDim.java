import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.lucene104.Lucene104HnswScalarQuantizedVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.KnnVectorValues;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RescoreTopNQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.quantization.QuantizedByteVectorValues.ScalarEncoding;

/**
 * Independent verification of candidate F4: Lucene104 scalar-quantized format, PACKED_NIBBLE, odd
 * dimension, full-precision vectors dropped -> getFloatVectorValues().vectorValue(ord) fails.
 * Deterministic: fixed vectors, no randomness used for inputs.
 */
public class TestVerifyNibbleOddDim extends LuceneTestCase {

  static final String F = "v";

  static float[] vec(int dim, int doc) {
    float[] v = new float[dim];
    for (int i = 0; i < dim; i++) {
      // deterministic, non-degenerate values in [-1, 1]
      v[i] = (float) Math.sin(1.7 * (doc + 1) + 0.9 * i);
    }
    return v;
  }

  static Codec codec(ScalarEncoding enc) {
    return TestUtil.alwaysKnnVectorsFormat(
        new Lucene104HnswScalarQuantizedVectorsFormat(enc, 16, 100));
  }

  static List<float[]> buildIndex(
      Directory dir, ScalarEncoding enc, int dim, int n, VectorSimilarityFunction sim)
      throws IOException {
    List<float[]> docs = new ArrayList<>();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(codec(enc))
            .setUseCompoundFile(false)
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setMaxBufferedDocs(n + 1)
            .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int d = 0; d < n; d++) {
        float[] v = vec(dim, d);
        docs.add(v);
        Document doc = new Document();
        doc.add(new KnnFloatVectorField(F, v, sim));
        w.addDocument(doc);
      }
    }
    return docs;
  }

  /** Reads every vector via getFloatVectorValues().vectorValue(ord); returns max abs error. */
  static float readAll(Directory dir, List<float[]> docs) throws IOException {
    float maxErr = 0;
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals(1, r.leaves().size());
      LeafReader leaf = r.leaves().get(0).reader();
      FloatVectorValues fvv = leaf.getFloatVectorValues(F);
      assertEquals(docs.size(), fvv.size());
      KnnVectorValues.DocIndexIterator it = fvv.iterator();
      for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
        float[] got = fvv.vectorValue(it.index());
        float[] exp = docs.get(doc);
        assertEquals(exp.length, got.length);
        for (int i = 0; i < exp.length; i++) {
          maxErr = Math.max(maxErr, Math.abs(exp[i] - got[i]));
        }
      }
    }
    return maxErr;
  }

  // ---- 1. The claimed bug: dim 3, PACKED_NIBBLE, raw vectors dropped -----------------------

  public void testOddDim3NibbleDroppedRawVectorValue() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      List<float[]> docs = buildIndex(dir, ScalarEncoding.PACKED_NIBBLE, 3, 4, VectorSimilarityFunction.EUCLIDEAN);
      // sanity: with raw vectors present, values are exact
      assertEquals(0f, readAll(dir, docs), 0f);
      assertTrue(DropRaw.drop(dir) >= 2);
      // expected per the class javadoc: dequantized values (4-bit => small error), no exception
      float err;
      try {
        err = readAll(dir, docs);
      } catch (RuntimeException e) {
        System.out.println("[dim3 nibble dropped] vectorValue threw: " + e);
        StackTraceElement[] st = e.getStackTrace();
        for (int i = 0; i < Math.min(6, st.length); i++) System.out.println("    at " + st[i]);
        throw e;
      }
      System.out.println("[dim3 nibble dropped] max abs err = " + err);
      assertTrue("err=" + err, err < 0.2f);
    }
  }

  // ---- 2. Control: same thing with an even dimension works -----------------------------------

  public void testEvenDim4NibbleDroppedRawVectorValueControl() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      List<float[]> docs = buildIndex(dir, ScalarEncoding.PACKED_NIBBLE, 4, 4, VectorSimilarityFunction.EUCLIDEAN);
      DropRaw.drop(dir);
      float err = readAll(dir, docs);
      System.out.println("[dim4 nibble dropped] max abs err = " + err);
      assertTrue("err=" + err, err < 0.2f);
    }
  }

  // ---- 3. Control: odd dim, PACKED_NIBBLE, raw vectors dropped, plain kNN search works -------

  public void testOddDimNibbleDroppedKnnSearchStillWorks() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      List<float[]> docs = buildIndex(dir, ScalarEncoding.PACKED_NIBBLE, 3, 4, VectorSimilarityFunction.EUCLIDEAN);
      DropRaw.drop(dir);
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        TopDocs td = s.search(new KnnFloatVectorQuery(F, docs.get(2), 4), 4);
        System.out.println("[dim3 nibble dropped] plain kNN hits = " + td.scoreDocs.length + " top doc = " + td.scoreDocs[0].doc);
        assertEquals(4, td.scoreDocs.length);
      }
    }
  }

  // ---- 4. Public query API path: full-precision rescoring over a dropped-raw index -----------

  public void testOddDimNibbleDroppedFullPrecisionRescoreQuery() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      List<float[]> docs = buildIndex(dir, ScalarEncoding.PACKED_NIBBLE, 3, 4, VectorSimilarityFunction.EUCLIDEAN);
      DropRaw.drop(dir);
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        float[] q = docs.get(2);
        Query rq = RescoreTopNQuery.createFullPrecisionRescorerQuery(new KnnFloatVectorQuery(F, q, 4), q, F, 2);
        TopDocs td;
        try {
          td = s.search(rq, 2);
        } catch (RuntimeException e) {
          System.out.println("[dim3 nibble dropped] RescoreTopNQuery threw: " + e);
          throw e;
        }
        assertEquals(2, td.scoreDocs.length);
      }
    }
  }

  // ---- 5. Characterization over all encodings x dims 1..40 -----------------------------------

  public void testMatrixAllEncodingsDims1to40() throws Exception {
    TreeSet<String> failures = new TreeSet<>();
    TreeSet<String> expected = new TreeSet<>();
    String firstMsg = null;
    int cases = 0;
    for (ScalarEncoding enc : ScalarEncoding.values()) {
      for (int dim = 1; dim <= 40; dim++) {
        cases++;
        if (enc == ScalarEncoding.PACKED_NIBBLE && dim % 2 == 1) {
          expected.add(enc + "/" + dim);
        }
        try (Directory dir = new ByteBuffersDirectory()) {
          List<float[]> docs = buildIndex(dir, enc, dim, 5, VectorSimilarityFunction.EUCLIDEAN);
          DropRaw.drop(dir);
          float err = readAll(dir, docs);
          // loose accuracy sanity per bit width (values in [-1,1])
          float tol = switch (enc) {
            case UNSIGNED_BYTE, SEVEN_BIT -> 0.05f;
            case PACKED_NIBBLE -> 0.25f;
            case DIBIT_QUERY_NIBBLE, SINGLE_BIT_QUERY_NIBBLE -> 2.1f;
          };
          if (!(err <= tol)) {
            failures.add(enc + "/" + dim + "(err=" + err + ")");
          }
        } catch (RuntimeException e) {
          failures.add(enc + "/" + dim);
          if (firstMsg == null) firstMsg = enc + "/" + dim + " -> " + e;
        }
      }
    }
    System.out.println("[matrix] cases=" + cases + " failures=" + failures.size() + " first=" + firstMsg);
    System.out.println("[matrix] failures=" + failures);
    assertEquals("failure set must be exactly PACKED_NIBBLE x odd dims", expected, failures);
  }
}
