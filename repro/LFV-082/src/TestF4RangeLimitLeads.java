import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queries.function.FunctionRangeQuery;
import org.apache.lucene.queries.function.valuesource.IntFieldSource;
import org.apache.lucene.queries.function.valuesource.LongFieldSource;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.config.PointsConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * side leads next to a related finding (flexible parser exclusive int/long bounds):
 * (a) exclusive OPEN bound drops the type limit, (b) out-of-range literals are narrowed by
 * Number.intValue(), and (c) FunctionRangeQuery over Int/LongFieldSource saturates an exclusive
 * bound at the type limit. Each query prints "query -> parsed hits expected".
 */
public class TestF4RangeLimitLeads extends LuceneTestCase {

  static final int[] INTS = {Integer.MIN_VALUE, -5, 0, 5, Integer.MAX_VALUE};
  static final float[] FLOATS = {Float.NEGATIVE_INFINITY, -1f, 0f, 1f, Float.POSITIVE_INFINITY};

  static Directory dir;
  static DirectoryReader reader;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int v : INTS) {
        Document d = new Document();
        d.add(new IntPoint("i", v));
        d.add(new NumericDocValuesField("n", v));
        w.addDocument(d);
      }
      for (float f : FLOATS) {
        Document d = new Document();
        d.add(new FloatPoint("x", f));
        w.addDocument(d);
      }
      Document d = new Document();
      d.add(new NumericDocValuesField("m", Long.MAX_VALUE));
      w.addDocument(d);
    }
    reader = DirectoryReader.open(dir);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  static StandardQueryParser parser() {
    StandardQueryParser p = new StandardQueryParser(new StandardAnalyzer());
    Map<String, PointsConfig> pc = new HashMap<>();
    pc.put("i", new PointsConfig(NumberFormat.getIntegerInstance(Locale.ROOT), Integer.class));
    pc.put("x", new PointsConfig(NumberFormat.getNumberInstance(Locale.ROOT), Float.class));
    p.setPointsConfigMap(pc);
    return p;
  }

  static String check(String qs, int expected, StringBuilder bad) throws Exception {
    Query q = parser().parse(qs, "i");
    int hits = new IndexSearcher(reader).count(q);
    String line = String.format(Locale.ROOT, "  %-26s -> %-40s hits=%d expected=%d %s", qs, q, hits, expected,
        hits == expected ? "OK" : "MISMATCH");
    System.out.println(line);
    if (hits != expected) bad.append(qs).append(" ; ");
    return line;
  }

  /** (a) exclusive open bound: '{*' should be unbounded (TermRangeQuery ignores exclusivity of '*') */
  public void testExclusiveOpenBound() throws Exception {
    StringBuilder bad = new StringBuilder();
    System.out.println("[open bound]");
    check("i:[* TO 0]", 3, bad); // control
    check("i<=0", 3, bad); // control (the '<' syntax uses an inclusive '*')
    check("i:{* TO 0]", 3, bad);
    check("i:{* TO *}", 5, bad);
    check("x:[* TO 0]", 3, bad); // control
    check("x:{* TO 0]", 3, bad);
    assertEquals("", bad.toString());
  }

  /** (b) out-of-range int literals */
  public void testOutOfRangeLiteralNarrowing() throws Exception {
    StringBuilder bad = new StringBuilder();
    System.out.println("[narrowing]");
    check("i:[2147483648 TO *]", 0, bad);
    check("i>=2147483648", 0, bad);
    check("i:2147483648", 0, bad);
    check("i:[* TO -2147483649]", 0, bad);
    assertEquals("", bad.toString());
  }

  /** run-3 #12 itself, as a control for the fix columns */
  public void testRun3Bug12Control() throws Exception {
    StringBuilder bad = new StringBuilder();
    System.out.println("[#12]");
    check("i:{2147483647 TO *]", 0, bad);
    check("i:[* TO -2147483648}", 0, bad);
    assertEquals("", bad.toString());
  }

  /** (c) FunctionRangeQuery with exclusive bounds at the type limits */
  public void testFunctionRangeAtLimits() throws Exception {
    IndexSearcher s = new IndexSearcher(reader);
    StringBuilder bad = new StringBuilder();
    Object[][] cases = {
      {new FunctionRangeQuery(new IntFieldSource("n"), "2147483647", null, false, true), 0, "n > MAX32"},
      {new FunctionRangeQuery(new IntFieldSource("n"), null, "-2147483648", true, false), 0, "n < MIN32"},
      {new FunctionRangeQuery(new IntFieldSource("n"), "2147483646", null, false, true), 1, "n > MAX32-1 (control)"},
      {new FunctionRangeQuery(new LongFieldSource("m"), "9223372036854775807", null, false, true), 0, "m > MAX64"},
    };
    System.out.println("[FunctionRangeQuery]");
    for (Object[] c : cases) {
      int hits = s.count((Query) c[0]);
      System.out.println("  " + c[2] + " hits=" + hits + " expected=" + c[1]);
      if (hits != (int) c[1]) bad.append(c[2]).append(" ; ");
    }
    assertEquals("", bad.toString());
  }
}
