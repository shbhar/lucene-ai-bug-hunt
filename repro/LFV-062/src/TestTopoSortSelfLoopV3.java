import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.automaton.AutomatonTestUtil;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;
import org.apache.lucene.util.automaton.Transition;

/**
 * Independent check of the claim "Operations.topoSortStates misses self-loop cycles".
 *
 * <p>Contract under test (Operations.java javadoc of topoSortStates(Automaton)): "This method
 * assumes that the automaton does not contain cycles, and will throw an IllegalArgumentException if
 * a cycle is detected."
 *
 * <p>Tests named testContract* assert the documented contract (expected to FAIL on main if the bug
 * is real). Tests named testControl* / testExhaustive* characterize behavior and are expected to
 * PASS on main.
 */
public class TestTopoSortSelfLoopV3 extends LuceneTestCase {

  // ---------------------------------------------------------------------------------------------
  // Contract tests (expected to fail on main 7cecf79523 if the candidate bug is real)
  // ---------------------------------------------------------------------------------------------

  /** 0 -a-> 1 (accept), 1 -b-> 1 : language a b*, infinite, has a cycle (self loop on 1). */
  public void testContractSelfLoopOnLeaf() {
    Automaton a = new Automaton();
    int s0 = a.createState();
    int s1 = a.createState();
    a.setAccept(s1, true);
    a.addTransition(s0, s1, 'a');
    a.addTransition(s1, s1, 'b');
    a.finishState();
    assertFalse("sanity: automaton is infinite", AutomatonTestUtil.isFinite(a));
    assertThrowsCycle(a, "0 -a-> 1(acc), 1 -b-> 1");
  }

