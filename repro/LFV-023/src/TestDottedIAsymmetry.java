import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.text.UnicodeSetIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
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
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.UnicodeUtil;
import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;
import org.apache.lucene.util.automaton.RegExp;
import org.apache.lucene.util.automaton.Transition;

/**
 * Independent verification of the finding (RegExp.CASE_INSENSITIVE asymmetric for U+0130 /
 * U+0131). Code points are written as \\u escapes so the source is encoding-independent.
 *
 * <p>Tests named test*Characterize pin the behaviour observed on main (they PASS on main). Tests
 * named test*Expected encode the documented / stated-intent model (they FAIL on main if the
 * candidate is real).
 */
public class TestDottedIAsymmetry extends LuceneTestCase {
  static final int CI = RegExp.CASE_INSENSITIVE;
  static final String DOTTED_I = "\u0130"; // LATIN CAPITAL LETTER I WITH DOT ABOVE
  static final String DOTLESS_I = "\u0131"; // LATIN SMALL LETTER DOTLESS I

  static boolean ci(String regex, String input) {
    return new CharacterRunAutomaton(new RegExp(regex, RegExp.ALL, CI).toAutomaton()).run(input);
  }

  static boolean javaCi(String regex, String input) {
    return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
        .matcher(input)
        .matches();
  }

  /** The set of single code points accepted by Automata.makeCaseInsensitiveChar(c) (public API). */
  static int[] expansion(int c) {
    Automaton a = Automata.makeCaseInsensitiveChar(c);
    List<Integer> out = new ArrayList<>();
    Transition t = new Transition();
    int n = a.initTransition(0, t);
    for (int i = 0; i < n; i++) {
      a.getNextTransition(t);
      for (int x = t.min; x <= t.max; x++) out.add(x);
    }
    return out.stream().mapToInt(Integer::intValue).toArray();
  }

  static boolean contains(int[] arr, int x) {
    for (int v : arr) if (v == x) return true;
    return false;
  }

  static String hex(int c) {
    return String.format("U+%04X", c);
  }

  // ---------------------------------------------------------------------------------------------
  // 1. Characterisation of what main does today (PASSES on main).
  // ---------------------------------------------------------------------------------------------
  public void testObservedBehaviourCharacterize() {
    // JDK simple case mappings (UnicodeData.txt fields 12/13) that CaseExpansion adds unconditionally
    assertEquals('i', Character.toLowerCase(0x0130));
    assertEquals(0x0130, Character.toUpperCase(0x0130));
    assertEquals('I', Character.toUpperCase(0x0131));
    assertEquals(0x0131, Character.toLowerCase(0x0131));
    assertEquals('I', Character.toUpperCase('i'));
    assertEquals('i', Character.toLowerCase('I'));

    // Lucene RegExp CASE_INSENSITIVE, pattern -> input
    assertTrue("\\u0130 pattern matches 'i'", ci(DOTTED_I, "i"));
    assertFalse("'i' pattern does NOT match \\u0130", ci("i", DOTTED_I));
    assertTrue("\\u0131 pattern matches 'I'", ci(DOTLESS_I, "I"));
    assertFalse("'I' pattern does NOT match \\u0131", ci("I", DOTLESS_I));
    // the escaped form used by the candidate behaves the same
    assertTrue(ci("\\" + DOTTED_I, "i"));
    assertTrue(ci("\\" + DOTLESS_I, "I"));
    // U+0130 does not reach I, U+0131 does not reach i (not even transitively)
    assertFalse(ci(DOTTED_I, "I"));
    assertFalse(ci(DOTLESS_I, "i"));
    assertFalse(ci(DOTTED_I, DOTLESS_I));

    // the expansion sets themselves (public Automata API, #15753)
    assertArrayEquals(new int[] {'i', 0x0130}, expansion(0x0130));
    assertArrayEquals(new int[] {'I', 0x0131}, expansion(0x0131));
    assertArrayEquals(new int[] {'I', 'i'}, expansion('i'));
    assertArrayEquals(new int[] {'I', 'i'}, expansion('I'));

    // multi-char string path (Automata.makeCaseInsensitiveString via REGEXP_STRING)
    assertTrue(ci(DOTTED_I + "stanbul", "istanbul"));
    assertFalse(ci("istanbul", DOTTED_I + "stanbul"));

    // java.util.regex (the javadoc's parity target) treats all four as mutually equal
    String[] four = {"i", "I", DOTTED_I, DOTLESS_I};
    for (String p : four) for (String s : four) assertTrue(p + " vs " + s, javaCi(p, s));

    // ICU4J 78.3 simple case closure (what rmuir said CaseExpansion "just gives you", PR #14384)
    assertEquals(new UnicodeSet("[\\u0130]"), new UnicodeSet(0x0130, 0x0130).closeOver(UnicodeSet.SIMPLE_CASE_INSENSITIVE));
    assertEquals(new UnicodeSet("[\\u0131]"), new UnicodeSet(0x0131, 0x0131).closeOver(UnicodeSet.SIMPLE_CASE_INSENSITIVE));
    assertEquals(new UnicodeSet("[Ii]"), new UnicodeSet('i', 'i').closeOver(UnicodeSet.SIMPLE_CASE_INSENSITIVE));

    // Lucene's own simple case folding (UnicodeUtil.foldCase, #16371) keeps the two apart from i/I
    assertEquals(0x0130, UnicodeUtil.foldCase(0x0130));
    assertEquals(0x0131, UnicodeUtil.foldCase(0x0131));
    assertEquals('i', UnicodeUtil.foldCase('I'));
    assertEquals('i', UnicodeUtil.foldCase('i'));
  }

