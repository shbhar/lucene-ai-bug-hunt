import java.util.Random;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.tests.search.similarities.BaseSimilarityTestCase;

/**
 * Runs the stock BaseSimilarityTestCase conformance suite against AntiIdfSimilarity. The suite's
 * javadoc says testRandomScoring checks "scores don't decrease as terms get rarer"; a correct
 * framework must therefore FAIL this suite. If it passes, the check is not effective.
 */
public class AntiIdfSimilarityConformanceTest extends BaseSimilarityTestCase {
  @Override
  protected Similarity getSimilarity(Random random) {
    return new AntiIdfSimilarity();
  }
}
