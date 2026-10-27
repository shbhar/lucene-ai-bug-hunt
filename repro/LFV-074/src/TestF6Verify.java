import static org.apache.lucene.index.VectorSimilarityFunction.EUCLIDEAN;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104Codec;
import org.apache.lucene.codecs.lucene104.Lucene104HnswScalarQuantizedVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104ScalarQuantizedVectorsFormat;
import org.apache.lucene.codecs.lucene104.OffHeapScalarQuantizedVectorValues;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.quantization.OptimizedScalarQuantizer;
import org.apache.lucene.util.quantization.OptimizedScalarQuantizer.QuantizationResult;
import org.apache.lucene.util.quantization.QuantizedByteVectorValues;
import org.apache.lucene.util.quantization.QuantizedByteVectorValues.ScalarEncoding;
import org.apache.lucene.util.quantization.QuantizedVectorsReader;

/**
 * Independent verification of candidate F6: OptimizedScalarQuantizer emits out-of-range codes and
 * a wrong/overflowed quantizedComponentSum when the interval width (b-a) makes step=(b-a)/n zero or
 * subnormal. Every input below is a centered vector (centroid all zeros) so the arithmetic is fully
 * determined; none of the tests use randomness.
 */
public class TestF6Verify extends LuceneTestCase {
  static final float M = Float.MIN_VALUE; // 2^-149, the smallest positive subnormal

  /** Stored code as the scorer interprets it: unsigned for 8 bits, signed byte otherwise. */
  private static int code(byte b, int bits) {
    return bits == 8 ? (b & 0xFF) : b;
  }

  private static String quantize(float[] centered, int bits, List<String> bad) {
    byte[] dest = new byte[centered.length];
    QuantizationResult r =
        new OptimizedScalarQuantizer(EUCLIDEAN)
            .scalarQuantize(centered.clone(), dest, (byte) bits, new float[centered.length]);
    int max = (1 << bits) - 1;
    int storedSum = 0;
    StringBuilder codes = new StringBuilder();
    boolean ok = true;
    for (byte b : dest) {
      int c = code(b, bits);
      codes.append(c).append(' ');
      storedSum += c;
      if (c < 0 || c > max) ok = false;
    }
    if (r.quantizedComponentSum() != storedSum) ok = false;
    if (r.quantizedComponentSum() < 0 || r.quantizedComponentSum() > centered.length * max) {
      ok = false;
    }
    float stepQ = (r.upperInterval() - r.lowerInterval()) / max; // what scalarQuantize used
    String line =
        String.format(
            "bits=%d interval=[%s,%s] step=%s codes=[%s] sumOfStoredCodes=%d quantizedComponentSum=%d %s",
            bits,
            r.lowerInterval(),
            r.upperInterval(),
            stepQ,
            codes.toString().trim(),
            storedSum,
            r.quantizedComponentSum(),
            ok ? "OK" : "VIOLATION");
    if (!ok) bad.add(line);
    return line;
  }

  /** step=(b-a)/n underflows to 0: x=b gives (b-a)/0=+Inf, Math.round(+Inf)=Integer.MAX_VALUE. */
  public void testZeroStepCodeIsIntegerMaxValue() {
    List<String> bad = new ArrayList<>();
    for (int bits : new int[] {1, 2, 4, 7, 8}) {
      System.out.println("[zeroStep {MIN_VALUE,0}] " + quantize(new float[] {M, 0f}, bits, bad));
    }
    assertTrue("contract violations:\n" + String.join("\n", bad), bad.isEmpty());
  }

  /** Non-zero subnormal step rounded down: x=b maps to a code above n. */
  public void testSubnormalNonZeroStepCodeAboveMax() {
    List<String> bad = new ArrayList<>();
    System.out.println("[subnormalStep {22*MIN,0}] " + quantize(new float[] {22 * M, 0f}, 4, bad));
    System.out.println(
        "[subnormalStep {189*MIN,0}] " + quantize(new float[] {189 * M, 0f}, 7, bad));
    System.out.println(
        "[subnormalStep {382*MIN,0}] " + quantize(new float[] {382 * M, 0f}, 8, bad));
    assertTrue("contract violations:\n" + String.join("\n", bad), bad.isEmpty());
  }

