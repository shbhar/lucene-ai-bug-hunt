import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.config.PointsConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * Independent check: StandardQueryParser exclusive float/double bounds at signed zero, compared
 * with the semantics FloatPoint/DoublePoint document (Float.compare / Double.compare order).
 */
public class TestFlexParserSignedZeroExclusive extends LuceneTestCase {
  // labels in index order
  static final String[] LABELS = {"-1", "-0.0", "+0.0", "1", "2"};
  static final float[] FVALS = {-1f, -0f, +0f, 1f, 2f};
  static final double[] DVALS = {-1d, -0d, +0d, 1d, 2d};

  static Directory dir;
  static DirectoryReader reader;
  static IndexSearcher searcher;

  @BeforeClass
  public static void build() throws Exception {
    dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < LABELS.length; i++) {
        Document d = new Document();
        d.add(new FloatPoint("f", FVALS[i]));
        d.add(new DoublePoint("d", DVALS[i]));
        d.add(new StoredField("label", LABELS[i]));
        w.addDocument(d);
      }
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @AfterClass
  public static void close() throws Exception {
    reader.close();
    dir.close();
    reader = null;
    dir = null;
    searcher = null;
  }

  static StandardQueryParser parser() {
    StandardQueryParser qp = new StandardQueryParser(new StandardAnalyzer());
    NumberFormat nf = NumberFormat.getInstance(Locale.ROOT);
    Map<String, PointsConfig> pc = new HashMap<>();
    pc.put("f", new PointsConfig(nf, Float.class));
    pc.put("d", new PointsConfig(nf, Double.class));
    qp.setPointsConfigMap(pc);
    return qp;
  }

  static TreeSet<String> hits(Query q) throws Exception {
    TreeSet<String> out = new TreeSet<>();
    StoredFields sf = searcher.storedFields();
    for (ScoreDoc sd : searcher.search(q, 100).scoreDocs) {
      out.add(sf.document(sd.doc).get("label"));
    }
    return out;
  }

  static TreeSet<String> parsedHits(String qs) throws Exception {
    Query q = parser().parse(qs, "f");
    TreeSet<String> h = hits(q);
    System.out.println("  " + qs + "  ->  parsed=" + q + "  hits=" + h);
    return h;
  }

  static TreeSet<String> minus(TreeSet<String> a, TreeSet<String> b) {
    TreeSet<String> r = new TreeSet<>(a);
    r.removeAll(b);
    return r;
  }

  public void testFloatLowerExclusiveAtNegativeZero() throws Exception {
    System.out.println("float, exclusive lower bound -0.0:");
    TreeSet<String> parsed = parsedHits("f:{-0.0 TO *]");
    TreeSet<String> inclusive = parsedHits("f:[-0.0 TO *]");
    TreeSet<String> exact = hits(FloatPoint.newExactQuery("f", -0f));
    TreeSet<String> api =
        hits(FloatPoint.newRangeQuery("f", FloatPoint.nextUp(-0f), Float.POSITIVE_INFINITY));
    System.out.println(
        "  FloatPoint.newRangeQuery(nextUp(-0f), +Inf) hits=" + api + " ; [..] minus exact(-0f)="
            + minus(inclusive, exact));
    assertEquals(minus(inclusive, exact), api); // API helper satisfies the identity
    assertEquals("parser {-0.0 TO *] vs FloatPoint.nextUp semantics", api, parsed);
  }

  public void testFloatUpperExclusiveAtPositiveZero() throws Exception {
    System.out.println("float, exclusive upper bound 0 (plain '0', no negative zero in the query):");
    TreeSet<String> parsed = parsedHits("f:[* TO 0}");
    TreeSet<String> inclusive = parsedHits("f:[* TO 0]");
    TreeSet<String> exact = hits(FloatPoint.newExactQuery("f", +0f));
    TreeSet<String> api =
        hits(FloatPoint.newRangeQuery("f", Float.NEGATIVE_INFINITY, FloatPoint.nextDown(+0f)));
    System.out.println(
        "  FloatPoint.newRangeQuery(-Inf, nextDown(+0f)) hits=" + api + " ; [..] minus exact(+0f)="
            + minus(inclusive, exact));
    assertEquals(minus(inclusive, exact), api);
    assertEquals("parser [* TO 0} vs FloatPoint.nextDown semantics", api, parsed);
  }

