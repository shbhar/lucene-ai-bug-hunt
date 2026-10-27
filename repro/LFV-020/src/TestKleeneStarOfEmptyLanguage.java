import java.util.regex.Pattern;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.automaton.AutomatonTestUtil;
import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;

/**
 * Independent verification of the finding: Kleene star of the empty language.
 *
 * <p>Math: for any language L, L* = {""} U L U LL U ... , so the star of the empty language is
 * {""}. Lucene documents Operations.repeat(Automaton) as "the Kleene star (zero or more concatenated
 * repetitions)" and RegExp '*' as "zero or more occurrences".
 *
 * <p>The oracles used here are (1) the definition, (2) other Lucene operations that must agree with
 * repeat() (optional, bounded repeat, a different representation of the same empty language) and
 * (3) java.util.regex for a pattern with an empty character class.
 */
public class TestKleeneStarOfEmptyLanguage extends LuceneTestCase {

  private static boolean acceptsEmptyString(Automaton a) {
    // Operations.run and CharacterRunAutomaton both require a DFA.
    Automaton d = Operations.determinize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
    boolean viaRun = Operations.run(d, "");
    boolean viaRunAutomaton = new CharacterRunAutomaton(d).run("");
    assertEquals("Operations.run and CharacterRunAutomaton disagree", viaRun, viaRunAutomaton);
    return viaRun;
  }

  /** One-state, non-accepting, transition-free automaton: also the empty language. */
  private static Automaton emptyLanguageWithOneDeadState() {
    Automaton a = new Automaton();
    a.createState();
    a.finishState();
    return a;
  }

  // ---------------------------------------------------------------- diagnostics (always pass)

  public void testPrintTable() {
    Automaton empty = Automata.makeEmpty();
    System.out.println("makeEmpty().getNumStates() = " + empty.getNumStates());
    System.out.println(
        "repeat(makeEmpty()) same object as input = " + (Operations.repeat(empty) == empty));
    System.out.println(
        "intersection(a,b).getNumStates() = "
            + Operations.intersection(Automata.makeChar('a'), Automata.makeChar('b'))
                .getNumStates());
    String[] patterns = {
      "#", "#?", "#*", "#+", "(#){0,}", "(#){0,5}", "(#){0,1}", "(a&b)", "(a&b)?", "(a&b)*",
      "(a&b){0,3}", "[^\\s\\S]*", "()", "()*", "a*", "(#|())*"
    };
    for (String p : patterns) {
      Automaton a = new RegExp(p).toAutomaton();
      String jdk = "-";
      if (p.startsWith("[^\\s\\S]")) {
        jdk = Boolean.toString(Pattern.compile(p).matcher("").matches());
      }
      System.out.printf(
          "RegExp %-12s numStates=%d acceptsEmptyString=%s  java.util.regex=%s%n",
          p, a.getNumStates(), acceptsEmptyString(a), jdk);
    }
  }

  // ---------------------------------------------------------------- definition

  public void testStarOfEmptyLanguageContainsEmptyString() {
    Automaton star = Operations.repeat(Automata.makeEmpty());
    assertTrue("Kleene star of {} must accept \"\"", acceptsEmptyString(star));
  }

  public void testRepeatMinZeroOfEmptyLanguageContainsEmptyString() {
    // repeat(a, 0) is documented as "min or more concatenated repetitions" -> 0 repetitions = ""
    Automaton a = Operations.repeat(Automata.makeEmpty(), 0);
    assertTrue("repeat({}, 0) must accept \"\"", acceptsEmptyString(a));
  }

  // ---------------------------------------------------------------- internal consistency oracles

  public void testRepeatIsAFunctionOfTheLanguage() {
    Automaton e0 = Automata.makeEmpty(); // 0 states
    Automaton e1 = emptyLanguageWithOneDeadState(); // 1 non-accepting state
    assertEquals(0, e0.getNumStates());
    assertEquals(1, e1.getNumStates());
    assertTrue("precondition: both inputs are the empty language", Operations.isEmpty(e0));
    assertTrue("precondition: both inputs are the empty language", Operations.isEmpty(e1));
    // (AutomatonTestUtil.sameLanguage needs dead-state-free DFAs, so compare canonical forms.)
    assertTrue(AutomatonTestUtil.sameLanguage(canon(e0), canon(e1)));
    // The result of a language operation must not depend on the representation of its input.
    Automaton s0 = Operations.repeat(e0);
    Automaton s1 = Operations.repeat(e1);
    System.out.println(
        "repeat(0-state empty) accepts \"\": "
            + acceptsEmptyString(s0)
            + " ; repeat(1-state empty) accepts \"\": "
            + acceptsEmptyString(s1));
    assertTrue(
        "repeat() gives different languages for two representations of {}",
        AutomatonTestUtil.sameLanguage(canon(s0), canon(s1)));
  }