  /** Two +Inf assignments: Integer.MAX_VALUE + Integer.MAX_VALUE wraps to -2. */
  public void testComponentSumOverflowsNegative() {
    float[] v = {M, M, 0f};
    byte[] dest = new byte[3];
    QuantizationResult r =
        new OptimizedScalarQuantizer(EUCLIDEAN).scalarQuantize(v, dest, (byte) 4, new float[3]);
    System.out.println(
        "[overflow {MIN,MIN,0} 4 bits] codes="
            + dest[0]
            + ","
            + dest[1]
            + ","
            + dest[2]
            + " quantizedComponentSum="
            + r.quantizedComponentSum());
    // Lucene's own TestOptimizedScalarQuantizer.assertValidResults requires sum >= 0.
    assertTrue("sum=" + r.quantizedComponentSum(), r.quantizedComponentSum() >= 0);
  }

  /** The merge-time multiScalarQuantize (OptimizedScalarQuantizer.java:159-167) has the same bug. */
  public void testMultiScalarQuantizeSameDefect() {
    byte[][] dest = new byte[2][2];
    QuantizationResult[] r =
        new OptimizedScalarQuantizer(EUCLIDEAN)
            .multiScalarQuantize(new float[] {M, 0f}, dest, new byte[] {1, 4}, new float[2]);
    System.out.println(
        "[multi {MIN,0} bits {1,4}] 1-bit codes="
            + dest[0][0]
            + ","
            + dest[0][1]
            + " sum="
            + r[0].quantizedComponentSum()
            + " | 4-bit codes="
            + dest[1][0]
            + ","
            + dest[1][1]
            + " sum="
            + r[1].quantizedComponentSum());
    assertTrue("4-bit code=" + dest[1][0], dest[1][0] >= 0 && dest[1][0] <= 15);
  }

  private static void printFrames(String tag, Throwable t) {
    Throwable root = t;
    while (root.getCause() != null) root = root.getCause();
    StackTraceElement[] st = root.getStackTrace();
    for (int i = 0; i < Math.min(10, st.length); i++) {
      System.out.println(tag + "   at " + st[i]);
    }
  }

