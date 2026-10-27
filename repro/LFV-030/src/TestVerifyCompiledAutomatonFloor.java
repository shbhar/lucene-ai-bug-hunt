import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.CompiledAutomaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;

/**
 * Independent verification of the finding (CompiledAutomaton.floor on an infinite language).
 *
 * <p>The automata are built by hand (binary/byte DFAs, isBinary=true) so nothing depends on RegExp
 * parsing or UTF-8 conversion, plus one RegExp/unicode-path variant.
 */
public class TestVerifyCompiledAutomatonFloor extends LuceneTestCase {

  /** L = b a* as a 2-state byte DFA: s0 -b-> s1(accept), s1 -a-> s1. */
  private static Automaton bAStar() {
    Automaton a = new Automaton();
    int s0 = a.createState();
    int s1 = a.createState();
    a.setAccept(s1, true);
    a.addTransition(s0, s1, 'b');
    a.addTransition(s1, s1, 'a');
    a.finishState();
    return a;
  }

  /** L = b a* c as a 3-state byte DFA: infinite, but floor("bb") = "bac" exists. */
  private static Automaton bAStarC() {
    Automaton a = new Automaton();
    int s0 = a.createState();
    int s1 = a.createState();
    int s2 = a.createState();
    a.setAccept(s2, true);
    a.addTransition(s0, s1, 'b');
    a.addTransition(s1, s1, 'a');
    a.addTransition(s1, s2, 'c');
    a.finishState();
    return a;
  }

  private static String floorOf(CompiledAutomaton ca, String in) {
    BytesRef r = ca.floor(new BytesRef(in), new BytesRefBuilder());
    return r == null ? null : r.utf8ToString();
  }

  /** Control 1: finite language {b, ba, baa}: floor works. */
  public void testControlFinite() {
    Automaton a = Automata.makeStringUnion(
        java.util.List.of(new BytesRef("b"), new BytesRef("ba"), new BytesRef("baa")));
    CompiledAutomaton ca = new CompiledAutomaton(a, true, false, true);
    assertEquals(CompiledAutomaton.AUTOMATON_TYPE.NORMAL, ca.type);
    assertEquals("baa", floorOf(ca, "bb"));
    assertEquals("ba", floorOf(ca, "ba"));
    assertNull(floorOf(ca, "a"));
  }

  /** Control 2: infinite language b a* c, floor exists and is returned (cycle not on the max path). */
  public void testControlInfiniteButFloorExists() {
    CompiledAutomaton ca = new CompiledAutomaton(bAStarC(), false, false, true);
    assertEquals(CompiledAutomaton.AUTOMATON_TYPE.NORMAL, ca.type);
    assertFalse(ca.finite);
    assertEquals("bac", floorOf(ca, "bb"));
    assertEquals("bac", floorOf(ca, "bac"));
    assertNull(floorOf(ca, "ba"));
  }

  /** Control 3: infinite language b a*, inputs whose floor exists. */
  public void testControlBAStarFloorExists() {
    CompiledAutomaton ca = new CompiledAutomaton(bAStar(), false, false, true);
    assertEquals("b", floorOf(ca, "b")); // accepted input
    assertEquals("baa", floorOf(ca, "baa")); // accepted input
    assertEquals("b", floorOf(ca, "b\u0001")); // b < b\001 < ba : floor is "b"
    assertNull(floorOf(ca, "a")); // input before first term
  }

  /**
   * The candidate: L = b a*, input "bb". {t in L : t <= "bb"} = {b, ba, baa, ...} is non-empty and
   * has no maximum. floor() should return (null, or some value) or throw a clear exception; it
   * instead grows the output term one 'a' at a time until the heap or the max array length is hit.
   */
  public void testBAStarFloorOfBB() throws Exception {
    runNoFloorCase(new CompiledAutomaton(bAStar(), false, false, true), "bb", "byte-DFA b a*");
  }

  /** Same, via the ordinary unicode path: new CompiledAutomaton(new RegExp("ba*").toAutomaton()). */
  public void testRegExpBAStarFloorOfBB() throws Exception {
    Automaton a = new RegExp("ba*").toAutomaton();
    CompiledAutomaton ca = new CompiledAutomaton(a);
    assertEquals(CompiledAutomaton.AUTOMATON_TYPE.NORMAL, ca.type);
    runNoFloorCase(ca, "bb", "RegExp(\"ba*\") unicode path");
  }

  private void runNoFloorCase(CompiledAutomaton ca, String input, String label)
      throws Exception {
    assertEquals(CompiledAutomaton.AUTOMATON_TYPE.NORMAL, ca.type);
    final BytesRefBuilder out = new BytesRefBuilder();
    final AtomicBoolean done = new AtomicBoolean(false);
    final AtomicInteger maxCap = new AtomicInteger(0);
    final StringBuilder samples = new StringBuilder();
    // Watcher: samples the capacity of the output buffer (racy read, indicative only).
    Thread watcher =
        new Thread(
            () -> {
              long t0 = System.nanoTime();
              long next = 0;
              while (done.get() == false) {
                int cap = out.bytes().length;
                maxCap.accumulateAndGet(cap, Math::max);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                if (ms >= next) {
                  synchronized (samples) {
                    samples.append(String.format("    t=%5d ms  output capacity=%,d bytes%n", ms, cap));
                  }
                  next += 1000;
                }
                try {
                  Thread.sleep(5);
                } catch (InterruptedException e) {
                  return;
                }
              }
            });
    watcher.setDaemon(true);
    watcher.start();
    long start = System.nanoTime();
    BytesRef result = null;
    Throwable thrown = null;
    try {
      result = ca.floor(new BytesRef(input), out);
    } catch (Throwable t) {
      thrown = t;
    } finally {
      done.set(true);
      watcher.join();
    }
    long ms = (System.nanoTime() - start) / 1_000_000;
    String summary =
        String.format(
            "%s floor(\"%s\"): elapsed=%d ms, result=%s, thrown=%s, max sampled output capacity=%,d bytes",
            label,
            input,
            ms,
            result == null ? "null" : ("len=" + result.length),
            thrown == null ? "none" : (thrown.getClass().getName() + ": " + thrown.getMessage()),
            maxCap.get());
    System.out.println(summary);
    System.out.print(samples);
    if (thrown != null) {
      // print the top frames to locate the loop
      StackTraceElement[] st = thrown.getStackTrace();
      for (int i = 0; i < Math.min(6, st.length); i++) {
        System.out.println("      at " + st[i]);
      }
    }
    // Expected per javadoc-ish contract: return null or a term <= input, promptly; or clear error.
    assertNull("floor threw instead of returning: " + summary, thrown);
    assertTrue(
        "floor returned a term > input: " + summary,
        result == null || result.compareTo(new BytesRef(input)) <= 0);
  }

  /**
   * Aside (not the candidate): floor() on an NFA-backed CompiledAutomaton. runAutomaton is null on
   * that path, so we just record what happens.
   */
  public void testAsideNfaBacked() {
    Automaton nfa = Operations.union(java.util.List.of(Automata.makeString("ab"), Automata.makeString("ac")));
    CompiledAutomaton ca = new CompiledAutomaton(nfa, true, false, true);
    System.out.println(
        "NFA-backed: isDeterministic=" + nfa.isDeterministic() + " runAutomaton=" + ca.runAutomaton);
    try {
      String r = floorOf(ca, "ad");
      System.out.println("NFA-backed floor(\"ad\") = " + r);
    } catch (Throwable t) {
      System.out.println("NFA-backed floor(\"ad\") threw " + t);
    }
  }
}
