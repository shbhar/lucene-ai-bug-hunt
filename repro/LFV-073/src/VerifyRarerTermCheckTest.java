import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.apache.lucene.search.FieldStats;
import org.apache.lucene.search.TermStats;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.Similarity.SimScorer;
import org.apache.lucene.tests.search.similarities.BaseSimilarityTestCase;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Deterministic check of BaseSimilarityTestCase's "scores don't decrease as terms get rarer"
 * assertion. All inputs are fixed; the only randomness inside doTestScoring (choice of prevFreq,
 * rarely() explanation verification) does not influence the prevTerm branch.
 */
public class VerifyRarerTermCheckTest extends LuceneTestCase {

  // corpus: 100 docs, consistent stats
  static final FieldStats CORPUS = new FieldStats("field", 100, 100, 1000, 500);
  // term with docFreq=5, ttf=10; "prevTerm" as built by the framework is (4, 9)
  static final TermStats TERM = new TermStats(new BytesRef("term"), 5, 10);
  static final TermStats RARER = new TermStats(new BytesRef("term"), 4, 9);
  static final float BOOST = 1f;
  static final float FREQ = 1f; // < ttf, so the prevTerm branch's guard is true
  static final int NORM = 1;

  /** Sanity: the similarity really violates the property for these exact inputs. */
  public void testSimilarityViolatesRarerTermProperty() {
    Similarity sim = new AntiIdfSimilarity();
    float score = sim.scorer(BOOST, CORPUS, TERM).score(FREQ, NORM);
    float rarerScore = sim.scorer(BOOST, CORPUS, RARER).score(FREQ, NORM);
    System.out.println(
        "score(df=5,ttf=10)=" + score + "  score(df=4,ttf=9)=" + rarerScore + "  (rarer is lower)");
    assertTrue(
        "expected rarer term to score strictly LOWER (property violated)", rarerScore < score);
  }

  /**
   * Expected behaviour per BaseSimilarityTestCase#testRandomScoring javadoc: the per-case checker
   * must reject this (similarity, term) pair. Fails on main 7cecf79523.
   */
  public void testFrameworkRejectsRarerTermViolation() throws Exception {
    Method m =
        BaseSimilarityTestCase.class.getDeclaredMethod(
            "doTestScoring",
            Similarity.class,
            FieldStats.class,
            TermStats.class,
            float.class,
            float.class,
            int.class);
    m.setAccessible(true);
    Throwable thrown = null;
    try {
      m.invoke(null, new AntiIdfSimilarity(), CORPUS, TERM, BOOST, FREQ, NORM);
    } catch (InvocationTargetException e) {
      thrown = e.getCause();
    }
    System.out.println("doTestScoring threw: " + thrown);
    assertNotNull(
        "BaseSimilarityTestCase.doTestScoring accepted a similarity whose score for a rarer term"
            + " (df=4,ttf=9) is lower than for (df=5,ttf=10): the 'scores don't decrease as terms"
            + " get rarer' check is ineffective",
        thrown);
    assertTrue(
        "unexpected failure kind: " + thrown,
        thrown instanceof AssertionError && String.valueOf(thrown.getMessage()).contains("< score("));
  }

  /**
   * Shows WHY the check cannot fire: the framework re-scores with the same TermStats, so the
   * "prevTermScore" equals "score" for any deterministic similarity.
   */
  public void testSameStatsGiveIdenticalScore() {
    Similarity sim = new AntiIdfSimilarity();
    SimScorer a = sim.scorer(BOOST, CORPUS, TERM);
    SimScorer b = sim.scorer(BOOST, CORPUS, TERM);
    assertEquals(
        Float.floatToRawIntBits(a.score(FREQ, NORM)), Float.floatToRawIntBits(b.score(FREQ, NORM)));
  }
}
