import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.FiniteStringsIterator;
import org.apache.lucene.util.automaton.LimitedFiniteStringsIterator;
import org.apache.lucene.util.automaton.Operations;

/**
 * Independent check of the finding: does FiniteStringsIterator emit the empty string twice
 * for a deterministic automaton whose initial state is accepting and has outgoing transitions?
 *
 * <p>Each case prints the emitted sequence so the output is self-describing, then asserts that
 * the sequence has no duplicates (the class javadoc only allows duplicates for non-determinized
 * automata).
 */
public class TestFSIEmptyStringDup extends LuceneTestCase {

  private static List<String> drain(FiniteStringsIterator it) {
    List<String> out = new ArrayList<>();
    for (IntsRef r; (r = it.next()) != null; ) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < r.length; i++) {
        sb.appendCodePoint(r.ints[r.offset + i]);
      }
      out.add(sb.toString());
    }
    return out;
  }

  private static String show(List<String> l) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < l.size(); i++) {
      if (i > 0) sb.append(", ");
      sb.append('"').append(l.get(i)).append('"');
    }
    return sb.append("] size=").append(l.size()).toString();
  }

  private static void check(String name, Automaton a, int expectedDistinct) {
    List<String> got = drain(new FiniteStringsIterator(a));
    System.out.println(
        name
            + ": isDeterministic="
            + a.isDeterministic()
            + " numStates="
            + a.getNumStates()
            + " isAccept(0)="
            + a.isAccept(0)
            + " numTransitions(0)="
            + a.getNumTransitions(0)
            + " -> "
            + show(got));
    assertEquals(name + " distinct strings", expectedDistinct, new HashSet<>(got).size());
    assertEquals(name + " no duplicates expected for a deterministic automaton", expectedDistinct, got.size());
  }

  /** Case 1: hand-built 2-state DFA: 0 (accept) --'a'--> 1 (accept). Language {"", "a"}. */
  public void testHandBuiltDfa() {
    Automaton a = new Automaton();
    int s0 = a.createState();
    int s1 = a.createState();
    a.setAccept(s0, true);
    a.setAccept(s1, true);
    a.addTransition(s0, s1, 'a');
    a.finishState();
    assertTrue(a.isDeterministic());
    check("handBuilt{'',a}", a, 2);
  }

  /** Case 2: Automata.makeStringUnion (documented deterministic and minimal) of {"", "a"}. */
  public void testStringUnion() {
    Automaton a = Automata.makeStringUnion(List.of(new BytesRef(""), new BytesRef("a")));
    check("makeStringUnion{'',a}", a, 2);
  }

  /** Case 3: optional("ab") determinized: language {"", "ab"}. */
  public void testOptionalDeterminized() {
    Automaton a =
        Operations.determinize(
            Operations.optional(Automata.makeString("ab")),
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
    check("determinize(optional(ab))", a, 2);
  }

  /** Control A: empty-string-only automaton (state 0 accepting, no transitions). */
  public void testControlEmptyStringOnly() {
    check("control makeEmptyString", Automata.makeEmptyString(), 1);
  }

  /** Control B: initial state not accepting: {"a", "b"}. */
  public void testControlNoEmptyString() {
    Automaton a = Automata.makeStringUnion(List.of(new BytesRef("a"), new BytesRef("b")));
    check("control makeStringUnion{a,b}", a, 2);
  }

  /** LimitedFiniteStringsIterator: size() counts the duplicate. */
  public void testLimitedCountsDuplicate() {
    Automaton a = Automata.makeStringUnion(List.of(new BytesRef(""), new BytesRef("a")));
    LimitedFiniteStringsIterator it = new LimitedFiniteStringsIterator(a, -1);
    List<String> got = drain(it);
    System.out.println("Limited(-1) makeStringUnion{'',a}: " + show(got) + " it.size()=" + it.size());
    assertEquals("Limited size() should equal number of accepted strings", 2, it.size());
  }
}
