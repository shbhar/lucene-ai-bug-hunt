import java.util.ArrayDeque;
import java.util.BitSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.AutomatonQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.CompiledAutomaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.Transition;

/**
 * Independent verification of the candidate "Operations.isTotal returns true for a non-total DFA
 * with a dead state (and false negatives for custom alphabets)". Written from scratch; does not
 * reuse the candidate's FvRef reference or repro tests.
 */
public class TestIsTotalVerify extends LuceneTestCase {

  /** 0 (accept) -[0..0x60]-> 1 (non-accept, no out transitions); 0 -[0x61..MAX]-> 0. */
  private static Automaton deadStateDfa() {
    Automaton a = new Automaton();
    int s0 = a.createState();
    int dead = a.createState();
    a.setAccept(s0, true);
    a.addTransition(s0, dead, Character.MIN_CODE_POINT, 'a' - 1);
    a.addTransition(s0, s0, 'a', Character.MAX_CODE_POINT);
    a.finishState();
    return a;
  }

  // ---- Mode 1: false positive caused by a (reachable) dead state ----

  public void testFalsePositiveWithDeadState() {
    Automaton a = deadStateDfa();
    assertTrue("precondition: DFA", a.isDeterministic());
    assertTrue("has a dead state", Operations.hasDeadStates(a));
    assertTrue(Operations.run(a, ""));
    assertTrue(Operations.run(a, "b"));
    assertFalse("\"0\" (U+0030) is rejected", Operations.run(a, "0"));
    // Language is NOT total, so isTotal must be false:
    assertFalse("isTotal(a) for a DFA that rejects \"0\"", Operations.isTotal(a));
  }

  public void testSameLanguageDifferentAnswer() {
    Automaton a = deadStateDfa();
    Automaton b = Operations.removeDeadStates(a);
    // removeDeadStates preserves the language; spot-check on boundary code points, len <= 3
    int[] cps = {0, '0', 'a' - 1, 'a', 'b', 0xFFFF, Character.MAX_CODE_POINT};
    for (int x : cps) {
      for (int y : cps) {
        for (int z : cps) {
          for (String w :
              new String[] {
                "", new String(new int[] {x}, 0, 1), new String(new int[] {x, y}, 0, 2),
                new String(new int[] {x, y, z}, 0, 3)
              }) {
            assertEquals(w, Operations.run(a, w), Operations.run(b, w));
          }
        }
      }
    }
    System.out.println(
        "isTotal(a)=" + Operations.isTotal(a) + " isTotal(removeDeadStates(a))=" + Operations.isTotal(b));
    assertEquals(
        "isTotal must depend only on the language", Operations.isTotal(b), Operations.isTotal(a));
  }

  public void testBinaryFalsePositiveWithDeadState() {
    Automaton a = new Automaton();
    int s0 = a.createState();
    int dead = a.createState();
    a.setAccept(s0, true);
    a.addTransition(s0, s0, 0, 'x' - 1);
    a.addTransition(s0, dead, 'x', 'x');
    a.addTransition(s0, s0, 'x' + 1, 0xff);
    a.finishState();
    assertTrue(a.isDeterministic());
    assertFalse("byte 'x' rejected", Operations.run(a, "x"));
    assertFalse("isTotal(a,0,255) for a byte DFA that rejects 'x'", Operations.isTotal(a, 0, 0xff));
  }

  /** Consequence via public query API (outside CompiledAutomaton's documented domain). */
  public void testAutomatonQueryEndToEnd() throws Exception {
    Automaton a = deadStateDfa();
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String v : new String[] {"0", "b", "zz"}) {
          Document d = new Document();
          d.add(new StringField("f", v, Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        AutomatonQuery q = new AutomatonQuery(new Term("f", ""), a);
        AutomatonQuery qClean = new AutomatonQuery(new Term("f", ""), Operations.removeDeadStates(a));
        int hits = s.count(q);
        int hitsClean = s.count(qClean);
        CompiledAutomaton ca = new CompiledAutomaton(a, false, true, false);
        System.out.println(
            "AutomatonQuery(dead-state DFA) hits="
                + hits
                + " compiled.type="
                + ca.type
                + " ; same language w/o dead state hits="
                + hitsClean
                + " compiled.type="
                + new CompiledAutomaton(Operations.removeDeadStates(a), false, true, false).type);
        assertEquals("only \"b\" and \"zz\" are in the language", 2, hits);
      }
    }
  }

  // ---- Mode 2: false negative with a custom alphabet [min,max] narrower than the labels ----

  public void testCustomAlphabetFalseNegativeGap() {
    Automaton a = new Automaton();
    int s0 = a.createState();
    a.setAccept(s0, true);
    a.addTransition(s0, s0, 'a', 'a');
    a.addTransition(s0, s0, 'c', 'c');
    a.finishState();
    assertTrue(a.isDeterministic());
    for (int n = 0; n < 6; n++) {
      assertTrue(Operations.run(a, "c".repeat(n)));
    }
    // every string over {c} is accepted (state 0 is accepting and 'c' loops on it)
    assertTrue("isTotal(a,'c','c') for a DFA accepting c*", Operations.isTotal(a, 'c', 'c'));
  }