  /** End-to-end through RegexpQuery on an index: hit sets are not symmetric (PASSES on main). */
  public void testRegexpQueryCharacterize() throws Exception {
    try (ByteBuffersDirectory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String v : new String[] {"i", "I", DOTTED_I, DOTLESS_I}) {
          Document d = new Document();
          d.add(new StringField("f", v, Field.Store.YES));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Map<String, TreeSet<String>> hits = new HashMap<>();
        for (String q : new String[] {"i", "I", DOTTED_I, DOTLESS_I}) {
          TreeSet<String> set = new TreeSet<>();
          for (var sd : s.search(new RegexpQuery(new Term("f", q), RegExp.ALL, CI), 10).scoreDocs) {
            set.add(hex(s.storedFields().document(sd.doc).get("f").codePointAt(0)));
          }
          hits.put(hex(q.codePointAt(0)), set);
          System.out.println("RegexpQuery CASE_INSENSITIVE /" + hex(q.codePointAt(0)) + "/ hits " + set);
        }
        assertEquals(new TreeSet<>(List.of("U+0049", "U+0069")), hits.get("U+0069"));
        assertEquals(new TreeSet<>(List.of("U+0049", "U+0069")), hits.get("U+0049"));
        assertEquals(new TreeSet<>(List.of("U+0069", "U+0130")), hits.get("U+0130"));
        assertEquals(new TreeSet<>(List.of("U+0049", "U+0131")), hits.get("U+0131"));
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // 2. Expected properties (FAIL on main if the candidate is real).
  // ---------------------------------------------------------------------------------------------

  /** Case-insensitive equality between two single characters should be symmetric. */
  public void testSymmetryOfTheFourIsExpected() {
    String[] four = {"i", "I", DOTTED_I, DOTLESS_I};
    List<String> asym = new ArrayList<>();
    for (String p : four)
      for (String s : four)
        if (ci(p, s) != ci(s, p))
          asym.add("/" + hex(p.codePointAt(0)) + "/ on " + hex(s.codePointAt(0)) + "=" + ci(p, s)
              + " but /" + hex(s.codePointAt(0)) + "/ on " + hex(p.codePointAt(0)) + "=" + ci(s, p));
    assertTrue("asymmetric pairs: " + asym, asym.isEmpty());
  }

  /** Exhaustive over all 0x110000 code points through the public Automata API. */
  public void testExhaustiveSymmetryExpected() {
    long t0 = System.nanoTime();
    Map<Integer, int[]> exp = new HashMap<>();
    for (int c = 0; c <= Character.MAX_CODE_POINT; c++) {
      int[] e = expansion(c);
      assertTrue("expansion must contain c itself " + hex(c), contains(e, c));
      if (e.length > 1) exp.put(c, e);
    }
    List<String> asym = new ArrayList<>();
    for (Map.Entry<Integer, int[]> en : exp.entrySet()) {
      int c = en.getKey();
      for (int d : en.getValue()) {
        if (d == c) continue;
        int[] back = exp.get(d);
        if (back == null || !contains(back, c)) asym.add(hex(c) + "->" + hex(d) + " but not back");
      }
    }
    asym.sort(null);
    System.out.println("exhaustive: " + exp.size() + " code points have >1 variant; asymmetric edges: "
        + asym + " (" + (System.nanoTime() - t0) / 1_000_000 + " ms)");
    assertTrue("asymmetric edges: " + asym, asym.isEmpty());
  }

  /**
   * Stated design intent (rmuir, PR #14384): CaseExpansion.expand(c) "just gives you
   * UnicodeSet(ch).closeOver(UnicodeSet.SIMPLE_CASE_INSENSITIVE)". Compare against ICU4J 78.3 (the
   * same ICU the generator used) for every code point, and against Lucene's own
   * UnicodeUtil.foldCase equivalence classes.
   */
  public void testAgreesWithIcuSimpleClosureAndFoldCaseExpected() {
    Map<Integer, List<Integer>> foldClass = new HashMap<>();
    for (int c = 0; c <= Character.MAX_CODE_POINT; c++) {
      foldClass.computeIfAbsent(UnicodeUtil.foldCase(c), k -> new ArrayList<>()).add(c);
    }
    List<String> icuDiff = new ArrayList<>();
    List<String> foldDiff = new ArrayList<>();
    List<String> icuDiffNonI = new ArrayList<>(), foldDiffNonI = new ArrayList<>();
    for (int c = 0; c <= Character.MAX_CODE_POINT; c++) {
      TreeSet<Integer> lucene = new TreeSet<>();
      for (int x : expansion(c)) lucene.add(x);
      TreeSet<Integer> icu = new TreeSet<>();
      UnicodeSet us = new UnicodeSet(c, c).closeOver(UnicodeSet.SIMPLE_CASE_INSENSITIVE);
      for (UnicodeSetIterator it = new UnicodeSetIterator(us); it.next(); ) {
        if (it.codepoint != UnicodeSetIterator.IS_STRING) icu.add(it.codepoint);
      }
      TreeSet<Integer> fold = new TreeSet<>(foldClass.get(UnicodeUtil.foldCase(c)));
      boolean iFamily = c == 'i' || c == 'I' || c == 0x0130 || c == 0x0131;
      if (!lucene.equals(icu)) {
        if (iFamily) icuDiff.add(hex(c) + " lucene=" + hexSet(lucene) + " icu=" + hexSet(icu));
        else icuDiffNonI.add(hex(c));
      }
      if (!lucene.equals(fold)) {
        if (iFamily) foldDiff.add(hex(c) + " lucene=" + hexSet(lucene) + " foldClass=" + hexSet(fold));
        else foldDiffNonI.add(hex(c));
      }
    }
    System.out.println("vs ICU simple closure, i-family diffs: " + icuDiff + "; other diffs (" + icuDiffNonI.size() + "): " + icuDiffNonI);
    System.out.println("vs UnicodeUtil.foldCase classes, i-family diffs: " + foldDiff + "; other diffs (" + foldDiffNonI.size() + "): " + foldDiffNonI);
    assertTrue("i-family differs from ICU simple case closure: " + icuDiff, icuDiff.isEmpty());
  }

  static String hexSet(TreeSet<Integer> s) {
    List<String> l = new ArrayList<>();
    for (int x : s) l.add(hex(x));
    return l.toString();
  }
}
