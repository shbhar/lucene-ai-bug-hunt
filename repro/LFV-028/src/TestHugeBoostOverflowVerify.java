import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.complexPhrase.ComplexPhraseQueryParser;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.core.nodes.BoostQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;
import org.apache.lucene.queryparser.flexible.precedence.PrecedenceQueryParser;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.parser.StandardSyntaxParser;
import org.apache.lucene.queryparser.surround.query.BasicQueryFactory;
import org.apache.lucene.queryparser.surround.query.SrndQuery;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.analysis.MockTokenFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent verification of the finding ("a huge boost that parses to Infinity gives
 * IllegalArgumentException from BoostQuery"). Fully deterministic: no random() is used anywhere.
 *
 * <p>Tests named testContract* assert the documented contract of parse() and are EXPECTED TO FAIL
 * on main if the candidate is real. All other tests are characterisation tests that pin down the
 * observed behaviour and are expected to pass.
 */
public class TestHugeBoostOverflowVerify extends LuceneTestCase {

  /** Exact decimal value of Float.MAX_VALUE = 2^128 - 2^104. */
  static final String MAX_DEC = new BigDecimal(Float.MAX_VALUE).toBigInteger().toString();

  /**
   * Smallest integer that rounds to +Infinity: the midpoint 2^128 - 2^103 between MAX_VALUE and
   * 2^128 (round-half-even sends it up, since MAX_VALUE has an odd significand).
   */
  static final BigInteger OVF_INT = BigInteger.TWO.pow(128).subtract(BigInteger.TWO.pow(103));

  static final String OVF = OVF_INT.toString();
  static final String OVF_MINUS_1 = OVF_INT.subtract(BigInteger.ONE).toString();
  static final String NINES_39 = "9".repeat(39);
  static final String NINES_40 = "9".repeat(40);

  /** Whitespace tokenizer, no lower-casing, no random checks. */
  static Analyzer ws() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new MockTokenizer(MockTokenizer.WHITESPACE, false));
      }
    };
  }

  /** Whitespace tokenizer + English stop set ("the", "a", ...). */
  static Analyzer wsStop() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new MockTokenizer(MockTokenizer.WHITESPACE, false);
        return new TokenStreamComponents(t, new MockTokenFilter(t, MockTokenFilter.ENGLISH_STOPSET));
      }
    };
  }

  static String top(Throwable t, int n) {
    StringBuilder sb = new StringBuilder();
    StackTraceElement[] st = t.getStackTrace();
    for (int i = 0; i < Math.min(n, st.length); i++) {
      sb.append("\n      at ").append(st[i]);
    }
    return sb.toString();
  }

  static void log(String s) {
    System.out.println("" + s);
  }

  // ---------------------------------------------------------------------------------------
  // 0. JDK facts the finding depends on (pure oracle, no Lucene involved).
  // ---------------------------------------------------------------------------------------

  public void testJdkFloatOverflowBoundary() {
    assertEquals(Float.MAX_VALUE, Float.parseFloat(MAX_DEC), 0f);
    assertEquals(Float.MAX_VALUE, Float.parseFloat(OVF_MINUS_1), 0f);
    assertEquals(Float.POSITIVE_INFINITY, Float.parseFloat(OVF), 0f);
    assertEquals(Float.POSITIVE_INFINITY, Float.parseFloat(NINES_39), 0f);
    assertTrue(Float.isFinite(Float.parseFloat("9".repeat(38))));
    // Underflow side: tiny boosts become 0.0, which BoostQuery accepts.
    assertEquals(0f, Float.parseFloat("0." + "0".repeat(60) + "1"), 0f);
    log("MAX_DEC=" + MAX_DEC + " (" + MAX_DEC.length() + " digits) -> " + Float.parseFloat(MAX_DEC));
    log("OVF-1  =" + OVF_MINUS_1 + " -> " + Float.parseFloat(OVF_MINUS_1));
    log("OVF    =" + OVF + " (" + OVF.length() + " digits) -> " + Float.parseFloat(OVF));
  }

  // ---------------------------------------------------------------------------------------
  // 1. Classic QueryParser.
  // ---------------------------------------------------------------------------------------

  public void testClassicLargestFiniteBoostIsAccepted() throws Exception {
    QueryParser qp = new QueryParser("f", ws());
    for (String b : new String[] {MAX_DEC, OVF_MINUS_1}) {
      Query q = qp.parse("a^" + b);
      assertTrue(q instanceof BoostQuery);
      assertEquals(Float.MAX_VALUE, ((BoostQuery) q).getBoost(), 0f);
    }
    // zero / underflowing boosts are accepted as 0
    Query q0 = qp.parse("a^0." + "0".repeat(60) + "1");
    assertEquals(0f, ((BoostQuery) q0).getBoost(), 0f);
    log("classic: a^<Float.MAX_VALUE as decimal> -> " + qp.parse("a^" + MAX_DEC));
  }

  /** Every boost site of the classic grammar: term, field:term, phrase, group, range, fuzzy, ... */
  public void testClassicOverflowBoostEscapesAsIAE() {
    QueryParser qp = new QueryParser("f", ws());
    String[] inputs = {
      "a^" + OVF,
      "a^" + NINES_39,
      "a^" + NINES_40,
      "f:a^" + OVF,
      "a^" + OVF + ".5",
      "\"a b\"^" + OVF,
      "(a b)^" + OVF,
      "f:(a b)^" + OVF,
      "[a TO b]^" + OVF,
      "{a TO *}^" + OVF,
      "a~^" + OVF,
      "a~1^" + OVF,
      "a^" + OVF + "~1",
      "ab*^" + OVF,
      "a?c^" + OVF,
      "/ab/^" + OVF,
      "*:*^" + OVF,
      "x AND a^" + OVF,
    };
    List<String> lines = new ArrayList<>();
    for (String in : inputs) {
      Throwable t = expectThrows(Throwable.class, () -> qp.parse(in));
      assertEquals("input: " + in, IllegalArgumentException.class, t.getClass());
      assertFalse(t instanceof ParseException);
      assertEquals("boost must be a positive float, got Infinity", t.getMessage());
      StackTraceElement s0 = t.getStackTrace()[0];
      StackTraceElement s1 = t.getStackTrace()[1];
      assertEquals(BoostQuery.class.getName(), s0.getClassName());
      assertEquals("<init>", s0.getMethodName());
      assertEquals("org.apache.lucene.queryparser.classic.QueryParserBase", s1.getClassName());
      assertEquals("handleBoost", s1.getMethodName());
      lines.add(
          "  classic parse(\""
              + abbreviate(in)
              + "\") -> "
              + t.getClass().getName()
              + ": "
              + t.getMessage());
    }
    for (String l : lines) log(l);
    Throwable t = expectThrows(Throwable.class, () -> qp.parse("a^" + NINES_40));
    log("classic stack (top 4):" + top(t, 4));
  }

  /** The documented contract: parse() @throws ParseException if the parsing fails. */
  public void testContractClassicThrowsParseException() {
    QueryParser qp = new QueryParser("f", ws());
    expectThrows(ParseException.class, () -> qp.parse("a^" + NINES_40));
  }

  /**
   * When the analyzer removes the term (stop word), handleBoost skips BoostQuery and the huge
   * boost is silently ignored, so whether the IAE happens depends on the analyzer.
   */
  public void testClassicStopWordMasksTheOverflow() throws Exception {
    QueryParser qp = new QueryParser("f", wsStop());
    Query q1 = qp.parse("the^" + NINES_40);
    assertTrue(q1 instanceof BooleanQuery);
    assertEquals(0, ((BooleanQuery) q1).clauses().size());
    Query q2 = qp.parse("the^" + NINES_40 + " b");
    log("classic+stop: the^9x40 -> '" + q1 + "' ; 'the^9x40 b' -> '" + q2 + "'");
    expectThrows(IllegalArgumentException.class, () -> qp.parse("b^" + NINES_40));
  }

  /** Subclasses of the classic parser share handleBoost. */
  public void testClassicSubclassesShareIt() {
    MultiFieldQueryParser mf = new MultiFieldQueryParser(new String[] {"f", "g"}, ws());
    Throwable t1 = expectThrows(Throwable.class, () -> mf.parse("a^" + NINES_40));
    assertEquals(IllegalArgumentException.class, t1.getClass());
    ComplexPhraseQueryParser cp = new ComplexPhraseQueryParser("f", ws());
    Throwable t2 = expectThrows(Throwable.class, () -> cp.parse("\"a b\"^" + NINES_40));
    assertEquals(IllegalArgumentException.class, t2.getClass());
    log("MultiFieldQueryParser   -> " + t1);
    log("ComplexPhraseQueryParser-> " + t2);
  }

  // ---------------------------------------------------------------------------------------
  // 2. Flexible StandardQueryParser (+ PrecedenceQueryParser, which extends it).
  // ---------------------------------------------------------------------------------------

  public void testFlexibleSyntaxPhaseAcceptsInfinity() throws Exception {
    QueryNode n = new StandardSyntaxParser().parse("a^" + NINES_40, "f");
    assertTrue(n instanceof BoostQueryNode);
    assertEquals(Float.POSITIVE_INFINITY, ((BoostQueryNode) n).getValue(), 0f);
    log("flexible syntax phase: BoostQueryNode value=" + ((BoostQueryNode) n).getValue());
  }

  public void testFlexibleLargestFiniteBoostIsAccepted() throws Exception {
    StandardQueryParser qp = new StandardQueryParser(ws());
    Query q = qp.parse("a^" + MAX_DEC, "f");
    assertTrue(q instanceof BoostQuery);
    assertEquals(Float.MAX_VALUE, ((BoostQuery) q).getBoost(), 0f);
  }

  public void testFlexibleOverflowBoostEscapesAsIAE() {
    StandardQueryParser qp = new StandardQueryParser(ws());
    String[] inputs = {
      "a^" + OVF,
      "a^" + NINES_40,
      "f:a^" + OVF,
      "\"a b\"^" + OVF,
      "(a b)^" + OVF,
      "[a TO b]^" + OVF,
      "a~1^" + OVF,
      "ab*^" + OVF,
      "/ab/^" + OVF,
      "x AND a^" + OVF,
    };
    List<String> lines = new ArrayList<>();
    for (String in : inputs) {
      Throwable t = expectThrows(Throwable.class, () -> qp.parse(in, "f"));
      assertEquals("input: " + in, IllegalArgumentException.class, t.getClass());
      assertFalse(t instanceof QueryNodeException);
      assertEquals("boost must be a positive float, got Infinity", t.getMessage());
      StackTraceElement s0 = t.getStackTrace()[0];
      StackTraceElement s1 = t.getStackTrace()[1];
      assertEquals(BoostQuery.class.getName(), s0.getClassName());
      assertEquals(
          "org.apache.lucene.queryparser.flexible.standard.builders.BoostQueryNodeBuilder",
          s1.getClassName());
      lines.add(
          "  flexible parse(\""
              + abbreviate(in)
              + "\") -> "
              + t.getClass().getName()
              + ": "
              + t.getMessage());
    }
    for (String l : lines) log(l);
    Throwable t = expectThrows(Throwable.class, () -> qp.parse("a^" + NINES_40, "f"));
    log("flexible stack (top 4):" + top(t, 4));

    PrecedenceQueryParser pqp = new PrecedenceQueryParser(ws());
    Throwable tp = expectThrows(Throwable.class, () -> pqp.parse("a^" + NINES_40, "f"));
    assertEquals(IllegalArgumentException.class, tp.getClass());
    log("PrecedenceQueryParser -> " + tp);
  }

  /** The documented contract: parse() @throws QueryNodeException if something goes wrong. */
  public void testContractFlexibleThrowsQueryNodeException() {
    StandardQueryParser qp = new StandardQueryParser(ws());
    expectThrows(QueryNodeException.class, () -> qp.parse("a^" + NINES_40, "f"));
  }

  // ---------------------------------------------------------------------------------------
  // 3. Context: the surround parser DOES validate boosts in its grammar (f <= 0 ->
  //    ParseException), but it also misses Infinity; its IAE is deferred to query construction.
  // ---------------------------------------------------------------------------------------

  public void testSurroundContext() throws Exception {
    // surround validates boost <= 0 as a ParseException
    expectThrows(
        org.apache.lucene.queryparser.surround.parser.ParseException.class,
        () -> org.apache.lucene.queryparser.surround.parser.QueryParser.parse("a^0"));
    SrndQuery sq = org.apache.lucene.queryparser.surround.parser.QueryParser.parse("a^" + NINES_40);
    assertEquals(Float.POSITIVE_INFINITY, sq.getWeight(), 0f);
    Throwable t =
        expectThrows(
            Throwable.class, () -> sq.makeLuceneQueryField("f", new BasicQueryFactory()));
    assertEquals(IllegalArgumentException.class, t.getClass());
    // two finite weights whose product overflows
    SrndQuery sq2 =
        org.apache.lucene.queryparser.surround.parser.QueryParser.parse(
            "a^" + "9".repeat(20) + "^" + "9".repeat(20));
    log(
        "surround: a^0 -> ParseException; a^9x40 parses (weight="
            + sq.getWeight()
            + "), makeLuceneQueryField -> "
            + t
            + "; a^9x20^9x20 weight="
            + sq2.getWeight());
  }

  static String abbreviate(String s) {
    return s.replace(OVF, "<2^128-2^103>")
        .replace(NINES_40, "<9 x40>")
        .replace(NINES_39, "<9 x39>");
  }
}