  /** The same shape arises from an ordinary regexp: ab* (determinized). */
  public void testContractRegExpABStar() {
    Automaton a =
        Operations.determinize(
            new RegExp("ab*").toAutomaton(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
    a = Operations.removeDeadStates(a);
    System.out.println("ab* automaton:\n" + a.toDot());
    assertFalse("sanity: ab* is infinite", AutomatonTestUtil.isFinite(a));
    assertThrowsCycle(a, "RegExp(ab*)");
  }

  /** a+ : 0 -a-> 1(acc), 1 -a-> 1 after determinization. */
  public void testContractRegExpAPlus() {
    Automaton a =
        Operations.determinize(
            new RegExp("a+").toAutomaton(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
    a = Operations.removeDeadStates(a);
    assertFalse("sanity: a+ is infinite", AutomatonTestUtil.isFinite(a));
    assertThrowsCycle(a, "RegExp(a+)");
  }

  /**
   * Self loop on a state whose only other successor was already visited (so it never pushes a
   * child): 0 -a-> 2, 0 -b-> 1, 1 -a-> 1, 1 -b-> 2, 2 accept.
   */
  public void testContractSelfLoopWithVisitedSibling() {
    Automaton a = new Automaton();
    int s0 = a.createState();
    int s1 = a.createState();
    int s2 = a.createState();
    a.setAccept(s2, true);
    a.addTransition(s0, s2, 'a');
    a.addTransition(s0, s1, 'b');
    a.addTransition(s1, s1, 'a');
    a.addTransition(s1, s2, 'b');
    a.finishState();
    assertFalse(AutomatonTestUtil.isFinite(a));
    assertThrowsCycle(a, "0-a->2,0-b->1,1-a->1,1-b->2");
  }

  // ---------------------------------------------------------------------------------------------
  // Controls (expected to pass on main): other cycle shapes ARE detected.
  // ---------------------------------------------------------------------------------------------

  /** Self loop on the initial state: detected (state 0 is pushed twice, then seen on-stack). */
  public void testControlSelfLoopOnInitialDetected() {
    Automaton a = new Automaton();
    int s0 = a.createState();
    a.setAccept(s0, true);
    a.addTransition(s0, s0, 'a');
    a.finishState();
    expectThrows(IllegalArgumentException.class, () -> Operations.topoSortStates(a));
  }

  /** Two-cycle 1 <-> 2 not through state 0: detected. */
  public void testControlTwoCycleDetected() {
    Automaton a = new Automaton();
    int s0 = a.createState();
    int s1 = a.createState();
    int s2 = a.createState();
    a.setAccept(s2, true);
    a.addTransition(s0, s1, 'a');
    a.addTransition(s1, s2, 'a');
    a.addTransition(s2, s1, 'a');
    a.finishState();
    expectThrows(IllegalArgumentException.class, () -> Operations.topoSortStates(a));
  }

  /**
   * Self loop on a state that ALSO pushes an unvisited child: detected, because onStack(state) is
   * set when it pushes the child, and the self loop is re-scanned on the next peek.
   */
  public void testControlSelfLoopWithUnvisitedChildDetected() {
    Automaton a = new Automaton();
    int s0 = a.createState();
    int s1 = a.createState();
    int s2 = a.createState();
    a.setAccept(s2, true);
    a.addTransition(s0, s1, 'a');
    a.addTransition(s1, s1, 'a');
    a.addTransition(s1, s2, 'b');
    a.finishState();
    expectThrows(IllegalArgumentException.class, () -> Operations.topoSortStates(a));
  }

  // ---------------------------------------------------------------------------------------------
  // Exhaustive characterization over all digraphs with n <= 4 nodes, two successor orders.
  // Reference = Kahn's algorithm on the subgraph reachable from 0 (independent of DFS).
  // ---------------------------------------------------------------------------------------------

  public void testExhaustiveCharacterization() {
    long total = 0, cyclic = 0;
    long mainMissed = 0, mainMissedSelfLoopOnly = 0, mainFalseAlarm = 0, mainBadOrder = 0;
    long fixMissed = 0, fixFalseAlarm = 0, fixBadOrder = 0;
    String firstMiss = null;
    for (int n = 1; n <= 4; n++) {
      int edges = n * n;
      for (long mask = 0; mask < (1L << edges); mask++) {
        for (int order = 0; order < 2; order++) {
          total++;
          boolean[][] adj = new boolean[n][n];
          for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
              adj[i][j] = ((mask >>> (i * n + j)) & 1) != 0;
            }
          }
          Automaton a = build(adj, order == 1);
          boolean[] reach = reachable(adj);
          boolean refCyclic = !kahnAcyclic(adj, reach, false);
          if (refCyclic) {
            cyclic++;
          }
          // main
          int[] out = null;
          boolean threw = false;
          try {
            out = Operations.topoSortStates(a);
          } catch (IllegalArgumentException e) {
            threw = true;
          }
          if (refCyclic && !threw) {
            mainMissed++;
            // is the reachable graph acyclic once self loops are ignored, i.e. the ONLY
            // reachable cycles are self loops?
            if (kahnAcyclic(adj, reach, true)) {
              mainMissedSelfLoopOnly++;
            }
            if (firstMiss == null) {
              firstMiss = "n=" + n + " adj=" + Arrays.deepToString(adj) + " desc=" + (order == 1)
                  + " -> " + Arrays.toString(out);
            }
          } else if (!refCyclic && threw) {
            mainFalseAlarm++;
          } else if (!refCyclic && !validTopo(adj, reach, out)) {
            mainBadOrder++;
          }
          // fixed copy
          int[] fout = null;
          boolean fthrew = false;
          try {
            fout = fixedTopoSortStates(a);
          } catch (IllegalArgumentException e) {
            fthrew = true;
          }
          if (refCyclic && !fthrew) {
            fixMissed++;
          } else if (!refCyclic && fthrew) {
            fixFalseAlarm++;
          } else if (!refCyclic && !validTopo(adj, reach, fout)) {
            fixBadOrder++;
          }
        }
      }
    }
    System.out.println(
        "EXHAUSTIVE n<=4 (x2 successor orders): cases="
            + total
            + " cyclicReachable="
            + cyclic
            + " | MAIN missed="
            + mainMissed
            + " (of which only-self-loop-cycles="
            + mainMissedSelfLoopOnly
            + ") falseAlarm="
            + mainFalseAlarm
            + " badOrder="
            + mainBadOrder
            + " | FIXED missed="
            + fixMissed
            + " falseAlarm="
            + fixFalseAlarm
            + " badOrder="
            + fixBadOrder);
    System.out.println("first main miss: " + firstMiss);
    // Characterization of main: every miss is a graph whose only reachable cycles are self loops,
    // and there are no false alarms and no invalid orders on acyclic inputs.
    assertTrue("main misses something", mainMissed > 0);
    assertEquals("all main misses are self-loop-only", mainMissed, mainMissedSelfLoopOnly);
    assertEquals(0, mainFalseAlarm);
    assertEquals(0, mainBadOrder);
    // Fixed copy is exact on this scope.
    assertEquals(0, fixMissed);
    assertEquals(0, fixFalseAlarm);
    assertEquals(0, fixBadOrder);
  }

  /**
   * Random larger graphs (n in [2,40]) with a random successor order per state (random distinct
   * labels). Same reference and same characterization as the exhaustive test.
   */
  public void testRandomLargerGraphs() {
    java.util.Random r = new java.util.Random(0xC0FFEEL); // fixed, independent of tests.seed
    int iters = 200_000;
    long cyclic = 0, mainMissed = 0, mainMissedSelfLoopOnly = 0, mainFalseAlarm = 0;
    long mainBadOrder = 0, fixMissed = 0, fixFalseAlarm = 0, fixBadOrder = 0;
    for (int it = 0; it < iters; it++) {
      int n = 2 + r.nextInt(39);
      double p = r.nextDouble() * 3.0 / n; // sparse-ish so that acyclic + self-loop-only occur
      double pSelf = r.nextDouble() * 0.3;
      boolean forwardOnly = r.nextBoolean(); // DAG skeleton (+ optional self loops)
      boolean[][] adj = new boolean[n][n];
      for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
          if (i == j) {
            adj[i][j] = r.nextDouble() < pSelf;
          } else if (!forwardOnly || j > i) {
            adj[i][j] = r.nextDouble() < p;
          }
        }
      }
      // random successor order: random permutation of labels per state
      Automaton a = new Automaton();
      for (int i = 0; i < n; i++) a.createState();
      a.setAccept(n - 1, true);
      for (int i = 0; i < n; i++) {
        int[] perm = new int[n];
        for (int k = 0; k < n; k++) perm[k] = k;
        for (int k = n - 1; k > 0; k--) {
          int m = r.nextInt(k + 1);
          int tmp = perm[k];
          perm[k] = perm[m];
          perm[m] = tmp;
        }
        for (int j = 0; j < n; j++) {
          if (adj[i][j]) a.addTransition(i, j, 'a' + perm[j]);
        }
      }
      a.finishState();
      boolean[] reach = reachable(adj);
      boolean refCyclic = !kahnAcyclic(adj, reach, false);
      if (refCyclic) cyclic++;
      int[] out = null;
      boolean threw = false;
      try {
        out = Operations.topoSortStates(a);
      } catch (IllegalArgumentException e) {
        threw = true;
      }
      if (refCyclic && !threw) {
        mainMissed++;
        if (kahnAcyclic(adj, reach, true)) mainMissedSelfLoopOnly++;
      } else if (!refCyclic && threw) {
        mainFalseAlarm++;
      } else if (!refCyclic && !validTopo(adj, reach, out)) {
        mainBadOrder++;
      }
      int[] fout = null;
      boolean fthrew = false;
      try {
        fout = fixedTopoSortStates(a);
      } catch (IllegalArgumentException e) {
        fthrew = true;
      }
      if (refCyclic && !fthrew) {
        fixMissed++;
      } else if (!refCyclic && fthrew) {
        fixFalseAlarm++;
      } else if (!refCyclic && !validTopo(adj, reach, fout)) {
        fixBadOrder++;
      }
    }
    System.out.println(
        "RANDOM n in [2,40]: cases="
            + iters
            + " cyclicReachable="
            + cyclic
            + " | MAIN missed="
            + mainMissed
            + " (only-self-loop-cycles="
            + mainMissedSelfLoopOnly
            + ") falseAlarm="
            + mainFalseAlarm
            + " badOrder="
            + mainBadOrder
            + " | FIXED missed="
            + fixMissed
            + " falseAlarm="
            + fixFalseAlarm
            + " badOrder="
            + fixBadOrder);
    assertTrue(mainMissed > 0);
    assertEquals(mainMissed, mainMissedSelfLoopOnly);
    assertEquals(0, mainFalseAlarm);
    assertEquals(0, mainBadOrder);
    assertEquals(0, fixMissed);
    assertEquals(0, fixFalseAlarm);
    assertEquals(0, fixBadOrder);
  }

