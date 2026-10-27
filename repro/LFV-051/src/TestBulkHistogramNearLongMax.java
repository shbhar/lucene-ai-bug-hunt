import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.internal.hppc.LongIntHashMap;
import org.apache.lucene.sandbox.facet.plain.histograms.HistogramCollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check of the sandbox HistogramCollectorManager point-tree bulk path
 * (PointTreeBulkCollector) for long values near Long.MAX_VALUE.
 *
 * <p>Each document gets two single-valued fields with the same value:
 *
 * <ul>
 *   <li>"p": LongPoint + NumericDocValuesField (eligible for the point-tree bulk path)
 *   <li>"d": NumericDocValuesField only (no points, so the doc-values path is always used)
 * </ul>
 *
 * The expected histogram is computed by brute force as floorDiv(value, width).
 */
public class TestBulkHistogramNearLongMax extends LuceneTestCase {

  private static final long MAX = Long.MAX_VALUE;

  private static final class Idx implements AutoCloseable {
    final Directory dir;
    final DirectoryReader reader;
    final IndexSearcher searcher;

    Idx(long[] values, boolean intField) throws IOException {
      dir = new ByteBuffersDirectory();
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (long v : values) {
          Document doc = new Document();
          if (intField) {
            doc.add(new IntPoint("p", (int) v));
          } else {
            doc.add(new LongPoint("p", v));
          }
          doc.add(new NumericDocValuesField("p", v));
          doc.add(new NumericDocValuesField("d", v));
          w.addDocument(doc);
        }
        w.commit();
      }
      reader = DirectoryReader.open(dir);
      assertEquals("expected a single segment", 1, reader.leaves().size());
      searcher = new IndexSearcher(reader); // no executor: deterministic, single slice
    }

    LongIntHashMap hist(Query q, String field, long width) throws IOException {
      return searcher.search(q, new HistogramCollectorManager(field, width));
    }

