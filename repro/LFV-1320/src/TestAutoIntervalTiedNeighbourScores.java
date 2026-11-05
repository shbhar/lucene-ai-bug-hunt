import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.quantization.ScalarQuantizer;

public class TestAutoIntervalTiedNeighbourScores extends LuceneTestCase {

  public void testIdenticalVectorsDoNotResetQuantiles() throws Exception {
    Random r = new Random(0);
    List<float[]> data = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      float[] v = new float[8];
      for (int d = 0; d < 8; d++) {
        v[d] = r.nextFloat() * 2f - 1f;
      }
      data.add(v);
    }
    ScalarQuantizer expected =
        ScalarQuantizer.fromVectorsAutoInterval(
            FloatVectorValues.fromFloats(data, 8), VectorSimilarityFunction.EUCLIDEAN, 20, (byte) 4);
    for (int i = 0; i < 11; i++) {
      float[] c = new float[8];
      Arrays.fill(c, 10f);
      data.add(c);
    }
    ScalarQuantizer actual =
        ScalarQuantizer.fromVectorsAutoInterval(
            FloatVectorValues.fromFloats(data, 8), VectorSimilarityFunction.EUCLIDEAN, 31, (byte) 4);
    // expected: same pair (-0.96047044, 0.957112); actual: grid's first pair (-0.9538363, 0.9618902)
    assertEquals(expected.toString(), actual.toString());
  }
}
