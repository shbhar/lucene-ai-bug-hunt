import java.util.regex.Pattern;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.automaton.AutomatonTestUtil;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;

/**
 * Independent verification of the finding: in a RegExp character class, a range whose
 * START endpoint is backslash-escaped ([\.-z]) is no longer parsed as a range.
 *
 * <p>Oracle 1: the RegExp class javadoc grammar (charclass ::= charexp '-' charexp, charexp ::=
 * ... | '\' Unicode character). Oracle 2: java.util.regex.Pattern. Oracle 3: Lucene's own
 * unescaped spelling of the same range.
 */
public class TestEscapedRangeStartInCharClass extends LuceneTestCase {

  private static CharacterRunAutomaton lucene(String regex) {
    Automaton a = new RegExp(regex).toAutomaton();
    return new CharacterRunAutomaton(Operations.determinize(a, 10_000));
  }

  /** Control: unescaped range start parses as a range (passes on main). */
  public void testUnescapedRangeStartControl() {
    CharacterRunAutomaton r = lucene("[.-z]");
    assertTrue(r.run("a"));
    assertTrue(r.run("."));
    assertTrue(r.run("z"));
    assertFalse(r.run("-"));
  }

  /** Control: escaped range END parses as a range (passes on main). */
  public void testEscapedRangeEndControl() {
    CharacterRunAutomaton r = lucene("[a-\\~]"); // regex text: [a-\~]
    assertTrue(r.run("m"));
    assertTrue(r.run("~"));
    assertFalse(r.run("-"));
  }

  /** The candidate bug: escaped range START. */
  public void testEscapedRangeStart() {
    String regex = "[\\.-z]"; // regex text: [\.-z]
    // java.util.regex agrees with the javadoc grammar: '.'..'z'
    assertTrue(Pattern.matches(regex, "a"));
    assertFalse(Pattern.matches(regex, "-"));
    CharacterRunAutomaton r = lucene(regex);
    String tree = new RegExp(regex).toStringTree();
    assertTrue("[\\.-z] must match 'a' (range '.'..'z'); parse tree:\n" + tree, r.run("a"));
    assertFalse("[\\.-z] must NOT match '-'; parse tree:\n" + tree, r.run("-"));
  }

  /** Same language as the unescaped spelling of the range. */
  public void testEscapedAndUnescapedStartSameLanguage() {
    Automaton esc = new RegExp("[\\.-z]").toAutomaton();
    Automaton plain = new RegExp("[.-z]").toAutomaton();
    assertTrue(
        "[\\.-z] and [.-z] should be the same language",
        AutomatonTestUtil.sameLanguage(
            Operations.determinize(esc, 10_000), Operations.determinize(plain, 10_000)));
  }

  /**
   * Exhaustive differential over all printable-ASCII non-letter start chars s and all printable
   * ASCII end chars e >= s: Lucene "[\s-e]" vs java.util.regex "[\s-e]", probed on all printable
   * ASCII single chars. (ASCII letters are excluded as starts because "\a" etc. are reserved
   * escapes in both engines.)
   */
  public void testExhaustiveAsciiDifferential() {
    int pairs = 0, mismatchedPairs = 0;
    String firstMismatch = null;
    for (int s = 0x21; s <= 0x7E; s++) {
      if (Character.isLetter(s)) continue;
      for (int e = s; e <= 0x7E; e++) {
        if (e == ']' || e == '\\' || e == '[' || e == '&') continue; // keep both parsers simple
        String regex = "[\\" + (char) s + "-" + (char) e + "]";
        CharacterRunAutomaton r;
        Pattern p;
        try {
          r = lucene(regex);
          p = Pattern.compile(regex);
        } catch (IllegalArgumentException ex) {
          continue;
        }
        pairs++;
        for (int probe = 0x20; probe <= 0x7E; probe++) {
          String in = String.valueOf((char) probe);
          if (r.run(in) != p.matcher(in).matches()) {
            mismatchedPairs++;
            if (firstMismatch == null) {
              firstMismatch =
                  regex + " probe='" + in + "' lucene=" + r.run(in) + " jdk=" + p.matcher(in).matches();
            }
            break;
          }
        }
      }
    }
    System.out.println(
        "ASCII differential: " + mismatchedPairs + "/" + pairs + " escaped-start ranges disagree"
            + " with java.util.regex; first: " + firstMismatch);
    assertEquals("first mismatch: " + firstMismatch, 0, mismatchedPairs);
  }

  /**
   * Consequence: RegExp.toString() escapes non-letter range endpoints (escapeCharIfNeeded), so
   * the printed form of [0-9] is [\0-\9], which no longer re-parses to [0-9].
   */
  public void testToStringRoundTripOfRange() {
    RegExp orig = new RegExp("[0-9]");
    String printed = orig.toString();
    System.out.println("toString of [0-9] = " + printed);
    Automaton a1 = Operations.determinize(orig.toAutomaton(), 10_000);
    Automaton a2 = Operations.determinize(new RegExp(printed).toAutomaton(), 10_000);
    assertTrue(
        "re-parse of toString() '" + printed + "' changed the language; tree:\n"
            + new RegExp(printed).toStringTree(),
        AutomatonTestUtil.sameLanguage(a1, a2));
  }
}