  public void testDoubleLowerExclusiveAtNegativeZero() throws Exception {
    System.out.println("double, exclusive lower bound -0.0:");
    TreeSet<String> parsed = parsedHits("d:{-0.0 TO *]");
    TreeSet<String> inclusive = parsedHits("d:[-0.0 TO *]");
    TreeSet<String> exact = hits(DoublePoint.newExactQuery("d", -0d));
    TreeSet<String> api =
        hits(DoublePoint.newRangeQuery("d", DoublePoint.nextUp(-0d), Double.POSITIVE_INFINITY));
    System.out.println("  DoublePoint.newRangeQuery(nextUp(-0d), +Inf) hits=" + api);
    assertEquals(minus(inclusive, exact), api);
    assertEquals("parser {-0.0 TO *] vs DoublePoint.nextUp semantics", api, parsed);
  }

  public void testDoubleUpperExclusiveAtPositiveZero() throws Exception {
    System.out.println("double, exclusive upper bound 0:");
    TreeSet<String> parsed = parsedHits("d:[* TO 0}");
    TreeSet<String> inclusive = parsedHits("d:[* TO 0]");
    TreeSet<String> exact = hits(DoublePoint.newExactQuery("d", +0d));
    TreeSet<String> api =
        hits(DoublePoint.newRangeQuery("d", Double.NEGATIVE_INFINITY, DoublePoint.nextDown(+0d)));
    System.out.println("  DoublePoint.newRangeQuery(-Inf, nextDown(+0d)) hits=" + api);
    assertEquals(minus(inclusive, exact), api);
    assertEquals("parser [* TO 0} vs DoublePoint.nextDown semantics", api, parsed);
  }

  /** Control: away from zero the parser's exclusive bounds agree with the identity. */
  public void testControlNonZeroBoundsAgree() throws Exception {
    System.out.println("control (non-zero and zero bounds where Math.* and FloatPoint.* agree):");
    String[][] cases = {
      // query, inclusive form, exact-bound value
      {"f:{-1 TO *]", "f:[-1 TO *]", "-1"},
      {"f:{1 TO *]", "f:[1 TO *]", "1"},
      {"f:{0 TO *]", "f:[0 TO *]", "0"},
      {"f:[* TO -0.0}", "f:[* TO -0.0]", "-0.0"},
      {"f:[* TO 1}", "f:[* TO 1]", "1"},
      {"f:[* TO 2}", "f:[* TO 2]", "2"},
    };
    for (String[] c : cases) {
      float b = Float.parseFloat(c[2]);
      TreeSet<String> expected = minus(parsedHits(c[1]), hits(FloatPoint.newExactQuery("f", b)));
      assertEquals(c[0], expected, parsedHits(c[0]));
    }
  }

  /**
   * Order-independent check: whatever order one assumes for -0.0/+0.0 (Float.compare or IEEE ==),
   * "x < 0" OR "x >= 0" must match every non-NaN document. Same for "x > -0.0" OR "x <= -0.0".
   */
  public void testPartitionLosesSignedZeroDocs() throws Exception {
    System.out.println("partition identities (must return all 5 docs):");
    String[] qs = {
      "f:[* TO 0} OR f:[0 TO *]",
      "f:{-0.0 TO *] OR f:[* TO -0.0]",
      "d:[* TO 0} OR d:[0 TO *]",
      "d:{-0.0 TO *] OR d:[* TO -0.0]",
    };
    TreeSet<String> all = new TreeSet<>(java.util.Arrays.asList(LABELS));
    StringBuilder bad = new StringBuilder();
    for (String q : qs) {
      TreeSet<String> h = parsedHits(q);
      if (h.equals(all) == false) {
        bad.append(q).append(" misses ").append(minus(all, h)).append("; ");
      }
    }
    assertEquals("", bad.toString());
  }
}
