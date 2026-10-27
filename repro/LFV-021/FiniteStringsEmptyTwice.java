import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.FiniteStringsIterator;
import org.apache.lucene.util.automaton.LimitedFiniteStringsIterator;
import org.apache.lucene.util.automaton.RegExp;

/** FiniteStringsIterator returns the empty string twice. Public API of lucene-core only. */
public class FiniteStringsEmptyTwice {
  static List<String> drain(FiniteStringsIterator it) {
    List<String> out = new ArrayList<>();
    for (IntsRef r; (r = it.next()) != null; ) {
      out.add('"' + new String(r.ints, r.offset, r.length) + '"');
    }
    return out;
  }

  static void show(String label, Automaton a) {
    List<String> got = drain(new FiniteStringsIterator(a));
    System.out.printf(
        "%-32s deterministic=%-5s count=%d %s%n",
        label, a.isDeterministic(), got.size(), got);
  }

  public static void main(String[] args) {
    show("RegExp a?", new RegExp("a?").toAutomaton());
    show("RegExp (ab)?", new RegExp("(ab)?").toAutomaton());
    show(
        "makeStringUnion [\"\", a, ab, b]",
        Automata.makeStringUnion(
            List.of(new BytesRef(""), new BytesRef("a"), new BytesRef("ab"), new BytesRef("b"))));

    LimitedFiniteStringsIterator limited =
        new LimitedFiniteStringsIterator(new RegExp("a?").toAutomaton(), -1);
    drain(limited);
    System.out.println("LimitedFiniteStringsIterator(a?, -1).size() = " + limited.size());

    // Controls: no empty string, or an accepting start state without transitions.
    show("control: RegExp a|b", new RegExp("a|b").toAutomaton());
    show("control: makeEmptyString()", Automata.makeEmptyString());
  }
}
