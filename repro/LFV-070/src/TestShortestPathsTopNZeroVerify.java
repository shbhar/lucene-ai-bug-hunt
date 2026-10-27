import java.io.IOException;
import java.util.Comparator;
import java.util.NoSuchElementException;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRefBuilder;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.FSTCompiler;
import org.apache.lucene.util.fst.PositiveIntOutputs;
import org.apache.lucene.util.fst.Util;

/**
 * Independent verification: Util.shortestPaths / Util.TopNSearcher with topN == 0 (or
 * maxQueueDepth == 0). Deterministic: no use of random().
 */
public class TestShortestPathsTopNZeroVerify extends LuceneTestCase {

  private static final Comparator<Long> CMP = Comparator.naturalOrder();

  /** keys must be given in sorted (unsigned byte) order. */
  private static FST<Long> build(String[] keys, long[] outs) throws IOException {
    PositiveIntOutputs o = PositiveIntOutputs.getSingleton();
    FSTCompiler<Long> c = new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE1, o).build();
    IntsRefBuilder scratch = new IntsRefBuilder();
    for (int i = 0; i < keys.length; i++) {
      c.add(Util.toIntsRef(new BytesRef(keys[i]), scratch), outs[i]);
    }
    return FST.fromFSTReader(c.compile(), c.getFSTReader());
  }

  private static FST<Long> threeKeys() throws IOException {
    return build(new String[] {"car", "cat", "dog"}, new long[] {7, 2, 5});
  }

  private static Util.TopResults<Long> sp(FST<Long> fst, int topN, boolean allowEmpty)
      throws IOException {
    return Util.shortestPaths(fst, fst.getFirstArc(new FST.Arc<>()), 0L, CMP, topN, allowEmpty);
  }

  /** Control: topN = 1 works and returns the min-cost path ("cat" -> 2). */
  public void testControlTopNOne() throws IOException {
    Util.TopResults<Long> r = sp(threeKeys(), 1, false);
    assertEquals(1, r.topN.size());
    assertEquals(2L, (long) r.topN.get(0).output());
    assertEquals("cat", Util.toBytesRef(r.topN.get(0).input(), new org.apache.lucene.util.BytesRefBuilder()).utf8ToString());
  }

  /** The claim: topN = 0 should give an empty result. On main it throws. */
  public void testTopNZeroAllowEmptyFalse() throws IOException {
    FST<Long> fst = threeKeys();
    try {
      Util.TopResults<Long> r = sp(fst, 0, false);
      System.out.println("topN=0 allowEmpty=false -> size=" + r.topN.size() + " isComplete=" + r.isComplete);
      assertTrue(r.topN.isEmpty());
    } catch (NoSuchElementException e) {
      System.out.println("topN=0 allowEmpty=false -> " + e + " at " + e.getStackTrace()[0] + " / " + e.getStackTrace()[1]);
      dumpUtilFrames(e);
      fail("BUG REPRODUCED: shortestPaths(topN=0) threw NoSuchElementException");
    }
  }

  public void testTopNZeroAllowEmptyTrue() throws IOException {
    FST<Long> fst = threeKeys();
    try {
      Util.TopResults<Long> r = sp(fst, 0, true);
      assertTrue(r.topN.isEmpty());
    } catch (NoSuchElementException e) {
      System.out.println("topN=0 allowEmpty=true -> " + e);
      fail("BUG REPRODUCED: shortestPaths(topN=0, allowEmpty=true) threw NoSuchElementException");
    }
  }

  /** Characterization: a NEGATIVE topN does not throw (returns empty, isComplete=true). */
  public void testCharacterizeNegativeTopN() throws IOException {
    Util.TopResults<Long> r = sp(threeKeys(), -1, false);
    System.out.println("topN=-1 -> size=" + r.topN.size() + " isComplete=" + r.isComplete);
    assertTrue(r.topN.isEmpty());
  }

  /**
   * Characterization: an FST whose only accepted input is "" with allowEmptyString=false never
   * calls addIfCompetitive, so topN=0 does NOT throw there. The crash needs >= 1 candidate arc.
   */
  public void testCharacterizeOnlyEmptyStringNoThrow() throws IOException {
    FST<Long> fst = build(new String[] {""}, new long[] {4});
    Util.TopResults<Long> r = sp(fst, 0, false);
    System.out.println("only-\"\" fst, topN=0 allowEmpty=false -> size=" + r.topN.size());
    assertTrue(r.topN.isEmpty());
    // ...but allowEmptyString=true does reach addIfCompetitive with the END_LABEL arc:
    expectThrows(NoSuchElementException.class, () -> sp(fst, 0, true));
  }

  /** Same root cause through the public TopNSearcher constructor: topN=1, maxQueueDepth=0. */
  public void testTopNSearcherMaxQueueDepthZero() throws IOException {
    FST<Long> fst = threeKeys();
    Util.TopNSearcher<Long> s = new Util.TopNSearcher<>(fst, 1, 0, CMP);
    NoSuchElementException e =
        expectThrows(
            NoSuchElementException.class,
            () ->
                s.addStartPaths(fst.getFirstArc(new FST.Arc<>()), 0L, false, new IntsRefBuilder()));
    dumpUtilFrames(e);
  }

  private static void dumpUtilFrames(Throwable e) {
    for (StackTraceElement f : e.getStackTrace()) {
      if (f.getClassName().startsWith("org.apache.lucene.util.fst")
          || f.getClassName().startsWith("java.util.TreeMap")
          || f.getClassName().startsWith("java.util.TreeSet")) {
        System.out.println("    at " + f);
      }
    }
  }
}
