import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.FSTCompiler;
import org.apache.lucene.util.fst.IntsRefFSTEnum;
import org.apache.lucene.util.fst.PositiveIntOutputs;
import org.apache.lucene.util.fst.Util;

/**
 * Independent check: an FST over INPUT_TYPE.BYTE4 whose node has fixed-length arcs and labels
 * spanning (last - first) >= 2^31 - 8 fails to compile. Each test asserts the CORRECT behaviour,
 * so a failure means the bug is present. Deterministic: no randomness is used.
 */
public class VerifyFstWideLabelSpan extends LuceneTestCase {

  private static final int MAX = Integer.MAX_VALUE;

  /** Builds an FST mapping each key (an int[] path) to output i+1, then verifies it. */
  private static void buildAndCheck(int[][] keys, float daFactor, boolean fixedLengthArcs)
      throws Exception {
    PositiveIntOutputs outputs = PositiveIntOutputs.getSingleton();
    FSTCompiler<Long> c =
        new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE4, outputs)
            .directAddressingMaxOversizingFactor(daFactor)
            .allowFixedLengthArcs(fixedLengthArcs)
            .build();
    for (int i = 0; i < keys.length; i++) {
      c.add(new IntsRef(keys[i], 0, keys[i].length), (long) (i + 1));
    }
    FST<Long> fst = FST.fromFSTReader(c.compile(), c.getFSTReader());
    for (int i = 0; i < keys.length; i++) {
      assertEquals(
          Long.valueOf(i + 1), Util.get(fst, new IntsRef(keys[i], 0, keys[i].length)));
    }
    IntsRefFSTEnum<Long> e = new IntsRefFSTEnum<>(fst);
    List<Long> seen = new ArrayList<>();
    IntsRefFSTEnum.InputOutput<Long> io;
    while ((io = e.next()) != null) {
      seen.add(io.output);
    }
    assertEquals(keys.length, seen.size());
  }

  private static int[][] singleLabelKeys(int... labels) {
    int[][] keys = new int[labels.length][];
    for (int i = 0; i < labels.length; i++) {
      keys[i] = new int[] {labels[i]};
    }
    return keys;
  }

  /** Root node, 5 arcs (>= FIXED_LENGTH_ARC_SHALLOW_NUM_ARCS), span MAX-6 (presence count wraps). */
  public void testRootSpanWrapsDefaultFactor() throws Exception {
    buildAndCheck(singleLabelKeys(6, 7, 8, 9, MAX), 1f, true);
  }

  /** Same, but with direct addressing "disabled" per the Builder javadoc (factor -1). */
  public void testRootSpanWrapsFactorMinusOne() throws Exception {
    buildAndCheck(singleLabelKeys(0, 1, 2, 3, MAX - 1), -1f, true);
  }

  /** labels 0..MAX: labelRange = MAX+1 itself wraps to Integer.MIN_VALUE. */
  public void testFullIntRange() throws Exception {
    buildAndCheck(singleLabelKeys(0, 1, 2, 3, MAX), 1f, true);
  }

  /** Deep node (depth 4) with 10 arcs (>= FIXED_LENGTH_ARC_DEEP_NUM_ARCS). */
  public void testDeepNodeTenArcs() throws Exception {
    int[] tails = {1, 2, 3, 4, 5, 6, 7, 8, 9, MAX};
    int[][] keys = new int[tails.length][];
    for (int i = 0; i < tails.length; i++) {
      keys[i] = new int[] {1, 1, 1, 1, tails[i]};
    }
    buildAndCheck(keys, 1f, true);
  }

  /** Control: span MAX-8 = 2^31-9 does NOT wrap; binary search is chosen and all works. */
  public void testControlSpanJustBelowThreshold() throws Exception {
    buildAndCheck(singleLabelKeys(8, 9, 10, 11, MAX), 1f, true);
  }

  /** Control: only 4 root arcs -> no fixed-length arcs at all -> works even with 0..MAX. */
  public void testControlFourArcsNoFixedLength() throws Exception {
    buildAndCheck(singleLabelKeys(0, 1, 2, MAX), 1f, true);
  }

  /** Workaround: allowFixedLengthArcs(false) avoids the bug for the failing key set. */
  public void testWorkaroundNoFixedLengthArcs() throws Exception {
    buildAndCheck(singleLabelKeys(0, 1, 2, 3, MAX), 1f, false);
  }

  /** Boundary sweep: first label f in 0..9, labels {f..f+3, MAX}. Report which fail. */
  public void testBoundarySweep() throws Exception {
    StringBuilder sb = new StringBuilder();
    List<Integer> failing = new ArrayList<>();
    for (int f = 0; f <= 9; f++) {
      long span = (long) MAX - f;
      String outcome;
      try {
        buildAndCheck(singleLabelKeys(f, f + 1, f + 2, f + 3, MAX), 1f, true);
        outcome = "OK";
      } catch (Throwable t) {
        outcome = t.getClass().getSimpleName() + ": " + t.getMessage();
        failing.add(f);
      }
      sb.append("first=")
          .append(f)
          .append(" span(last-first)=")
          .append(span)
          .append(" (2^31-")
          .append((1L << 31) - span)
          .append(") -> ")
          .append(outcome)
          .append('\n');
    }
    System.out.print(sb);
    // Correct behaviour: nothing fails.
    assertTrue("failing first labels: " + failing + "\n" + sb, failing.isEmpty());
  }
}
