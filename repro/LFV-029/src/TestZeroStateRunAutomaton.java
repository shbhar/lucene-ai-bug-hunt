import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Supplier;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.ByteRunAutomaton;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;
import org.apache.lucene.util.automaton.CompiledAutomaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;

/**
 * Independent verification of the finding: RunAutomaton on a 0-state automaton.
 *
 * <p>Every assertion states the EXPECTED behaviour: a 0-state automaton is deterministic and
 * accepts nothing, so a RunAutomaton built from it should simply reject every input. A test that
 * fails therefore reproduces the candidate bug. Controls are expected to pass.
 */
public class TestZeroStateRunAutomaton extends LuceneTestCase {

  /** Length of the private Automaton.states array (diagnostic only; classpath => reflection ok). */
  private static int statesArrayLength(Automaton a) throws Exception {
    Field f = Automaton.class.getDeclaredField("states");
    f.setAccessible(true);
    return ((int[]) f.get(a)).length;
  }

  private static void describe(String label, Automaton a) throws Exception {
    System.out.println(
        "[diag] "
            + label
            + ": numStates="
            + a.getNumStates()
            + " deterministic="
            + a.isDeterministic()
            + " Operations.isEmpty="
            + Operations.isEmpty(a)
            + " states.length="
            + statesArrayLength(a));
  }

  private static void assertRejectsEverything(String label, Automaton a) throws Exception {
    describe(label, a);
    assertEquals(label + ": precondition, 0 states", 0, a.getNumStates());
    assertTrue(label + ": precondition, deterministic", a.isDeterministic());
    CharacterRunAutomaton cra;
    try {
      cra = new CharacterRunAutomaton(a);
    } catch (RuntimeException e) {
      System.out.println("[diag] " + label + ": new CharacterRunAutomaton threw " + e);
      StackTraceElement[] st = e.getStackTrace();
      for (int i = 0; i < Math.min(4, st.length); i++) {
        System.out.println("[diag]     at " + st[i]);
      }
      throw e;
    }
    assertFalse(label, cra.run(""));
    assertFalse(label, cra.run("x"));
    assertFalse(label, cra.run("😀"));
  }

  // ---------------------------------------------------------------- controls (expected to pass)

  /** Control: the canonical empty automaton has spare (default) capacity and works. */
  public void testControlMakeEmpty() throws Exception {
    assertRejectsEverything("Automata.makeEmpty()", Automata.makeEmpty());
    ByteRunAutomaton bra = new ByteRunAutomaton(Automata.makeEmpty());
    assertFalse(bra.run(new byte[0], 0, 0));
  }

  /** Control: new Automaton() (no-arg ctor, "creates an automaton with no states") works. */
  public void testControlNoArgCtor() throws Exception {
    Automaton a = new Automaton();
    a.finishState();
    assertRejectsEverything("new Automaton() + finishState()", a);
  }

  /** Control: CompiledAutomaton replaces a 0-state automaton before building any RunAutomaton. */
  public void testControlCompiledAutomatonDefends() throws Exception {
    Automaton a = new Automaton.Builder().finish();
    CompiledAutomaton ca = new CompiledAutomaton(a);
    assertEquals(CompiledAutomaton.AUTOMATON_TYPE.NONE, ca.type);
    CompiledAutomaton ca2 = new CompiledAutomaton(a, true, false, false);
    assertNotNull(ca2.runAutomaton);
    assertFalse(ca2.runAutomaton.run(new byte[] {'x'}, 0, 1));
  }

  // ------------------------------------------- candidate triggers (fail = bug reproduced)

  /** Exact-size 0-state automaton from the public Builder. */
  public void testBuilderFinishNoStates() throws Exception {
    assertRejectsEverything("new Automaton.Builder().finish()", new Automaton.Builder().finish());
  }

  /** Exact-size 0-state automaton from the public sizing constructor. */
  public void testSizedCtorZero() throws Exception {
    Automaton a = new Automaton(0, 0);
    a.finishState();
    assertRejectsEverything("new Automaton(0, 0) + finishState()", a);
  }

  /** Same automaton through ByteRunAutomaton, binary (no conversion) path. */
  public void testByteRunAutomatonBinary() throws Exception {
    Automaton a = new Automaton.Builder().finish();
    describe("Builder().finish() for ByteRunAutomaton(a, true)", a);
    ByteRunAutomaton bra = new ByteRunAutomaton(a, true);
    assertFalse(bra.run(new byte[] {'x'}, 0, 1));
  }