  public void testCustomAlphabetFalseNegativeOutOfAlphabetState() {
    Automaton a = new Automaton();
    int s0 = a.createState();
    int s1 = a.createState();
    a.setAccept(s0, true);
    a.addTransition(s0, s0, 'b', 'c'); // covers the whole alphabet [b,c]
    a.addTransition(s0, s1, 'z', 'z'); // s1 only reachable by out-of-alphabet 'z'
    a.addTransition(s1, s0, 'z', 'z'); // s1 is live (zz accepted) but non-accepting
    a.finishState();
    assertTrue(a.isDeterministic());
    assertTrue("isTotal(a,'b','c') for a DFA accepting all of [b-c]*", Operations.isTotal(a, 'b', 'c'));
  }

  // ---- Exhaustive differential check against a brute-force oracle ----

  /** Corrected isTotal: BFS from the initial state over in-alphabet labels, clipped gap scan. */
  static boolean fixedIsTotal(Automaton a, int min, int max) {
    if (a.getNumStates() == 0) return false;
    BitSet seen = new BitSet();
    ArrayDeque<Integer> q = new ArrayDeque<>();
    q.add(0);
    seen.set(0);
    Transition t = new Transition();
    while (q.isEmpty() == false) {
      int s = q.poll();
      if (a.isAccept(s) == false) return false;
      long prev = (long) min - 1;
      int n = a.initTransition(s, t);
      for (int i = 0; i < n; i++) {
        a.getNextTransition(t);
        int lo = Math.max(t.min, min), hi = Math.min(t.max, max);
        if (lo > hi) continue;
        if (lo > prev + 1) return false;
        prev = Math.max(prev, hi);
        if (seen.get(t.dest) == false) {
          seen.set(t.dest);
          q.add(t.dest);
        }
      }
      if (prev < max) return false;
    }
    return true;
  }

  /**
   * Oracle: a DFA with n states that rejects some string over sigma rejects one of length <= n
   * (shortest rejected word visits pairwise-distinct accepting states before the last step), so
   * enumerating all words of length <= n decides totality exactly.
   */
  static boolean bruteTotal(int[][] dest, boolean[] acc, int[] sigma) {
    int n = acc.length;
    int k = sigma.length;
    for (int len = 0; len <= n; len++) {
      int total = 1;
      for (int i = 0; i < len; i++) total *= k;
      for (int w = 0; w < total; w++) {
        int s = 0, x = w;
        boolean ok = true;
        for (int i = 0; i < len && ok; i++) {
          int sym = sigma[x % k];
          x /= k;
          s = dest[s][sym];
          if (s < 0) ok = false;
        }
        if (ok == false || acc[s] == false) return false;
      }
    }
    return true;
  }

  private void exhaustive(int maxStates, int numLabels, int[] sigma, int min, int max, String label) {
    long count = 0, fpMain = 0, fnMain = 0, fpDeadOnly = 0, badFixed = 0;
    String firstFp = null, firstFn = null;
    for (int n = 1; n <= maxStates; n++) {
      int perState = 2;
      for (int i = 0; i < numLabels; i++) perState *= (n + 1);
      long combos = 1;
      for (int i = 0; i < n; i++) combos *= perState;
      int[][] dest = new int[n][numLabels];
      boolean[] acc = new boolean[n];
      for (long c = 0; c < combos; c++) {
        long x = c;
        for (int s = 0; s < n; s++) {
          long v = x % perState;
          x /= perState;
          acc[s] = (v & 1) == 1;
          v >>= 1;
          for (int l = 0; l < numLabels; l++) {
            dest[s][l] = (int) (v % (n + 1)) - 1;
            v /= (n + 1);
          }
        }
        Automaton a = new Automaton();
        for (int s = 0; s < n; s++) {
          a.createState();
          a.setAccept(s, acc[s]);
        }
        for (int s = 0; s < n; s++) {
          for (int l = 0; l < numLabels; l++) {
            if (dest[s][l] >= 0) a.addTransition(s, dest[s][l], 'a' + l, 'a' + l);
          }
        }
        a.finishState();
        count++;
        boolean truth = bruteTotal(dest, acc, sigma);
        boolean main = Operations.isTotal(a, min, max);
        boolean fixed = fixedIsTotal(a, min, max);
        if (main && !truth) {
          fpMain++;
          if (Operations.hasDeadStates(a)) fpDeadOnly++;
          if (firstFp == null) firstFp = a.toString().replace('\n', ' ');
        }
        if (!main && truth) {
          fnMain++;
          if (firstFn == null) firstFn = a.toString().replace('\n', ' ');
        }
        if (fixed != truth) badFixed++;
      }
    }
    System.out.println(
        label
            + ": automata="
            + count
            + " mainFalsePositives="
            + fpMain
            + " (with dead states: "
            + fpDeadOnly
            + ") mainFalseNegatives="
            + fnMain
            + " fixedDisagreements="
            + badFixed);
    if (firstFp != null) System.out.println("  first FP: " + firstFp);
    if (firstFn != null) System.out.println("  first FN: " + firstFn);
    assertEquals("fixed version must agree with oracle", 0, badFixed);
    assertEquals(label + ": main false positives", 0, fpMain);
    assertEquals(label + ": main false negatives", 0, fnMain);
  }

  /** In-domain: all labels inside [min,max] (alphabet {a,b,c}); isolates the dead-state mode. */
  public void testExhaustiveLabelsInsideAlphabet() {
    exhaustive(3, 3, new int[] {0, 1, 2}, 'a', 'c', "labels{a,b,c} alphabet[a,c]");
  }

  /** Custom alphabet narrower than the labels: labels {a,b,c}, alphabet [b,c]. */
  public void testExhaustiveLabelsOutsideAlphabet() {
    exhaustive(3, 3, new int[] {1, 2}, 'b', 'c', "labels{a,b,c} alphabet[b,c]");
  }
}
