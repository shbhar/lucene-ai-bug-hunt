import org.apache.lucene.util.automaton.CharacterRunAutomaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;

public class EscapedRangeStart {
  public static void main(String[] args) {
    for (String regex : new String[] {"[.-z]", "[\\.-z]"}) { // regex texts: [.-z] and [\.-z]
      RegExp re = new RegExp(regex);
      CharacterRunAutomaton run =
          new CharacterRunAutomaton(
              Operations.determinize(re.toAutomaton(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT));
      System.out.println(
          regex + "  a=" + run.run("a") + "  /=" + run.run("/") + "  -=" + run.run("-")
              + "  tree: " + re.toStringTree().trim());
    }
  }
}
