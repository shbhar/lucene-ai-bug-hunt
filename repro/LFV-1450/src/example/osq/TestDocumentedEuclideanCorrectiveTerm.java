package example.osq;

import org.apache.lucene.codecs.hnsw.DefaultFlatVectorScorer;
import org.apache.lucene.codecs.lucene104.Lucene104ScalarQuantizedVectorScorer;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.quantization.OptimizedScalarQuantizer;
import org.apache.lucene.util.quantization.OptimizedScalarQuantizer.QuantizationResult;
import org.apache.lucene.util.quantization.QuantizedByteVectorValues;

public class TestDocumentedEuclideanCorrectiveTerm extends LuceneTestCase {

  public void testDocumentedL2NormCorrectiveTerm() throws Exception {
    float[] centroid = {1, 1};
    float[] doc = {4, 5};
    OptimizedScalarQuantizer quantizer =
        new OptimizedScalarQuantizer(VectorSimilarityFunction.EUCLIDEAN);
    byte[] quantized = new byte[2];
    QuantizationResult r = quantizer.scalarQuantize(doc.clone(), quantized, (byte) 8, centroid);
    QuantizationResult documented =
        new QuantizationResult(
            r.lowerInterval(), r.upperInterval(), 5f, r.quantizedComponentSum());
    QuantizedByteVectorValues values =
        new QuantizedByteVectorValues() {
          @Override
          public QuantizationResult getCorrectiveTerms(int ord) {
            return documented;
          }

          @Override
          public OptimizedScalarQuantizer getQuantizer() {
            return quantizer;
          }

          @Override
          public ScalarEncoding getScalarEncoding() {
            return ScalarEncoding.UNSIGNED_BYTE;
          }

          @Override
          public float[] getCentroid() {
            return centroid;
          }

          @Override
          public float getCentroidDP() {
            return 2f;
          }

          @Override
          public byte[] vectorValue(int ord) {
            return quantized;
          }

          @Override
          public int dimension() {
            return 2;
          }

          @Override
          public int size() {
            return 1;
          }

          @Override
          public QuantizedByteVectorValues copy() {
            return this;
          }
        };
    float score =
        new Lucene104ScalarQuantizedVectorScorer(DefaultFlatVectorScorer.INSTANCE)
            .getRandomVectorScorer(VectorSimilarityFunction.EUCLIDEAN, values, new float[] {7, 9})
            .score(0);
    // third term = documented l2norm ||(3, 4)|| = 5; expected 1/(1+25) = 0.0385, actual 1/6 = 0.1667
    assertEquals(1f / 26f, score, 1e-3f);
  }
}