  /** Reached from a public RegExp: an empty character class repeated twice. */
  public void testRegExpEmptyClassRepeated() throws Exception {
    Automaton a = new RegExp("([^\\D\\d]){2}").toAutomaton();
    assertRejectsEverything("RegExp(\"([^\\\\D\\\\d]){2}\").toAutomaton()", a);
  }

  /**
   * Survey (not an assertion of the bug): which public operations yield a 0-state automaton, how
   * big its internal states[] is, and whether new CharacterRunAutomaton(...) succeeds. Prints a
   * table; always passes.
   */
  public void testSurvey() throws Exception {
    List<Object[]> cases =
        List.of(
            new Object[] {"Automata.makeEmpty()", (Supplier<Automaton>) Automata::makeEmpty},
            new Object[] {
              "new Automaton.Builder().finish()",
              (Supplier<Automaton>) () -> new Automaton.Builder().finish()
            },
            new Object[] {
              "RegExp(\"[^\\\\D\\\\d]\")",
              (Supplier<Automaton>) () -> new RegExp("[^\\D\\d]").toAutomaton()
            },
            new Object[] {
              "RegExp(\"([^\\\\D\\\\d]){2}\")",
              (Supplier<Automaton>) () -> new RegExp("([^\\D\\d]){2}").toAutomaton()
            },
            new Object[] {
              "RegExp(\"[^\\\\D\\\\d]a\")",
              (Supplier<Automaton>) () -> new RegExp("[^\\D\\d]a").toAutomaton()
            },
            new Object[] {
              "RegExp(\"a[^\\\\D\\\\d]\")",
              (Supplier<Automaton>) () -> new RegExp("a[^\\D\\d]").toAutomaton()
            },
            new Object[] {
              "RegExp(\"#\")", (Supplier<Automaton>) () -> new RegExp("#").toAutomaton()
            },
            new Object[] {
              "RegExp(\"a&b\")", (Supplier<Automaton>) () -> new RegExp("a&b").toAutomaton()
            },
            new Object[] {
              "RegExp(\"(a&b){2}\")",
              (Supplier<Automaton>) () -> new RegExp("(a&b){2}").toAutomaton()
            },
            new Object[] {
              "Operations.repeat(makeEmpty(),2,2)",
              (Supplier<Automaton>) () -> Operations.repeat(Automata.makeEmpty(), 2, 2)
            },
            new Object[] {
              "Operations.concatenate(makeEmpty(),makeChar('a'))",
              (Supplier<Automaton>)
                  () -> Operations.concatenate(List.of(Automata.makeEmpty(), Automata.makeChar('a')))
            },
            new Object[] {
              "Operations.intersection(makeChar('a'),makeChar('b'))",
              (Supplier<Automaton>)
                  () -> Operations.intersection(Automata.makeChar('a'), Automata.makeChar('b'))
            },
            new Object[] {
              "Operations.union(List.of())",
              (Supplier<Automaton>) () -> Operations.union(List.<Automaton>of())
            },
            new Object[] {
              "Operations.reverse(makeEmpty())",
              (Supplier<Automaton>) () -> Operations.reverse(Automata.makeEmpty())
            },
            new Object[] {
              "Operations.determinize(RegExp(\"([^\\\\D\\\\d]){2}\"))",
              (Supplier<Automaton>)
                  () ->
                      Operations.determinize(
                          new RegExp("([^\\D\\d]){2}").toAutomaton(),
                          Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
            });
    for (Object[] c : cases) {
      String label = (String) c[0];
      @SuppressWarnings("unchecked")
      Supplier<Automaton> s = (Supplier<Automaton>) c[1];
      Automaton a;
      try {
        a = s.get();
      } catch (RuntimeException e) {
        System.out.println("[survey] " + label + " -> construction threw " + e);
        continue;
      }
      String cra;
      if (a.isDeterministic() == false) {
        cra = "n/a (not deterministic)";
      } else {
        try {
          CharacterRunAutomaton r = new CharacterRunAutomaton(a);
          cra = "OK run(\"\")=" + r.run("") + " run(\"a\")=" + r.run("a");
        } catch (RuntimeException e) {
          cra = "THREW " + e;
        }
      }
      System.out.println(
          "[survey] "
              + label
              + " -> numStates="
              + a.getNumStates()
              + " det="
              + a.isDeterministic()
              + " states.length="
              + statesArrayLength(a)
              + " | new CharacterRunAutomaton: "
              + cra);
    }
  }
}