  private static IndexWriterConfig iwc(KnnVectorsFormat format) {
    IndexWriterConfig c = new IndexWriterConfig();
    c.setCodec(
        new Lucene104Codec() {
          @Override
          public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
            return format;
          }
        });
    c.setMergeScheduler(new SerialMergeScheduler());
    return c;
  }

  private static void add(IndexWriter w, float[] v) throws Exception {
    Document d = new Document();
    d.add(new KnnFloatVectorField("f", v, EUCLIDEAN));
    w.addDocument(d);
  }

  /**
   * End to end through IndexWriter (PACKED_NIBBLE). Segment = {0,0},{0,MIN},{0,0}: float centroid =
   * MIN/3 -> 0. Doc 1 gets 4-bit codes {0, -1}; packNibbles sign-extends the -1 of the low slot into
   * the high slot, so the stored byte is 0xFF and dimension 0 reads back as 15 instead of 0.
   */
  public void testEndToEndPackedNibbleStoredCodes() throws Exception {
    float[][] docs = {{0f, 0f}, {0f, M}, {0f, 0f}};
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(
                dir, iwc(new Lucene104ScalarQuantizedVectorsFormat(ScalarEncoding.PACKED_NIBBLE)))) {
      for (float[] v : docs) add(w, v);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        LeafReader leaf = r.leaves().get(0).reader();
        SegmentReader sr = (SegmentReader) FilterLeafReader.unwrap(leaf);
        QuantizedVectorsReader qr =
            (QuantizedVectorsReader) sr.getVectorReader().unwrapReaderForField("f");
        QuantizedByteVectorValues qv = (QuantizedByteVectorValues) qr.getQuantizedVectorValues("f");
        float[] c = qv.getCentroid();
        System.out.println("[e2e PACKED_NIBBLE] centroid=" + c[0] + "," + c[1]);
        List<String> bad = new ArrayList<>();
        for (int ord = 0; ord < qv.size(); ord++) {
          byte[] packed = qv.vectorValue(ord).clone();
          byte[] unpacked = new byte[2];
          OffHeapScalarQuantizedVectorValues.unpackNibbles(packed, unpacked);
          QuantizationResult ct = qv.getCorrectiveTerms(ord);
          String line =
              String.format(
                  "[e2e PACKED_NIBBLE] ord=%d packedByte=0x%02X unpacked=[%d,%d] interval=[%s,%s]"
                      + " quantizedComponentSum=%d",
                  ord,
                  packed[0] & 0xFF,
                  unpacked[0],
                  unpacked[1],
                  ct.lowerInterval(),
                  ct.upperInterval(),
                  ct.quantizedComponentSum());
          System.out.println(line);
          // The doc's dimension 0 is exactly the interval's lower end (0), so its code must be 0.
          if (unpacked[0] != 0) bad.add(line + "  <- dim0 corrupted (expected 0)");
          if (ct.quantizedComponentSum() > 2 * 15) bad.add(line + "  <- sum out of range");
        }

        // Score impact: compare quantized scores with exact float scores.
        IndexSearcher s = new IndexSearcher(r);
        for (float[] q : new float[][] {{0.5f, 0.25f}, {0f, M}, {M, 0f}}) {
          TopDocs td = s.search(new KnnFloatVectorQuery("f", q, 3), 3);
          for (ScoreDoc sd : td.scoreDocs) {
            float exact = EUCLIDEAN.compare(q, docs[sd.doc]);
            System.out.printf(
                "[e2e PACKED_NIBBLE score] q=[%s,%s] doc=%d quantized=%s exact=%s diff=%s%n",
                q[0], q[1], sd.doc, sd.score, exact, sd.score - exact);
            assertEquals(exact, sd.score, 1e-6f);
          }
        }
        assertTrue("stored-code violations:\n" + String.join("\n", bad), bad.isEmpty());
      }
    }
  }

  /**
   * Asymmetric encoding: the 4-bit query quantization of {0,MIN} (centroid 0) yields code -1, which
   * OptimizedScalarQuantizer.transposeHalfByte asserts against. Under -ea the search throws
   * AssertionError; without -ea it returns normally.
   */
  public void testAsymmetricQueryHitsTransposeHalfByteAssert() throws Exception {
    for (ScalarEncoding enc :
        new ScalarEncoding[] {
          ScalarEncoding.SINGLE_BIT_QUERY_NIBBLE, ScalarEncoding.DIBIT_QUERY_NIBBLE
        }) {
      float[][] docs = {{0f, 0f}, {0f, 1f}, {0f, -1f}};
      try (Directory dir = new ByteBuffersDirectory();
          IndexWriter w =
              new IndexWriter(dir, iwc(new Lucene104ScalarQuantizedVectorsFormat(enc)))) {
        for (float[] v : docs) add(w, v);
        w.commit();
        try (DirectoryReader r = DirectoryReader.open(w)) {
          IndexSearcher s = new IndexSearcher(r);
          float[] q = {M, 0f}; // index 0: transposeHalfByte only asserts q[i] for i % 8 == 0
          Throwable thrown = null;
          TopDocs td = null;
          try {
            td = s.search(new KnnFloatVectorQuery("f", q, 3), 3);
          } catch (AssertionError e) {
            thrown = e;
          }
          if (thrown != null) {
            StackTraceElement top = thrown.getStackTrace()[0];
            System.out.println("[asym " + enc + "] search threw " + thrown + " at " + top);
            printFrames("[asym " + enc + "]", thrown);
          } else {
            StringBuilder sb = new StringBuilder();
            for (ScoreDoc sd : td.scoreDocs) {
              sb.append(" doc=")
                  .append(sd.doc)
                  .append(" q=")
                  .append(sd.score)
                  .append(" exact=")
                  .append(EUCLIDEAN.compare(q, docs[sd.doc]));
            }
            System.out.println("[asym " + enc + "] search returned normally:" + sb);
          }
          assertNull("search threw " + thrown, thrown);
        }
      }
    }
  }

  /**
   * Asymmetric HNSW merge: multiScalarQuantize writes the 4-bit query-side record of every merged
   * doc through transposeHalfByte. Two segments that each hold {0,MIN} and zeros.
   */
  public void testAsymmetricHnswMerge() throws Exception {
    Throwable thrown = null;
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(
                dir,
                iwc(
                    new Lucene104HnswScalarQuantizedVectorsFormat(
                        ScalarEncoding.SINGLE_BIT_QUERY_NIBBLE, 16, 100)))) {
      for (int seg = 0; seg < 2; seg++) {
        // +v/-v pairs sum to exactly 0 in float, so the centroid stays {M/n -> 0, 0} and the
        // {M,0} doc stays subnormal after centering; 1000 pairs so that a graph is built.
        for (int i = 0; i < 1000; i++) {
          float x = (i + 1) / 1000f, y = ((i * 7) % 13) / 13f;
          add(w, new float[] {x, y});
          add(w, new float[] {-x, -y});
        }
        add(w, new float[] {M, 0f}); // index 0, see transposeHalfByte's assert placement
        w.commit();
      }
      try {
        w.forceMerge(1);
      } catch (Throwable t) {
        thrown = t;
      }
      if (thrown != null) {
        Throwable root = thrown;
        while (root.getCause() != null) root = root.getCause();
        System.out.println(
            "[asym HNSW merge] forceMerge threw "
                + thrown
                + " root="
                + root
                + " at "
                + root.getStackTrace()[0]);
        printFrames("[asym HNSW merge]", thrown);
      } else {
        try (DirectoryReader r = DirectoryReader.open(w)) {
          System.out.println(
              "[asym HNSW merge] forceMerge completed normally; segments=" + r.leaves().size());
        }
      }
    }
    assertNull("forceMerge threw " + thrown, thrown);
  }

  /** Sanity: the reached domain. COSINE, a unit vector differing from the centroid by MIN_VALUE. */
  public void testCosineReachability() {
    // Segment {1,0} and {1,MIN}: both already unit length; centroid = normalize({1, MIN/2 -> 0}).
    float[] v = {1f, M};
    float[] centroid = {1f, 0f};
    byte[] dest = new byte[2];
    QuantizationResult r =
        new OptimizedScalarQuantizer(VectorSimilarityFunction.COSINE)
            .scalarQuantize(v, dest, (byte) 4, centroid);
    System.out.println(
        "[cosine {1,MIN} vs centroid {1,0}] 4-bit codes="
            + dest[0]
            + ","
            + dest[1]
            + " sum="
            + r.quantizedComponentSum());
    assertTrue("code=" + dest[1], dest[1] >= 0 && dest[1] <= 15);
  }
  /** End to end, COSINE: unit vectors {1,0} and {1,MIN}; centroid normalize({1, MIN/2 -> 0}). */
  public void testEndToEndCosinePackedNibble() throws Exception {
    float[][] docs = {{1f, 0f}, {1f, M}};
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(
                dir, iwc(new Lucene104ScalarQuantizedVectorsFormat(ScalarEncoding.PACKED_NIBBLE)))) {
      for (float[] v : docs) {
        Document d = new Document();
        d.add(new KnnFloatVectorField("f", v, VectorSimilarityFunction.COSINE));
        w.addDocument(d);
      }
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        SegmentReader sr = (SegmentReader) FilterLeafReader.unwrap(r.leaves().get(0).reader());
        QuantizedByteVectorValues qv =
            (QuantizedByteVectorValues)
                ((QuantizedVectorsReader) sr.getVectorReader().unwrapReaderForField("f"))
                    .getQuantizedVectorValues("f");
        float[] c = qv.getCentroid();
        byte[] packed = qv.vectorValue(1).clone();
        QuantizationResult ct = qv.getCorrectiveTerms(1);
        System.out.printf(
            "[e2e COSINE] centroid=[%s,%s] ord=1 packedByte=0x%02X (hi nibble=dim0=%d, lo=dim1=%d)"
                + " quantizedComponentSum=%d%n",
            c[0], c[1], packed[0] & 0xFF, (packed[0] >> 4) & 0xF, packed[0] & 0xF,
            ct.quantizedComponentSum());
        IndexSearcher s = new IndexSearcher(r);
        float[] q = {0.6f, 0.8f};
        for (ScoreDoc sd : s.search(new KnnFloatVectorQuery("f", q, 2), 2).scoreDocs) {
          float exact = VectorSimilarityFunction.COSINE.compare(q, docs[sd.doc]);
          System.out.printf(
              "[e2e COSINE score] doc=%d quantized=%s exact=%s%n", sd.doc, sd.score, exact);
          assertEquals(exact, sd.score, 1e-6f);
        }
        assertEquals("dim0 code of doc 1 must be 0", 0, (packed[0] >> 4) & 0xF);
      }
    }
  }
}
