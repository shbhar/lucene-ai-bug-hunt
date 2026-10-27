import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.pattern.SimplePatternSplitTokenizer;
import org.apache.lucene.analysis.pattern.SimplePatternTokenizer;
import org.apache.lucene.analysis.pattern.SimplePatternTokenizerFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.uhighlight.CharArrayMatcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Downstream reachability of the finding: public Lucene APIs that take a user regexp string and build
 * a CharacterRunAutomaton directly (no CompiledAutomaton guard). Assertions state EXPECTED
 * behaviour (an empty-language pattern yields a tokenizer that emits no tokens); failures reproduce
 * the bug through a string-configurable API.
 */
public class TestZeroStateDownstream extends LuceneTestCase {

  private static int countTokens(Tokenizer t, String text) throws Exception {
    t.setReader(new StringReader(text));
    t.reset();
    int n = 0;
    while (t.incrementToken()) {
      n++;
    }
    t.end();
    t.close();
    return n;
  }

  private static void report(String label, Throwable e) {
    System.out.println("[downstream] " + label + " THREW " + e);
    StackTraceElement[] st = e.getStackTrace();
    for (int i = 0; i < Math.min(5, st.length); i++) {
      System.out.println("[downstream]     at " + st[i]);
    }
  }

  // ------------------------------------------------------------------- controls

  /** Control: query path is guarded by CompiledAutomaton (0-state -> 1-state, type NONE). */
  public void testControlRegexpQuery() throws Exception {
    try (Directory dir = newDirectory()) {
      try (RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
        Document d = new Document();
        d.add(new StringField("f", "aa", Field.Store.NO));
        w.addDocument(d);
        try (DirectoryReader r = w.getReader()) {
          IndexSearcher s = newSearcher(r);
          assertEquals(0, s.count(new RegexpQuery(new Term("f", "(a&b){2}"))));
          assertEquals(0, s.count(new RegexpQuery(new Term("f", "([^\\D\\d]){2}"))));
          assertEquals(1, s.count(new RegexpQuery(new Term("f", "a{2}"))));
        }
      }
    }
  }

  /** Control: an empty-language pattern whose automaton has spare capacity works. */
  public void testControlSimplePatternTokenizerAAndB() throws Exception {
    assertEquals(0, countTokens(new SimplePatternTokenizer("a&b"), "ab ab"));
  }

  // ------------------------------------------------------- candidate triggers

  public void testSimplePatternTokenizer() throws Exception {
    Tokenizer t;
    try {
      t = new SimplePatternTokenizer("(a&b){2}");
    } catch (RuntimeException e) {
      report("new SimplePatternTokenizer(\"(a&b){2}\")", e);
      throw e;
    }
    assertEquals(0, countTokens(t, "ab ab"));
  }

  public void testSimplePatternSplitTokenizer() throws Exception {
    Tokenizer t;
    try {
      t = new SimplePatternSplitTokenizer("([^\\D\\d]){2}");
    } catch (RuntimeException e) {
      report("new SimplePatternSplitTokenizer(\"([^\\\\D\\\\d]){2}\")", e);
      throw e;
    }
    assertEquals(1, countTokens(t, "ab ab"));
  }

  /** Factory path: the pattern is a plain string argument (analyzer configuration). */
  public void testSimplePatternTokenizerFactory() throws Exception {
    Map<String, String> args = new HashMap<>();
    args.put("pattern", "(a&b){2}");
    SimplePatternTokenizerFactory f;
    try {
      f = new SimplePatternTokenizerFactory(args);
    } catch (RuntimeException e) {
      report("new SimplePatternTokenizerFactory({pattern=(a&b){2}})", e);
      throw e;
    }
    Tokenizer t;
    try {
      t = f.create();
    } catch (RuntimeException e) {
      report("SimplePatternTokenizerFactory.create()", e);
      throw e;
    }
    assertEquals(0, countTokens(t, "ab ab"));
  }

  /** Highlighter helper with no terms (survey; result printed, assertion is expected behaviour). */
  public void testCharArrayMatcherNoTerms() throws Exception {
    CharArrayMatcher m;
    try {
      m = CharArrayMatcher.fromTerms(List.<BytesRef>of());
    } catch (RuntimeException e) {
      report("CharArrayMatcher.fromTerms(List.of())", e);
      throw e;
    }
    System.out.println("[downstream] CharArrayMatcher.fromTerms(List.of()) OK");
    assertFalse(m.match("x".toCharArray(), 0, 1));
  }
}
