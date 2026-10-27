import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloat16VectorField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Float16VectorValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.VectorUtil;
import org.apache.lucene.util.quantization.OptimizedScalarQuantizer;
import org.apache.lucene.util.quantization.QuantizedByteVectorValues;
import org.apache.lucene.util.quantization.QuantizedByteVectorValues.ScalarEncoding;
import org.apache.lucene.util.quantization.QuantizedVectorsReader;

/** Extra checks for F4 (main only: uses float16 and QuantizedVectorsReader APIs). */
public class TestVerifyNibbleOddDimExtra extends LuceneTestCase {

  /**
   * The quantized bytes on disk are fine for odd dims: decoding them with a buffer of
   * discretize(dim, 2) slots and truncating to dim reproduces the original vectors. So the defect
   * is confined to the reader's buffer size, not the written data.
   */
  public void testOnDiskNibbleDataDecodesWithCorrectlySizedBuffer() throws Exception {
    float worst = 0;
    for (int dim = 1; dim <= 41; dim += 2) {
      try (Directory dir = new ByteBuffersDirectory()) {
        List<float[]> docs =
            TestVerifyNibbleOddDim.buildIndex(
                dir, ScalarEncoding.PACKED_NIBBLE, dim, 5, VectorSimilarityFunction.EUCLIDEAN);
        DropRaw.drop(dir);
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          CodecReader cr = (CodecReader) r.leaves().get(0).reader();
          KnnVectorsReader kvr = cr.getVectorReader().unwrapReaderForField(TestVerifyNibbleOddDim.F);
          QuantizedByteVectorValues q =
              (QuantizedByteVectorValues)
                  ((QuantizedVectorsReader) kvr).getQuantizedVectorValues(TestVerifyNibbleOddDim.F);
          float[] centroid = q.getCentroid();
          for (int ord = 0; ord < q.size(); ord++) {
            byte[] packed = q.vectorValue(ord);
            assertEquals((dim + 1) / 2, packed.length);
            byte[] unpacked = new byte[OptimizedScalarQuantizer.discretize(dim, 2)];
            VectorUtil.int4Unpack(packed, unpacked);
            byte[] codes = Arrays.copyOf(unpacked, dim);
            OptimizedScalarQuantizer.QuantizationResult c = q.getCorrectiveTerms(ord);
            float[] out = new float[dim];
            OptimizedScalarQuantizer.deQuantize(
                codes, out, (byte) 4, c.lowerInterval(), c.upperInterval(), centroid);
            float[] exp = docs.get(q.ordToDoc(ord));
            for (int i = 0; i < dim; i++) worst = Math.max(worst, Math.abs(exp[i] - out[i]));
          }
        }
      }
    }
    System.out.println("[on-disk decode, odd dims 1..41] worst abs err = " + worst);
    assertTrue("worst=" + worst, worst < 0.25f);
  }

  /** fp16 twin: OffHeapScalarQuantizedFloat16VectorValues has the same buffer size. */
  public void testFloat16OddDim3NibbleDroppedRawVectorValue() throws Exception {
    int dim = 3;
    try (Directory dir = new ByteBuffersDirectory()) {
      List<short[]> docs = new ArrayList<>();
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestVerifyNibbleOddDim.codec(ScalarEncoding.PACKED_NIBBLE))
              .setUseCompoundFile(false)
              .setMergePolicy(NoMergePolicy.INSTANCE)
              .setMaxBufferedDocs(10)
              .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int d = 0; d < 4; d++) {
          float[] f = TestVerifyNibbleOddDim.vec(dim, d);
          short[] h = new short[dim];
          for (int i = 0; i < dim; i++) h[i] = Float.floatToFloat16(f[i]);
          docs.add(h);
          Document doc = new Document();
          doc.add(new KnnFloat16VectorField("v", h, VectorSimilarityFunction.EUCLIDEAN));
          w.addDocument(doc);
        }
      }
      DropRaw.drop(dir);
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Float16VectorValues v = r.leaves().get(0).reader().getFloat16VectorValues("v");
        try {
          short[] got = v.vectorValue(0);
          System.out.println("[fp16 dim3 nibble dropped] ok: " + Arrays.toString(got));
        } catch (RuntimeException e) {
          System.out.println("[fp16 dim3 nibble dropped] vectorValue threw: " + e);
          StackTraceElement[] st = e.getStackTrace();
          for (int i = 0; i < Math.min(4, st.length); i++) System.out.println("    at " + st[i]);
          throw e;
        }
      }
    }
  }
}
