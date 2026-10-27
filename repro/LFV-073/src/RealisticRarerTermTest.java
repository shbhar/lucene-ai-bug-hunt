import org.apache.lucene.search.FieldStats;
import org.apache.lucene.search.TermStats;
import org.apache.lucene.search.similarities.AfterEffectL;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BasicModelG;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.DFRSimilarity;
import org.apache.lucene.search.similarities.IndriDirichletSimilarity;
import org.apache.lucene.search.similarities.NormalizationH1;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.SmallFloat;

/**
 * Not a bug repro: measures whether the property the (dead) check documents -- "scores don't
 * decrease as terms get rarer", using the framework's own prevTerm = (df-1, ttf-1) -- holds for
 * real similarities on ordinary, non-degenerate statistics (default parameters, integer freq).
 * This tells us what a one-word fix of BaseSimilarityTestCase would surface.
 */
public class RealisticRarerTermTest extends LuceneTestCase {

  // 1000 docs, avg field length 100
  static final FieldStats CORPUS = new FieldStats("f", 1000, 1000, 100_000, 50_000);
  // a common term: in 500 docs, 1000 occurrences; "rarer" neighbour exactly as the framework builds
  static final TermStats COMMON = new TermStats(new BytesRef("t"), 500, 1000);
  static final TermStats RARER = new TermStats(new BytesRef("t"), 499, 999);

  private static float[] pair(Similarity sim, float freq, int docLen) {
    long norm = SmallFloat.intToByte4(docLen) & 0xFF;
    float common = sim.scorer(1f, CORPUS, COMMON).score(freq, norm);
    float rarer = sim.scorer(1f, CORPUS, RARER).score(freq, norm);
    System.out.printf(
        java.util.Locale.ROOT,
        "%-45s freq=%.1f len=%d(norm=%d): score(df=500,ttf=1000)=%s score(df=499,ttf=999)=%s -> %s%n",
        sim, freq, docLen, norm, common, rarer, rarer < common ? "RARER SCORES LOWER" : "ok");
    return new float[] {common, rarer};
  }

  public void testBm25AndClassicHoldProperty() {
    for (Similarity sim : new Similarity[] {new BM25Similarity(), new ClassicSimilarity()}) {
      for (int len : new int[] {10, 100, 300, 1000}) {
        float[] p = pair(sim, 1f, len);
        assertTrue(sim + " len=" + len, p[1] >= p[0]);
      }
    }
  }

  /** DFR G-L-H1 with the default c=1: common term, freq=1, document 3x the average length. */
  public void testBasicModelGViolatesOnLongDocument() {
    Similarity sim = new DFRSimilarity(new BasicModelG(), new AfterEffectL(), new NormalizationH1());
    float[] shortDoc = pair(sim, 1f, 50); // tfn = 2 > lambda ~ 0.5 -> property holds
    assertTrue(shortDoc[1] >= shortDoc[0]);
    float[] longDoc = pair(sim, 1f, 300); // tfn ~ 0.33 < lambda ~ 0.5 -> property fails
    assertTrue("expected G to score the rarer term lower here", longDoc[1] < longDoc[0]);
  }

  /** Indri Dirichlet, default mu=2000: score = log((tf + mu*P(t|C)) / (len + mu)) grows with ttf. */
  public void testIndriViolatesForOrdinaryInputs() {
    Similarity sim = new IndriDirichletSimilarity();
    for (int len : new int[] {10, 100, 1000}) {
      float[] p = pair(sim, 1f, len);
      assertTrue("expected Indri to score the rarer term lower", p[1] < p[0]);
    }
  }
}
