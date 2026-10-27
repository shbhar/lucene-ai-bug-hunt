import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;

/** The Kleene star of the empty language. Public API of lucene-core only. */
public class KleeneStarOfEmpty {
  static String show(String label, Automaton a, String input) {
    Automaton d = Operations.determinize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
    return String.format("%-28s states=%d  accepts \"%s\": %s", label, a.getNumStates(), input, Operations.run(d, input));
  }

  public static void main(String[] args) {
    Automaton empty0 = Automata.makeEmpty(); // the empty language, 0 states
    Automaton empty1 = new Automaton(); // the empty language, 1 non-accepting state
    empty1.createState();
    empty1.finishState();
    System.out.println(show("repeat(makeEmpty())", Operations.repeat(empty0), ""));
    System.out.println(show("repeat(1-state empty)", Operations.repeat(empty1), ""));
    System.out.println(show("optional(makeEmpty())", Operations.optional(empty0), ""));
    String[][] cases = {
      {"#*", ""}, {"(a&b)*", ""}, {"(#){0,}", ""}, {"(a&b)?", ""}, {"(a&b){0,5}", ""},
      {"a(a&b)*", "a"}, {"a(a&b)?", "a"}
    };
    for (String[] c : cases) {
      System.out.println(show("RegExp " + c[0], new RegExp(c[0]).toAutomaton(), c[1]));
    }
  }
}