  private static Automaton canon(Automaton a) {
    return Operations.removeDeadStates(
        Operations.determinize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT));
  }

  public void testOptionalIsSubsetOfStar() {
    // L? = {""} U L  is always a subset of L*
    Automaton empty = Automata.makeEmpty();
    Automaton opt = Operations.optional(empty);
    Automaton star = Operations.repeat(empty);
    assertTrue("optional({}) accepts \"\" (control)", acceptsEmptyString(opt));
    assertTrue("L? must be a subset of L*", AutomatonTestUtil.subsetOf(opt, star));
  }

  public void testBoundedRepeatIsSubsetOfStar() {
    // L{0,3} is always a subset of L*
    Automaton empty = Automata.makeEmpty();
    Automaton bounded = Operations.repeat(empty, 0, 3);
    Automaton star = Operations.repeat(empty);
    assertTrue("repeat({},0,3) accepts \"\" (control)", acceptsEmptyString(bounded));
    assertTrue("L{0,3} must be a subset of L*", AutomatonTestUtil.subsetOf(bounded, star));
  }

  // ---------------------------------------------------------------- RegExp + java.util.regex

  public void testRegExpEmptyCharClassStarMatchesJavaRegex() {
    String p = "[^\\s\\S]*"; // empty character class, starred: same meaning in both syntaxes
    boolean jdk = Pattern.compile(p).matcher("").matches();
    boolean lucene = acceptsEmptyString(new RegExp(p).toAutomaton());
    assertTrue("control: java.util.regex accepts \"\"", jdk);
    assertEquals("Lucene RegExp disagrees with java.util.regex on \"\"", jdk, lucene);
  }

  public void testRegExpStarAgreesWithBoundedForms() {
    // '#?' and '(#){0,5}' accept "" in Lucene; '#*' and '(#){0,}' must too.
    assertTrue("control", acceptsEmptyString(new RegExp("#?").toAutomaton()));
    assertTrue("control", acceptsEmptyString(new RegExp("(#){0,5}").toAutomaton()));
    assertTrue("'#*' must accept \"\"", acceptsEmptyString(new RegExp("#*").toAutomaton()));
    assertTrue(
        "'(#){0,}' must accept \"\"", acceptsEmptyString(new RegExp("(#){0,}").toAutomaton()));
  }

  public void testConcatenationAmplifiesToEmptyLanguage() {
    // X* concatenated with "abc" must still accept "abc" (zero repetitions of X). Because repeat()
    // returns the empty language, the whole concatenation collapses to the empty language.
    String p = "abc[^\\s\\S]*";
    boolean jdk = Pattern.compile(p).matcher("abc").matches();
    assertTrue("control: java.util.regex accepts \"abc\"", jdk);
    Automaton a = new RegExp(p).toAutomaton();
    System.out.println(p + " -> numStates=" + a.getNumStates() + " accepts abc=" + run(a, "abc"));
    assertEquals("Lucene RegExp disagrees with java.util.regex on \"abc\"", jdk, run(a, "abc"));
    assertTrue("'abc(a&b)*' must accept \"abc\"", run(new RegExp("abc(a&b)*").toAutomaton(), "abc"));
  }

  private static boolean run(Automaton a, String s) {
    return Operations.run(Operations.determinize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT), s);
  }

  // ---------------------------------------------------------------- end to end: RegexpQuery

  public void testRegexpQueryOnEmptyTerm() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document d1 = new Document();
      d1.add(new StringField("f", "", Field.Store.NO)); // the empty term
      w.addDocument(d1);
      Document d2 = new Document();
      d2.add(new StringField("f", "a", Field.Store.NO));
      w.addDocument(d2);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        int cEmptyStr = s.count(new RegexpQuery(new Term("f", "()")));
        int cAStar = s.count(new RegexpQuery(new Term("f", "a*")));
        int cOpt = s.count(new RegexpQuery(new Term("f", "(a&b)?")));
        int cStar = s.count(new RegexpQuery(new Term("f", "(a&b)*")));
        int cHashStar = s.count(new RegexpQuery(new Term("f", "#*")));
        System.out.println(
            "RegexpQuery counts: '()'="
                + cEmptyStr
                + " 'a*'="
                + cAStar
                + " '(a&b)?'="
                + cOpt
                + " '(a&b)*'="
                + cStar
                + " '#*'="
                + cHashStar);
        assertEquals("control: '()' matches the empty term", 1, cEmptyStr);
        assertEquals("control: 'a*' matches \"\" and \"a\"", 2, cAStar);
        assertEquals("control: '(a&b)?' matches the empty term", 1, cOpt);
        int cAbc = s.count(new RegexpQuery(new Term("f", "a(a&b)*")));
        System.out.println("RegexpQuery count 'a(a&b)*' (should match term \"a\") = " + cAbc);
        assertEquals("'(a&b)*' must match the empty term", 1, cStar);
        assertEquals("'#*' must match the empty term", 1, cHashStar);
        assertEquals("'a(a&b)*' must match the term \"a\"", 1, cAbc);
      }
    }
  }
}