    @Override
    public void close() throws IOException {
      reader.close();
      dir.close();
    }
  }

  private static LongIntHashMap expected(long[] values, long lo, long hi, long width) {
    LongIntHashMap m = new LongIntHashMap();
    for (long v : values) {
      if (v >= lo && v <= hi) {
        m.addTo(Math.floorDiv(v, width), 1);
      }
    }
    return m;
  }

  private static long[] range(long from, int n) {
    long[] a = new long[n];
    for (int i = 0; i < n; i++) {
      a[i] = from + i;
    }
    return a;
  }

  private static long[] repeat(long v, int n) {
    long[] a = new long[n];
    java.util.Arrays.fill(a, v);
    return a;
  }

  private static long[] concat(long[] a, long[] b) {
    long[] c = java.util.Arrays.copyOf(a, a.length + b.length);
    System.arraycopy(b, 0, c, a.length, b.length);
    return c;
  }

  /** Runs one case; returns a report line, and records mismatches of the "p" field. */
  private static String check(
      String name, long[] values, Query q, long lo, long hi, long width, List<String> failures)
      throws IOException {
    try (Idx idx = new Idx(values, false)) {
      LongIntHashMap exp = expected(values, lo, hi, width);
      LongIntHashMap bulk = idx.hist(q, "p", width);
      LongIntHashMap dv = idx.hist(q, "d", width);
      // Oracle sanity: the doc-values-only field must always match brute force.
      assertEquals(name + ": doc-values-only path disagrees with brute force", exp, dv);
      String line =
          name + "  width=" + width + "  expected=" + exp + "  pointField=" + bulk + "  dvOnly=" + dv;
      System.out.println(line);
      if (!exp.equals(bulk)) {
        failures.add(line);
      }
      return line;
    }
  }

  /** Case A: every doc has value Long.MAX_VALUE, width 2 (smallest allowed width). */
  public void testAllLongMaxWidth2() throws IOException {
    List<String> f = new ArrayList<>();
    long[] v = repeat(MAX, 10);
    check("A all=MAX", v, MatchAllDocsQuery.INSTANCE, Long.MIN_VALUE, MAX, 2, f);
    assertTrue("bulk path lost counts: " + f, f.isEmpty());
  }

  /**
   * Case B: every doc has value Long.MAX_VALUE - 1. Segment max is NOT Long.MAX_VALUE, so `leafMax
   * + 1` does not overflow; only the bucket-end computation `(floorDiv(v,w)+1)*w` overflows.
   */
  public void testAllLongMaxMinus1Width2() throws IOException {
    List<String> f = new ArrayList<>();
    long[] v = repeat(MAX - 1, 10);
    check("B all=MAX-1", v, MatchAllDocsQuery.INSTANCE, Long.MIN_VALUE, MAX, 2, f);
    assertTrue("bulk path lost counts: " + f, f.isEmpty());
  }

  /**
   * Case C: 2000 consecutive values MAX-2000..MAX-1, width 1000 -> 3 buckets; only the last
   * (partial) bucket's end overflows.
   */
  public void testTopBucketLostOthersKept() throws IOException {
    List<String> f = new ArrayList<>();
    long[] v = range(MAX - 2000, 2000);
    check("C MAX-2000..MAX-1", v, MatchAllDocsQuery.INSTANCE, Long.MIN_VALUE, MAX, 1000, f);
    assertTrue("bulk path lost counts: " + f, f.isEmpty());
  }

  /** Case D: like C plus one doc at exactly Long.MAX_VALUE (so `leafMax + 1` wraps). */
  public void testWholeSegmentLostWhenMaxIsLongMax() throws IOException {
    List<String> f = new ArrayList<>();
    long[] v = concat(range(MAX - 2000, 2000), new long[] {MAX});
    check("D MAX-2000..MAX", v, MatchAllDocsQuery.INSTANCE, Long.MIN_VALUE, MAX, 1000, f);
    assertTrue("bulk path lost counts: " + f, f.isEmpty());
  }

  /** Case E: PointRangeQuery path (not match-all), data of case C, open-ended upper bound. */
  public void testPointRangeQueryPath() throws IOException {
    List<String> f = new ArrayList<>();
    long[] v = range(MAX - 2000, 2000);
    long lo = MAX - 1500;
    Query q = LongPoint.newRangeQuery("p", lo, MAX);
    check("E PRQ[MAX-1500,MAX]", v, q, lo, MAX, 1000, f);
    assertTrue("bulk path lost counts: " + f, f.isEmpty());
  }

  /** Case F: the standard LongField (points + SORTED_NUMERIC doc values), match-all query. */
  public void testLongFieldMatchAll() throws IOException {
    long[] v = repeat(MAX, 10);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (long x : v) {
          Document doc = new Document();
          doc.add(new LongField("lf", x, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LongIntHashMap exp = expected(v, Long.MIN_VALUE, MAX, 2);
        LongIntHashMap got =
            new IndexSearcher(r).search(MatchAllDocsQuery.INSTANCE, new HistogramCollectorManager("lf", 2));
        System.out.println("F LongField all=MAX width=2 expected=" + exp + " got=" + got);
        assertEquals(exp, got);
      }
    }
  }

  /**
   * Case G: a realistic "sentinel" layout. Segment 1 holds ordinary values 0..4999, segment 2 holds
   * 100 docs whose value is the sentinel Long.MAX_VALUE. With two segments the sentinel segment is
   * bulk-collected and its 100 docs vanish; after forceMerge(1) the bucket span is too wide for the
   * bulk path, so the doc-values path runs and the answer is correct. Same docs, different answer.
   */
  public void testAnswerDependsOnSegmentation() throws IOException {
    long[] seg1 = range(0, 5000);
    long[] seg2 = repeat(MAX, 100);
    long[] all = concat(seg1, seg2);
    LongIntHashMap exp = expected(all, Long.MIN_VALUE, MAX, 1000);
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (long[] seg : new long[][] {seg1, seg2}) {
          for (long x : seg) {
            Document doc = new Document();
            doc.add(new LongField("lf", x, Field.Store.NO));
            w.addDocument(doc);
          }
          w.commit();
        }
        LongIntHashMap twoSegs;
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals(2, r.leaves().size());
          twoSegs =
              new IndexSearcher(r).search(MatchAllDocsQuery.INSTANCE, new HistogramCollectorManager("lf", 1000));
        }
        w.getConfig().setMergePolicy(new TieredMergePolicy()); // live setting
        w.forceMerge(1);
        LongIntHashMap oneSeg;
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals(1, r.leaves().size());
          oneSeg =
              new IndexSearcher(r).search(MatchAllDocsQuery.INSTANCE, new HistogramCollectorManager("lf", 1000));
        }
        System.out.println("G expected=" + exp + "\n  2 segments=" + twoSegs + "\n  1 segment =" + oneSeg);
        assertEquals("after forceMerge(1)", exp, oneSeg);
        assertEquals("with 2 segments", exp, twoSegs);
      }
    }
  }

  /** Controls: same shapes shifted away from Long.MAX_VALUE, and the Long.MIN_VALUE side. */
  public void testControlsAreCorrect() throws IOException {
    List<String> f = new ArrayList<>();
    check(
        "ctl shifted C",
        range(MAX - 1_000_000_000L, 2000),
        MatchAllDocsQuery.INSTANCE,
        Long.MIN_VALUE,
        MAX,
        1000,
        f);
    check("ctl all=MAX-2", repeat(MAX - 2, 10), MatchAllDocsQuery.INSTANCE, Long.MIN_VALUE, MAX, 2, f);
    check(
        "ctl MIN..MIN+1999",
        range(Long.MIN_VALUE, 2000),
        MatchAllDocsQuery.INSTANCE,
        Long.MIN_VALUE,
        MAX,
        1000,
        f);
    check("ctl all=MIN", repeat(Long.MIN_VALUE, 10), MatchAllDocsQuery.INSTANCE, Long.MIN_VALUE, MAX, 2, f);
    // IntPoint (4-byte) is widened to long before the arithmetic, so it cannot overflow.
    long[] iv = range(Integer.MAX_VALUE - 1999L, 2000);
    try (Idx idx = new Idx(iv, true)) {
      LongIntHashMap exp = expected(iv, Long.MIN_VALUE, MAX, 1000);
      LongIntHashMap got = idx.hist(MatchAllDocsQuery.INSTANCE, "p", 1000);
      System.out.println("ctl IntPoint INT_MAX-1999..INT_MAX expected=" + exp + " pointField=" + got);
      if (!exp.equals(got)) {
        f.add("IntPoint control");
      }
    }
    assertTrue("controls failed: " + f, f.isEmpty());
  }

  /**
   * Characterization sweep: for several widths, 10 docs all equal to v for v in the last few
   * values below Long.MAX_VALUE. Prints which v lose their bucket and checks the predicted rule:
   * counts are lost iff v == Long.MAX_VALUE or (floorDiv(v,w)+1)*w overflows (v is in the topmost
   * bucket).
   */
  public void testSweepMatchesOverflowPrediction() throws IOException {
    long[] widths = {2, 3, 7, 10, 1000, 1L << 20, 1L << 62};
    int lost = 0, total = 0, predictionErrors = 0;
    for (long w : widths) {
      long topBucketStart = Math.floorDiv(MAX, w) * w; // first value whose bucket end overflows
      long[] probes = {
        MAX, MAX - 1, MAX - 2, MAX - 9, topBucketStart, topBucketStart - 1, topBucketStart - 2
      };
      for (long v : probes) {
        long[] vals = repeat(v, 10);
        try (Idx idx = new Idx(vals, false)) {
          LongIntHashMap exp = expected(vals, Long.MIN_VALUE, MAX, w);
          LongIntHashMap got = idx.hist(MatchAllDocsQuery.INSTANCE, "p", w);
          boolean isLost = !exp.equals(got);
          boolean bucketEndOverflows;
          try {
            Math.multiplyExact(Math.floorDiv(v, w) + 1, w);
            bucketEndOverflows = false;
          } catch (ArithmeticException e) {
            bucketEndOverflows = true;
          }
          boolean predicted = v == MAX || bucketEndOverflows;
          total++;
          if (isLost) lost++;
          if (isLost != predicted) predictionErrors++;
          System.out.println(
              "sweep w="
                  + w
                  + " v=MAX-"
                  + (MAX - v)
                  + " lost="
                  + isLost
                  + " predicted="
                  + predicted
                  + " got="
                  + got);
        }
      }
    }
    System.out.println(
        "sweep: " + lost + "/" + total + " probes lost counts; prediction errors=" + predictionErrors);
    assertEquals("overflow prediction does not explain the observations", 0, predictionErrors);
  }
}
