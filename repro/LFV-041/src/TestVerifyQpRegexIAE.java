import java.util.Arrays;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.automaton.RegExp;

/**
 * Independent verification of the finding. Part A characterizes what main does (these
 * PASS when the reported behavior is present). Part B asserts the documented contract of parse()
 * (these FAIL when the reported behavior is present).
 */
public class TestVerifyQpRegexIAE extends LuceneTestCase {

  static final String[] BAD_REGEXES = {"/((/", "/(/", "/[a/", "/a{2/", "/a{3,1}/", "/<1-/"};

  static Throwable classic(String q) {
    QueryParser qp = new QueryParser("f", new StandardAnalyzer());
    try {
      Query r = qp.parse(q);
      return new AssertionError("no exception, got " + r);
    } catch (Throwable t) {
      return t;
    }
  }

  static Throwable flexible(String q) {
    StandardQueryParser qp = new StandardQueryParser(new StandardAnalyzer());
    try {
      Query r = qp.parse(q, "f");
      return new AssertionError("no exception, got " + r);
    } catch (Throwable t) {
      return t;
    }
  }

  static String describe(Throwable t) {
    StackTraceElement[] st = t.getStackTrace();
    String origin = "";
    for (StackTraceElement e : st) {
      String c = e.getClassName();
      if (c.contains("queryparser")) {
        origin = c.substring(c.lastIndexOf('.') + 1) + "." + e.getMethodName() + ":" + e.getLineNumber();
        break;
      }
    }
    return t.getClass().getName() + ": " + t.getMessage() + "  [first queryparser frame: " + origin + "]";
  }

  // ---------------- Part A: characterization (expected to PASS on main if the bug is real)

  public void testA1_RegExpItselfDocumentsIAE() {
    // RegExp(String,int,int) javadoc: "@exception IllegalArgumentException if an error occurred
    // while parsing the regular expression"
    IllegalArgumentException e =
        expectThrows(IllegalArgumentException.class, () -> new RegExp("((", RegExp.ALL, 0));
    System.out.println("A1 RegExp(\"((\") -> " + e);
    expectThrows(
        IllegalArgumentException.class,
        () -> new RegexpQuery(new org.apache.lucene.index.Term("f", "((")));
  }

  public void testA2_ClassicLetsIAEEscape() {
    for (String q : BAD_REGEXES) {
      for (String wrapped : new String[] {q, "f:" + q, "foo AND " + q, "+(" + q + ")^2"}) {
        Throwable t = classic(wrapped);
        System.out.println("A2 classic  " + wrapped + " -> " + describe(t));
        assertTrue(wrapped + " -> " + t, t instanceof IllegalArgumentException);
        assertFalse(t instanceof ParseException);
      }
    }
  }

  public void testA3_MultiFieldClassicLetsIAEEscape() {
    MultiFieldQueryParser qp =
        new MultiFieldQueryParser(new String[] {"a", "b"}, new StandardAnalyzer());
    IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> qp.parse("/((/"));
    System.out.println("A3 MultiFieldQueryParser /((/ -> " + describe(e));
  }

  public void testA4_FlexibleLetsIAEEscape() {
    for (String q : BAD_REGEXES) {
      for (String wrapped : new String[] {q, "f:" + q, "foo AND " + q}) {
        Throwable t = flexible(wrapped);
        System.out.println("A4 flexible " + wrapped + " -> " + describe(t));
        assertTrue(wrapped + " -> " + t, t instanceof IllegalArgumentException);
        assertFalse(t instanceof QueryNodeException);
      }
    }
  }

  // Controls: the parsers DO map other user-input errors to their checked exception.
  public void testA5_ControlsOtherErrorsAreChecked() {
    Throwable t;
    for (String q : new String[] {"foo AND (", "foo:", "\"unterminated", "a~1.5", "*foo"}) {
      t = classic(q);
      System.out.println("A5 classic  " + q + " -> " + describe(t));
      assertTrue(q + " -> " + t, t instanceof ParseException);
    }
    for (String q : new String[] {"foo AND (", "\"unterminated"}) {
      t = flexible(q);
      System.out.println("A5 flexible " + q + " -> " + describe(t));
      assertTrue(q + " -> " + t, t instanceof QueryNodeException);
    }
    // valid regexes parse fine
    t = classic("/a(b|c)*/");
    assertTrue(t.toString(), t instanceof AssertionError && t.getMessage().contains("no exception"));
    t = flexible("/a(b|c)*/");
    assertTrue(t.toString(), t instanceof AssertionError && t.getMessage().contains("no exception"));
  }

  // ---------------- Part B: documented contract (expected to FAIL on main if the bug is real)

  /** QueryParserBase#parse: "@throws ParseException if the parsing fails". */
  public void testB1_ClassicContract() {
    expectThrows(ParseException.class, () -> new QueryParser("f", new StandardAnalyzer()).parse("/((/"));
  }

  /** StandardQueryParser#parse: "@throws QueryNodeException if something wrong happens along the three phases". */
  public void testB2_FlexibleContract() {
    expectThrows(
        QueryNodeException.class, () -> new StandardQueryParser(new StandardAnalyzer()).parse("/((/", "f"));
  }
}