  // ---------------------------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------------------------

  private static void assertThrowsCycle(Automaton a, String what) {
    int[] out;
    try {
      out = Operations.topoSortStates(a);
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("cycle"));
      return;
    }
    fail(
        "topoSortStates("
            + what
            + ") contains a cycle but returned "
            + Arrays.toString(out)
            + " without IllegalArgumentException");
  }

  /** Builds an automaton; successor order controlled via labels (iteration is by min label). */
  private static Automaton build(boolean[][] adj, boolean descending) {
    int n = adj.length;
    Automaton a = new Automaton();
    for (int i = 0; i < n; i++) {
      a.createState();
    }
    a.setAccept(n - 1, true);
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        if (adj[i][j]) {
          int label = 'a' + (descending ? (n - 1 - j) : j);
          a.addTransition(i, j, label);
        }
      }
    }
    a.finishState();
    return a;
  }

  private static boolean[] reachable(boolean[][] adj) {
    int n = adj.length;
    boolean[] r = new boolean[n];
    ArrayDeque<Integer> q = new ArrayDeque<>();
    r[0] = true;
    q.add(0);
    while (!q.isEmpty()) {
      int u = q.poll();
      for (int v = 0; v < n; v++) {
        if (adj[u][v] && !r[v]) {
          r[v] = true;
          q.add(v);
        }
      }
    }
    return r;
  }

  /** Kahn's algorithm on the reachable subgraph; optionally ignoring self loops. */
  private static boolean kahnAcyclic(boolean[][] adj, boolean[] reach, boolean ignoreSelfLoops) {
    int n = adj.length;
    int[] indeg = new int[n];
    int cnt = 0;
    for (int u = 0; u < n; u++) {
      if (!reach[u]) continue;
      cnt++;
      for (int v = 0; v < n; v++) {
        if (adj[u][v] && reach[v] && !(ignoreSelfLoops && u == v)) indeg[v]++;
      }
    }
    ArrayDeque<Integer> q = new ArrayDeque<>();
    for (int u = 0; u < n; u++) if (reach[u] && indeg[u] == 0) q.add(u);
    int removed = 0;
    while (!q.isEmpty()) {
      int u = q.poll();
      removed++;
      for (int v = 0; v < n; v++) {
        if (adj[u][v] && reach[v] && !(ignoreSelfLoops && u == v)) {
          if (--indeg[v] == 0) q.add(v);
        }
      }
    }
    return removed == cnt;
  }

  /** out must list exactly the reachable states, each once, with every edge going forward. */
  private static boolean validTopo(boolean[][] adj, boolean[] reach, int[] out) {
    int n = adj.length;
    int[] pos = new int[n];
    Arrays.fill(pos, -1);
    for (int i = 0; i < out.length; i++) {
      if (out[i] < 0 || out[i] >= n || pos[out[i]] != -1 || !reach[out[i]]) return false;
      pos[out[i]] = i;
    }
    for (int u = 0; u < n; u++) {
      if (reach[u] && pos[u] == -1) return false;
    }
    for (int u = 0; u < n; u++) {
      if (!reach[u]) continue;
      for (int v = 0; v < n; v++) {
        if (adj[u][v] && pos[v] <= pos[u]) return false;
      }
    }
    return true;
  }

  /**
   * Copy of main's Operations.topoSortStates with the minimal fix: the initial state is marked
   * visited+onStack before the loop, and every pushed state is marked onStack when pushed (instead
   * of marking the parent when it pushes a child).
   */
  static int[] fixedTopoSortStates(Automaton a) {
    if (a.getNumStates() == 0) {
      return new int[0];
    }
    int[] states = new int[a.getNumStates()];
    BitSet onStack = new BitSet(a.getNumStates());
    BitSet visited = new BitSet(a.getNumStates());
    var stack = new ArrayDeque<Integer>();
    stack.push(0);
    visited.set(0);
    onStack.set(0);
    int upto = 0;
    Transition t = new Transition();
    while (!stack.isEmpty()) {
      int state = stack.peek();
      int count = a.initTransition(state, t);
      boolean pushed = false;
      for (int i = 0; i < count; i++) {
        a.getNextTransition(t);
        if (!visited.get(t.dest)) {
          visited.set(t.dest);
          onStack.set(t.dest);
          stack.push(t.dest);
          pushed = true;
          break;
        } else if (onStack.get(t.dest)) {
          throw new IllegalArgumentException("Input automaton has a cycle.");
        }
      }
      if (!pushed) {
        onStack.clear(state);
        stack.pop();
        states[upto++] = state;
      }
    }
    states = Arrays.copyOf(states, upto);
    for (int i = 0; i < states.length / 2; i++) {
      int s = states[i];
      states[i] = states[states.length - 1 - i];
      states[states.length - 1 - i] = s;
    }
    return states;
  }
}
