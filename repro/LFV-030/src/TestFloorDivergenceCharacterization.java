import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.CompiledAutomaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;
import org.apache.lucene.util.automaton.Transition;

/**
 * Characterizes WHEN CompiledAutomaton.floor diverges, against a bounded-enumeration oracle.
 *
 * <p>Oracle: for the compiled byte DFA with n states, M(L) = max accepted string s with s <= input
 * and |s| <= L. With L1 = |input| + n + 1 and L2 = L1 + n + 1: if M(L1) == M(L2) the floor exists
 * and equals M(L1) (an existing floor is p + c + acyclic max path, so |floor| <= |input| + n);
 * otherwise the set {s in L : s <= input} has no maximum.
 *
 * <p>Divergence detector: a BytesRefBuilder whose grow() throws once capacity exceeds 64 KiB, so a
 * diverging floor() call is caught cheaply instead of running to OOM.
 */
public class TestFloorDivergenceCharacterization extends LuceneTestCase {

  static final class Diverged extends RuntimeException {
    Diverged(int cap) {
      super("output grew to " + cap, null, false, false);
    }
  }

  static final class GuardedBuilder extends BytesRefBuilder {
    @Override
    public void grow(int capacity) {
      if (capacity > (1 << 16)) throw new Diverged(capacity);
      super.grow(capacity);
    }
  }

  private static String randomRegexp(Random r, int depth) {
    if (depth == 0 || r.nextInt(4) == 0) {
      return switch (r.nextInt(3)) {
        case 0 -> "a";
        case 1 -> "b";
        default -> "c";
      };
    }
    String x = randomRegexp(r, depth - 1);
    return switch (r.nextInt(5)) {
      case 0 -> "(" + x + randomRegexp(r, depth - 1) + ")";
      case 1 -> "(" + x + "|" + randomRegexp(r, depth - 1) + ")";
      case 2 -> "(" + x + ")*";
      case 3 -> "(" + x + ")?";
      default -> "(" + x + randomRegexp(r, depth - 1) + ")";
    };
  }

  /**
   * Max accepted s <= input with |s| <= maxLen; null if none. Bounded-length DP, written
   * independently of CompiledAutomaton.floor: R[q][k] = "q reaches an accept state within k steps";
   * then a tight/loose greedy construction (stay equal to input > go below input > stop).
   */
  private static BytesRef boundedMax(Automaton dfa, BytesRef input, int maxLen) {
    int n = dfa.getNumStates();
    boolean[][] reach = new boolean[n][maxLen + 1];
    for (int q = 0; q < n; q++) reach[q][0] = dfa.isAccept(q);
    Transition t = new Transition();
    for (int k = 1; k <= maxLen; k++) {
      for (int q = 0; q < n; q++) {
        boolean v = reach[q][k - 1];
        int nt = dfa.getNumTransitions(q);
        for (int i = 0; i < nt && v == false; i++) {
          dfa.getTransition(q, i, t);
          if (reach[t.dest][k - 1]) v = true;
        }
        reach[q][k] = v;
      }
    }
    BytesRefBuilder out = new BytesRefBuilder();
    if (tight(dfa, input, 0, 0, maxLen, reach, out)) return out.toBytesRef();
    return null;
  }

  private static int stepDfa(Automaton dfa, int q, int label) {
    Transition t = new Transition();
    int nt = dfa.getNumTransitions(q);
    for (int i = 0; i < nt; i++) {
      dfa.getTransition(q, i, t);
      if (t.min <= label && label <= t.max) return t.dest;
    }
    return -1;
  }

  /** Appends to out the max accepted suffix s (prefix so far == input[0..i)), s <= rest. */
  private static boolean tight(
      Automaton dfa, BytesRef input, int i, int q, int budget, boolean[][] reach,
      BytesRefBuilder out) {
    if (i == input.length) return dfa.isAccept(q); // only the input itself; extensions are > input
    int x = input.bytes[input.offset + i] & 0xff;
    int mark = out.length();
    // 1. stay equal to input at position i
    if (budget >= 1) {
      int q2 = stepDfa(dfa, q, x);
      if (q2 != -1) {
        out.append((byte) x);
        if (tight(dfa, input, i + 1, q2, budget - 1, reach, out)) return true;
        out.setLength(mark);
      }
    }
    // 2. largest label < x leading to a state that can still accept within budget-1
    if (budget >= 1) {
      Transition t = new Transition();
      int nt = dfa.getNumTransitions(q);
      for (int k = nt - 1; k >= 0; k--) {
        dfa.getTransition(q, k, t);
        if (t.min < x && reach[t.dest][budget - 1]) {
          out.append((byte) Math.min(t.max, x - 1));
          loose(dfa, t.dest, budget - 1, reach, out);
          return true;
        }
      }
    }
    // 3. stop here (proper prefix of input)
    return dfa.isAccept(q);
  }

  /** Appends the lexicographically largest accepted continuation of length <= k from q. */
  private static void loose(Automaton dfa, int q, int k, boolean[][] reach, BytesRefBuilder out) {
    Transition t = new Transition();
    while (true) {
      boolean moved = false;
      if (k >= 1) {
        int nt = dfa.getNumTransitions(q);
        for (int i = nt - 1; i >= 0; i--) {
          dfa.getTransition(q, i, t);
          if (reach[t.dest][k - 1]) {
            out.append((byte) t.max);
            q = t.dest;
            k--;
            moved = true;
            break;
          }
        }
      }
      if (moved == false) {
        assert dfa.isAccept(q);
        return;
      }
    }
  }

