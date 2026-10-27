import java.io.IOException;
import java.util.Arrays;
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
import org.apache.lucene.util.automaton.CharacterRunAutomaton;
import org.apache.lucene.util.automaton.RegExp;

/**
 * Independent verification of the finding: under RegExp.CASE_INSENSITIVE an escaped
 * character inside a character class with more than one item is not case-expanded.
 *
 * <p>Deterministic: no randomness is used (fixed inputs, ByteBuffersDirectory, plain
 * IndexWriterConfig). Non-ASCII characters are written as unicode escapes so the source encoding
 * cannot matter.
 */
public class TestEscapedCharInCaseInsensitiveClass extends LuceneTestCase {
  static final int CI = RegExp.CASE_INSENSITIVE;
  static final String E_LO = "\u00e9"; // e with acute, lower case
  static final String E_UP = "\u00c9"; // E with acute, upper case
  static final String LONG_S = "\u017f"; // LATIN SMALL LETTER LONG S, folds to s/S

  static boolean lucene(String re, String input) {
    return new CharacterRunAutomaton(new RegExp(re, RegExp.NONE, CI).toAutomaton()).run(input);
  }

  /** Oracle: java.util.regex, which the CASE_INSENSITIVE javadoc names as the parity target. */
  static boolean jdk(String re, String input) {
    return Pattern.compile(re, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
        .matcher(input)
        .matches();
  }

  /** Controls: the same character, written in the other three ways, IS case-insensitive. */
  public void testControlsMatch() {
    String[][] cases = {
      {"[" + E_LO + "x]", E_UP}, // unescaped, multi-item class
      {"\\" + E_LO, E_UP}, // escaped, outside a class
      {"[\\" + E_LO + "]", E_UP}, // escaped, single-item class (simplified to REGEXP_CHAR)
      {"[" + LONG_S + "x]", "S"},
      {"\\" + LONG_S, "S"},
      {"[\\" + LONG_S + "]", "S"},
      {"[\\.x]", "X"}, // escaped reserved char + plain letter: the plain letter is still expanded
    };
    for (String[] c : cases) {
      assertTrue("JDK oracle should match: " + Arrays.toString(c), jdk(c[0], c[1]));
      assertTrue("Lucene control should match: " + Arrays.toString(c), lucene(c[0], c[1]));
    }
  }

  /** Candidate bug: escaped non-ASCII letter in a 2-item class. */
  public void testEscapedCharFirstInMultiItemClass() {
    String re = "[\\" + E_LO + "x]";
    assertTrue("JDK oracle", jdk(re, E_UP));
    assertTrue("Lucene " + re + " should match " + E_UP + " under CASE_INSENSITIVE", lucene(re, E_UP));
  }

  /** Same, escaped char in second position (order must not matter). */
  public void testEscapedCharSecondInMultiItemClass() {
    String re = "[x\\" + E_LO + "]";
    assertTrue("JDK oracle", jdk(re, E_UP));
    assertTrue("Lucene " + re + " should match " + E_UP + " under CASE_INSENSITIVE", lucene(re, E_UP));
  }

  /** Same, with the special-cased long s (folds to ASCII s/S). */
  public void testEscapedLongSInMultiItemClass() {
    String re = "[\\" + LONG_S + "x]";
    assertTrue("JDK oracle", jdk(re, "S"));
    assertTrue("Lucene " + re + " should match S under CASE_INSENSITIVE", lucene(re, "S"));
  }

  /** Over-match consequence: the negated class lets the case variant through. */
  public void testNegatedClassWithEscapedChar() {
    String re = "[^\\" + E_LO + "x]";
    assertFalse("JDK oracle", jdk(re, E_UP));
    assertFalse("Unescaped control", lucene("[^" + E_LO + "x]", E_UP));
    assertFalse(
        "Lucene " + re + " must NOT match " + E_UP + " under CASE_INSENSITIVE", lucene(re, E_UP));
  }

  /** Parse-tree evidence: the REGEXP_CHAR_CLASS node only contains the literal escaped char. */
  public void testParsedClassContents() {
    RegExp unescaped = new RegExp("[" + E_LO + "x]", RegExp.NONE, CI);
    RegExp escaped = new RegExp("[\\" + E_LO + "x]", RegExp.NONE, CI);
    assertEquals(RegExp.Kind.REGEXP_CHAR_CLASS, unescaped.kind);
    assertEquals(RegExp.Kind.REGEXP_CHAR_CLASS, escaped.kind);
    assertTrue(
        "unescaped class contains U+00C9: " + Arrays.toString(unescaped.from),
        Arrays.stream(unescaped.from).anyMatch(cp -> cp == 0xC9));
    assertTrue(
        "escaped class should also contain U+00C9: " + Arrays.toString(escaped.from),
        Arrays.stream(escaped.from).anyMatch(cp -> cp == 0xC9));
  }

  /** End-to-end: RegexpQuery with CASE_INSENSITIVE over an index. */
  public void testRegexpQueryEndToEnd() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String v : new String[] {E_LO, E_UP, "x", "X", "y"}) {
          Document d = new Document();
          d.add(new StringField("f", v, Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        int unescaped =
            s.count(new RegexpQuery(new Term("f", "[" + E_LO + "x]"), RegExp.ALL, CI));
        int escaped =
            s.count(new RegexpQuery(new Term("f", "[\\" + E_LO + "x]"), RegExp.ALL, CI));
        assertEquals("unescaped [e-acute x] CI hits", 4, unescaped);
        assertEquals("escaped [backslash e-acute x] CI hits should equal unescaped", unescaped, escaped);
      }
    }
  }
}
