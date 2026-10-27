import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongPredicate;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.config.PointsConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: StandardQueryParser + PointsConfig (configured exactly like Luke's SearchImpl,
 * NumberFormat.getIntegerInstance(Locale.ROOT)) on exclusive int/long bounds at the type limits.
 *
 * <p>Oracle = the documented semantics in StandardQueryParser javadoc ("{2.5 TO *] selects
 * documents whose field is larger than 2.5"), evaluated by brute force over the stored values.
 */
public class TestVerifyExclusivePointOverflow extends LuceneTestCase {

  static final long[] INT_VALUES = {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE};
  static final long[] LONG_VALUES = {Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE};

  static StandardQueryParser luKeLikeParser() {
    StandardQueryParser qp = new StandardQueryParser();
    qp.setPointsConfigMap(
        Map.of(
            "i", new PointsConfig(NumberFormat.getIntegerInstance(Locale.ROOT), Integer.class),
            "l", new PointsConfig(NumberFormat.getIntegerInstance(Locale.ROOT), Long.class)));
    return qp;
  }

  static Directory buildIndex() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int d = 0; d < INT_VALUES.length; d++) {
        Document doc = new Document();
        doc.add(new IntPoint("i", (int) INT_VALUES[d]));
        doc.add(new LongPoint("l", LONG_VALUES[d]));
        doc.add(new StoredField("id", d));
        w.addDocument(doc);
      }
    }
    return dir;
  }

  record Case(String query, long[] values, LongPredicate semantics) {}

  static long expected(Case c) {
    long n = 0;
    for (long v : c.values) if (c.semantics.test(v)) n++;
    return n;
  }

  static List<Case> overflowCases() {
    final long IMAX = Integer.MAX_VALUE, IMIN = Integer.MIN_VALUE;
    final long LMAX = Long.MAX_VALUE, LMIN = Long.MIN_VALUE;
    List<Case> cs = new ArrayList<>();
    // bracket syntax
    cs.add(new Case("i:{2147483647 TO *]", INT_VALUES, v -> v > IMAX));
    cs.add(new Case("i:[* TO -2147483648}", INT_VALUES, v -> v < IMIN));
    cs.add(new Case("l:{9223372036854775807 TO *]", LONG_VALUES, v -> v > LMAX));
    cs.add(new Case("l:[* TO -9223372036854775808}", LONG_VALUES, v -> v < LMIN));
    // comparison-operator syntax (StandardSyntaxParser FieldRangeExpr: '>' => lowerInclusive=false)
    cs.add(new Case("i>2147483647", INT_VALUES, v -> v > IMAX));
    cs.add(new Case("i<\"-2147483648\"", INT_VALUES, v -> v < IMIN));
    cs.add(new Case("l>9223372036854775807", LONG_VALUES, v -> v > LMAX));
    cs.add(new Case("l<\"-9223372036854775808\"", LONG_VALUES, v -> v < LMIN));
    // both bounds exclusive at the limits: {MAX TO MAX} and {MIN TO MIN}
    cs.add(new Case("i:{2147483647 TO 2147483647}", INT_VALUES, v -> false));
    return cs;
  }

  static List<Case> controlCases() {
    final long IMAX = Integer.MAX_VALUE, IMIN = Integer.MIN_VALUE;
    final long LMAX = Long.MAX_VALUE;
    List<Case> cs = new ArrayList<>();
    cs.add(new Case("i:{2147483646 TO *]", INT_VALUES, v -> v > IMAX - 1));
    cs.add(new Case("i:[* TO -2147483647}", INT_VALUES, v -> v < IMIN + 1));
    cs.add(new Case("i>0", INT_VALUES, v -> v > 0));
    cs.add(new Case("i<\"0\"", INT_VALUES, v -> v < 0));
    cs.add(new Case("i:[2147483647 TO *]", INT_VALUES, v -> v >= IMAX));
    cs.add(new Case("l:{9223372036854775806 TO *]", LONG_VALUES, v -> v > LMAX - 1));
    cs.add(new Case("l:{0 TO 1]", LONG_VALUES, v -> v > 0 && v <= 1));
    return cs;
  }

  static long run(IndexSearcher s, StandardQueryParser qp, Case c, StringBuilder log)
      throws Exception {
    Query q = qp.parse(c.query, "i");
    long hits = s.count(q);
    log.append(
        String.format(
            Locale.ROOT,
            "  %-34s -> %-40s hits=%d expected=%d %s%n",
            c.query,
            q,
            hits,
            expected(c),
            hits == expected(c) ? "OK" : "MISMATCH"));
    return hits;
  }

  /** Control: non-boundary exclusive bounds are exact (shows the oracle and setup are sound). */
  public void testControlsAreExact() throws Exception {
    try (Directory dir = buildIndex();
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      StandardQueryParser qp = luKeLikeParser();
      StringBuilder log = new StringBuilder("controls:\n");
      for (Case c : controlCases()) {
        assertEquals(c.query, expected(c), run(s, qp, c, log));
      }
      System.out.print(log);
    }
  }

  /** The candidate bug: exclusive bound at MAX/MIN wraps and matches every document. */
  public void testExclusiveBoundAtTypeLimit() throws Exception {
    try (Directory dir = buildIndex();
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      StandardQueryParser qp = luKeLikeParser();
      StringBuilder log = new StringBuilder("overflow cases:\n");
      List<String> failures = new ArrayList<>();
      for (Case c : overflowCases()) {
        long hits = run(s, qp, c, log);
        if (hits != expected(c)) failures.add(c.query + " hits=" + hits + " expected=" + expected(c));
      }
      System.out.print(log);
      assertTrue("mismatches: " + failures, failures.isEmpty());
    }
  }

  /**
   * Sibling observations (printed, not asserted): (a) exclusive OPEN bound '{* TO 0]' drops docs at
   * Integer.MIN_VALUE (the pre-points LegacyNumericRangeQuery ignored exclusivity on null bounds);
   * (b) out-of-range int literal is narrowed by Number.intValue() in PointRangeQueryNodeProcessor.
   */
  public void testSiblingObservations() throws Exception {
    try (Directory dir = buildIndex();
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      StandardQueryParser qp = luKeLikeParser();
      StringBuilder log = new StringBuilder("sibling observations (not asserted):\n");
      final long IMAX = Integer.MAX_VALUE;
      run(s, qp, new Case("i:{* TO 0]", INT_VALUES, v -> v <= 0), log);
      run(s, qp, new Case("i:[0 TO *}", INT_VALUES, v -> v >= 0), log);
      run(s, qp, new Case("i:{* TO *}", INT_VALUES, v -> true), log);
      run(s, qp, new Case("i:[2147483648 TO *]", INT_VALUES, v -> v >= IMAX + 1), log);
      run(s, qp, new Case("i>=2147483648", INT_VALUES, v -> v >= IMAX + 1), log);
      run(s, qp, new Case("i:2147483648", INT_VALUES, v -> v == IMAX + 1), log);
      System.out.print(log);
    }
  }
}