  public void testFloorDivergesExactlyWhenNoFloorExists() {
    Random r = new Random(0xF100A3L); // fixed, independent of tests.seed
    String[] inputs;
    {
      List<String> in = new ArrayList<>();
      String al = "abcd";
      for (int l = 1; l <= 3; l++) {
        int total = (int) Math.pow(al.length(), l);
        for (int k = 0; k < total; k++) {
          StringBuilder sb = new StringBuilder();
          int x = k;
          for (int j = 0; j < l; j++) {
            sb.append(al.charAt(x % al.length()));
            x /= al.length();
          }
          in.add(sb.toString());
        }
      }
      inputs = in.toArray(String[]::new);
    }
    int automata = 0, calls = 0, ok = 0, okNull = 0, diverged = 0, oracleNoFloor = 0;
    int mismatches = 0;
    List<String> firstMismatches = new ArrayList<>();
    List<String> examples = new ArrayList<>();
    for (int iter = 0; iter < 400; iter++) {
      String re = randomRegexp(r, 4);
      Automaton a =
          Operations.determinize(Operations.removeDeadStates(new RegExp(re).toAutomaton()), 100000);
      if (a.getNumStates() == 0) continue;
      CompiledAutomaton ca = new CompiledAutomaton(a, false, false); // NORMAL, unicode path
      Automaton dfa = ca.automaton;
      int n = dfa.getNumStates();
      automata++;
      for (String s : inputs) {
        BytesRef input = new BytesRef(s);
        int l1 = input.length + n + 1;
        int l2 = l1 + n + 1;
        BytesRef m1 = boundedMax(dfa, input, l1);
        BytesRef m2 = boundedMax(dfa, input, l2);
        boolean floorExists = (m1 == null && m2 == null) || (m1 != null && m1.equals(m2));
        if (floorExists == false) oracleNoFloor++;
        String got;
        calls++;
        try {
          BytesRef res = ca.floor(input, new GuardedBuilder());
          got = res == null ? "null" : res.utf8ToString();
          if (res == null) okNull++;
          ok++;
        } catch (Diverged d) {
          got = "DIVERGED";
          diverged++;
        }
        String expected = floorExists ? (m1 == null ? "null" : m1.utf8ToString()) : "DIVERGED";
        if (expected.equals(got) == false) {
          mismatches++;
          if (firstMismatches.size() < 10)
            firstMismatches.add(
                "re=" + re + " input=" + s + " expected=" + expected + " got=" + got);
        } else if (got.equals("DIVERGED") && examples.size() < 8) {
          examples.add("re=" + re + " input=" + s + " (bounded max L1=" + m1.utf8ToString()
              + ", L2=" + m2.utf8ToString() + ")");
        }
      }
    }
    System.out.printf(
        "automata=%d floor-calls=%d returned=%d (null=%d) diverged=%d oracle-no-floor=%d mismatches=%d%n",
        automata, calls, ok, okNull, diverged, oracleNoFloor, mismatches);
    for (String e : examples) System.out.println("  diverging example: " + e);
    for (String m : firstMismatches) System.out.println("  MISMATCH: " + m);
    // Claim under test: floor() returns the correct floor whenever one exists, and diverges
    // exactly when the set of accepted terms <= input is non-empty but has no maximum.
    assertEquals("mismatches: " + firstMismatches, 0, mismatches);
    assertTrue("expected some diverging cases", diverged > 0);
  }

  /** Public-API reachability: floor() on the CompiledAutomaton of a stock PrefixQuery("b"). */
  public void testPrefixQueryCompiledAutomaton() {
    org.apache.lucene.search.PrefixQuery pq =
        new org.apache.lucene.search.PrefixQuery(new org.apache.lucene.index.Term("f", "b"));
    CompiledAutomaton ca = pq.getCompiled();
    System.out.println("PrefixQuery(b) compiled type=" + ca.type + " finite=" + ca.finite);
    assertEquals(CompiledAutomaton.AUTOMATON_TYPE.NORMAL, ca.type);
    assertEquals("b", ca.floor(new BytesRef("b"), new GuardedBuilder()).utf8ToString());
    assertNull(ca.floor(new BytesRef("a"), new GuardedBuilder()));
    String got;
    try {
      BytesRef res = ca.floor(new BytesRef("c"), new GuardedBuilder());
      got = res == null ? "null" : res.utf8ToString();
    } catch (Diverged d) {
      got = "DIVERGED (" + d.getMessage() + ")";
    }
    System.out.println("PrefixQuery(b).getCompiled().floor(\"c\") -> " + got);
  }

  /** Control: the only in-tree caller (FuzzyTermsEnum) uses finite Levenshtein automata. */
  public void testLevenshteinAutomataNeverDiverge() {
    Random r = new Random(42);
    int calls = 0;
    for (String term : new String[] {"foo", "lucene", "ab", "x", "banana"}) {
      for (int ed = 0; ed <= 2; ed++) {
        for (boolean transpositions : new boolean[] {false, true}) {
          Automaton lev =
              new org.apache.lucene.util.automaton.LevenshteinAutomata(term, transpositions)
                  .toAutomaton(ed);
          CompiledAutomaton ca = new CompiledAutomaton(lev, true, false); // as FuzzyAutomatonBuilder
          for (int i = 0; i < 500; i++) {
            int len = 1 + r.nextInt(8);
            byte[] b = new byte[len];
            for (int j = 0; j < len; j++) b[j] = (byte) ('a' + r.nextInt(26));
            ca.floor(new BytesRef(b), new GuardedBuilder()); // throws Diverged if it loops
            calls++;
          }
        }
      }
    }
    System.out.println("Levenshtein control: " + calls + " floor() calls, none diverged");
  }
}
