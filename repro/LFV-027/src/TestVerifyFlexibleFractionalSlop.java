import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent verification of the finding: the flexible StandardQueryParser lets a
 * NumberFormatException escape parse() for a phrase slop written as a decimal number.
 *
 * <p>Contract checked: StandardQueryParser#parse is declared "@throws QueryNodeException if
 * something wrong happens along the three phases". So for any input it must either return a Query
 * or throw a QueryNodeException; any other exception is a contract violation.
 */
public class TestVerifyFlexibleFractionalSlop extends LuceneTestCase {

  private static final String FIELD = "f";

  /** Returns null on success/QueryNodeException, else the unexpected throwable. */
  private static Throwable flexibleOutcome(String q, StringBuilder log) {
    StandardQueryParser qp = new StandardQueryParser(new StandardAnalyzer());
    try {
      Query query = qp.parse(q, FIELD);
      log.append("  flexible ").append(q).append(" -> ").append(query.getClass().getSimpleName())
          .append(" ").append(query).append('\n');
      return null;
    } catch (QueryNodeException e) {
      log.append("  flexible ").append(q).append(" -> QueryNodeException (ok)\n");
      return null;
    } catch (Throwable t) {
      log.append("  flexible ").append(q).append(" -> ").append(t).append('\n');
      return t;
    }
  }

  /** CONTROL: an integer slop parses to a PhraseQuery with that slop. */
  public void testIntegerSlopControl() throws Exception {
    StandardQueryParser qp = new StandardQueryParser(new StandardAnalyzer());
    Query q = qp.parse("\"a b\"~30", FIELD);
    System.out.println("control flexible \"a b\"~30 -> " + q);
    assertTrue(q instanceof PhraseQuery);
    assertEquals(30, ((PhraseQuery) q).getSlop());
  }

  /** CONTROL: the classic parser accepts the same input and truncates the slop to 30. */
  public void testClassicParserAcceptsFractionalSlop() throws Exception {
    QueryParser qp = new QueryParser(FIELD, new StandardAnalyzer());
    Query q = qp.parse("\"a b\"~30.5");
    System.out.println("control classic  \"a b\"~30.5 -> " + q);
    assertTrue(q instanceof PhraseQuery);
    assertEquals(30, ((PhraseQuery) q).getSlop());
  }

  /** THE CANDIDATE: fractional phrase slop in the flexible parser. */
  public void testFlexibleFractionalSlop() {
    StringBuilder log = new StringBuilder();
    Throwable t = flexibleOutcome("\"a b\"~30.5", log);
    System.out.print(log);
    if (t != null) {
      StackTraceElement top = null;
      for (StackTraceElement e : t.getStackTrace()) {
        if (e.getClassName().startsWith("org.apache.lucene")) {
          top = e;
          break;
        }
      }
      System.out.println("  first Lucene frame: " + top);
      throw new AssertionError(
          "parse() must return a Query or throw QueryNodeException, but threw " + t, t);
    }
  }

  /**
   * Same root cause, other triggers: every grammar site that feeds a NUMBER token (which admits
   * "d+.d+") or an over-long digit string into StandardSyntaxParser.parseInt.
   */
  public void testOtherParseIntSites() {
    String[] inputs = {
      "\"a b\"~3000000000", // integer overflow, same parseInt
      "(a b)@1.5", // MinShouldMatchNode
      "fn:atLeast(1.5 a b)",
      "fn:maxwidth(2.5 a)",
      "fn:maxgaps(2.5 a)",
      "fn:within(a 2.5 b)",
      "fn:extend(a 1.5 2)",
      "fn:notWithin(a 2.5 b)",
      "fn:wildcard(a* 1.5)",
      "fn:fuzzyTerm(abc 1.5)",
    };
    StringBuilder log = new StringBuilder();
    int bad = 0;
    for (String in : inputs) {
      if (flexibleOutcome(in, log) instanceof NumberFormatException) {
        bad++;
      }
    }
    System.out.print(log);
    System.out.println("  NumberFormatException escaped for " + bad + "/" + inputs.length);
    assertEquals("inputs that leak NumberFormatException", 0, bad);
  }
}
