import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.core.nodes.FieldQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.RangeQueryNode;
import org.apache.lucene.queryparser.flexible.core.processors.QueryNodeProcessor;
import org.apache.lucene.queryparser.flexible.core.processors.QueryNodeProcessorPipeline;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.processors.MatchAllDocsQueryNodeProcessor;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent verification of the finding (written from the grammar/processor source, not
 * from the finder's repro). Contract under test: StandardQueryParser#parse and
 * QueryParserHelper#parse declare "throws QueryNodeException"; any other exception escaping
 * parse() for a user query string is a contract violation. Deterministic: no randomness is used.
 */
public class TestStarFieldRangeCCE extends LuceneTestCase {

  /** Returns "OK:<query>" / "QNE:<class>" / "OTHER:<class>: <msg>". */
  static String outcome(StandardQueryParser qp, String q) {
    try {
      Query query = qp.parse(q, "f");
      return "OK:" + query.getClass().getSimpleName() + " " + query;
    } catch (QueryNodeException e) {
      return "QNE:" + e.getClass().getSimpleName();
    } catch (RuntimeException e) {
      StackTraceElement top = e.getStackTrace()[0];
      return "OTHER:" + e.getClass().getName() + " at " + top + " (" + e.getMessage() + ")";
    }
  }

  static String outcome(String q) {
    return outcome(new StandardQueryParser(new WhitespaceAnalyzer()), q);
  }

  /** Every range whose field is "*" and where at least one bound has text "*". */
  static final String[] TRIGGERS = {
    "*>a", "*<a", "*>=a", "*<=a", // FieldRangeExpr shorthand with "*" field (the reported case)
    "*:[a TO *]", "*:[* TO a]", "*:{a TO *}", "*:[* TO *]", // explicit TermRangeExpr, "*" field
    "x AND *>a", "*>\"a b\"", "*>5", // embedded / quoted / numeric bound
    "\\*>a", // escaped field name: discardEscapeChar makes it "*" again
    "*:[a TO \\*]", // escaped star bound: MatchAllDocsQueryNodeProcessor ignores wasEscaped
    "*>a*" // any shorthand with field "*": the synthesized open bound is always "*"
  };

  static final String[] CONTROLS = {
    "f>a", "f<a", "f:[a TO *]", "f:[* TO *]", "*:*", "*:a", "*:[a TO b]"
  };

  public void testPrintMatrix() {
    for (String q : TRIGGERS) System.out.println("TRIGGER  " + q + "  ->  " + outcome(q));
    for (String q : CONTROLS) System.out.println("CONTROL  " + q + "  ->  " + outcome(q));
    // default field "*" (no explicit field in the query string)
    StandardQueryParser qpStarDefault = new StandardQueryParser(new WhitespaceAnalyzer());
    for (String q : new String[] {"[a TO *]", "a"}) {
      String o;
      try {
        o = "OK:" + qpStarDefault.parse(q, "*");
      } catch (QueryNodeException e) {
        o = "QNE:" + e;
      } catch (RuntimeException e) {
        o = "OTHER:" + e.getClass().getName() + " at " + e.getStackTrace()[0];
      }
      System.out.println("DEFAULTFIELD=*  " + q + "  ->  " + o);
    }
    QueryParser classic = new QueryParser("f", new WhitespaceAnalyzer());
    for (String q : new String[] {"*:[a TO *]", "*:[* TO a]", "*:[* TO *]"}) {
      String o;
      try {
        o = "OK:" + classic.parse(q);
      } catch (Exception e) {
        o = "EXC:" + e;
      }
      System.out.println("CLASSIC  " + q + "  ->  " + o);
    }
  }

  /** The reported trigger, asserting the documented contract. Expected to FAIL on main. */
  public void testStarGreaterThanThrowsOnlyQueryNodeException() throws Exception {
    StandardQueryParser qp = new StandardQueryParser(new WhitespaceAnalyzer());
    try {
      qp.parse("*>a", "f");
    } catch (QueryNodeException acceptable) {
      // documented
    }
  }

  /** All trigger variants; collects every contract violation. Expected to FAIL on main. */
  public void testAllStarFieldRangesHonourContract() {
    List<String> bad = new ArrayList<>();
    for (String q : TRIGGERS) {
      String o = outcome(q);
      if (o.startsWith("OTHER:")) bad.add(q + " -> " + o);
    }
    assertTrue(bad.size() + " contract violations:\n" + String.join("\n", bad), bad.isEmpty());
  }

  /** Controls must not throw anything except QueryNodeException. Expected to PASS. */
  public void testControlsHonourContract() {
    for (String q : CONTROLS) {
      String o = outcome(q);
      assertFalse(q + " -> " + o, o.startsWith("OTHER:"));
    }
  }

  // ---- causal confirmation of the root cause (expected to PASS) ----

  static int indexOfMatchAll(QueryNodeProcessorPipeline p) {
    for (int i = 0; i < p.size(); i++) {
      if (p.get(i).getClass() == MatchAllDocsQueryNodeProcessor.class) return i;
    }
    throw new AssertionError("MatchAllDocsQueryNodeProcessor not in pipeline");
  }

  /** Removing MatchAllDocsQueryNodeProcessor from the default pipeline makes the CCE disappear. */
  public void testWithoutMatchAllProcessorNoCCE() {
    StandardQueryParser qp = new StandardQueryParser(new WhitespaceAnalyzer());
    QueryNodeProcessorPipeline p = (QueryNodeProcessorPipeline) qp.getQueryNodeProcessor();
    p.remove(indexOfMatchAll(p));
    for (String q : TRIGGERS) {
      String o = outcome(qp, q);
      assertFalse(q + " -> " + o, o.startsWith("OTHER:"));
    }
  }

  /** Candidate fix: only rewrite a FieldQueryNode "*:*" that is not a range bound. */
  static final class RangeAwareMatchAllDocsQueryNodeProcessor extends MatchAllDocsQueryNodeProcessor {
    @Override
    protected QueryNode postProcessNode(QueryNode node) throws QueryNodeException {
      if (node instanceof FieldQueryNode && node.getParent() instanceof RangeQueryNode) {
        return node; // a range bound "*" is an open-range marker, not match-all
      }
      return super.postProcessNode(node);
    }
  }

  public void testRangeAwareProcessorGivesOpenRangesAndKeepsMatchAll() throws Exception {
    StandardQueryParser qp = new StandardQueryParser(new WhitespaceAnalyzer());
    QueryNodeProcessorPipeline p = (QueryNodeProcessorPipeline) qp.getQueryNodeProcessor();
    QueryNodeProcessor fixed = new RangeAwareMatchAllDocsQueryNodeProcessor();
    p.set(indexOfMatchAll(p), fixed);
    for (String q : TRIGGERS) {
      String o = outcome(qp, q);
      System.out.println("FIXED    " + q + "  ->  " + o);
      assertFalse(q + " -> " + o, o.startsWith("OTHER:"));
    }
    Query gt = qp.parse("*>a", "f");
    assertTrue(gt.toString(), gt instanceof TermRangeQuery);
    assertEquals("*", ((TermRangeQuery) gt).getField());
    assertEquals("*:{a TO *]", gt.toString()); // same shape as "f>a" -> f:{a TO *]
    assertTrue(qp.parse("*:*", "f") instanceof MatchAllDocsQuery);
  }
}
