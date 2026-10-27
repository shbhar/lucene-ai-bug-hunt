import org.apache.lucene.search.FieldStats;
import org.apache.lucene.search.TermStats;
import org.apache.lucene.search.similarities.Similarity;

/**
 * Deliberately "wrong" similarity: the per-term weight GROWS with docFreq (common terms score
 * higher than rare terms), i.e. the opposite of IDF. Everything else is chosen to satisfy every
 * other property BaseSimilarityTestCase claims to check:
 *
 * <ul>
 *   <li>finite, non-negative scores for boost in [0, Integer.MAX_VALUE], freq in (0, 2^31)
 *   <li>score non-decreasing in freq (log1p is semi-monotonic; RN division/multiplication by a
 *       non-negative constant and the double-to-float cast are monotone)
 *   <li>score non-increasing in unsigned norm
 *   <li>explanation value == score (default SimScorer.explain)
 * </ul>
 */
public final class AntiIdfSimilarity extends Similarity {

  @Override
  public SimScorer scorer(float boost, FieldStats fieldStats, TermStats... termStats) {
    long df = 0;
    for (TermStats ts : termStats) {
      df += ts.docFreq();
    }
    // weight increases with docFreq: rarer term => smaller weight
    final double weight = boost * Math.log1p(df);
    return new SimScorer() {
      @Override
      public float score(float freq, long norm) {
        double unsignedNorm = norm >= 0 ? (double) norm : (double) norm + 0x1p64;
        return (float) (weight * Math.log1p(freq) / (1.0 + unsignedNorm));
      }
    };
  }

  @Override
  public String toString() {
    return "AntiIdfSimilarity";
  }
}
