/*
 * Independent verification of the candidate bug:
 * Lucene90DocValuesProducer encoded-domain range scan (transformGcdBounds) is wrong when the
 * packed value (v - minValue) / gcd does not fit in [0, Long.MAX_VALUE).
 *
 * Every check compares the codec's bulk path against an oracle computed by decoding each
 * document's values with the plain per-doc iterator API (nextDoc/longValue/nextValue), i.e. the
 * semantics the javadoc of NumericDocValues#rangeIntoBitSet / SortedNumericDocValues#rangeIntoBitSet
 * defines as the default implementation.
 */
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.NumericUtils;

public class TestVerifyEncodedRange extends LuceneTestCase {

  static final long MAX = Long.MAX_VALUE;
  static final long MIN = Long.MIN_VALUE;

  /** One document = list of values (empty list = doc without the field). */
  private static DirectoryReader index(
      Directory dir, String field, List<long[]> docs, boolean multi, boolean skipIndex)
      throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec()); // real default codec, not the randomized one
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (long[] vals : docs) {
        Document d = new Document();
        for (long v : vals) {
          if (multi) {
            d.add(
                skipIndex
                    ? SortedNumericDocValuesField.indexedField(field, v)
                    : new SortedNumericDocValuesField(field, v));
          } else {
            d.add(
                skipIndex
                    ? NumericDocValuesField.indexedField(field, v)
                    : new NumericDocValuesField(field, v));
          }
        }
        w.addDocument(d);
      }
      w.forceMerge(1);
    }
    DirectoryReader r = DirectoryReader.open(dir);
    assertEquals(1, r.leaves().size());
    return r;
  }

  /** Oracle: decoded per-doc evaluation. */
  private static FixedBitSet oracle(List<long[]> docs, long lo, long hi) {
    FixedBitSet bs = new FixedBitSet(docs.size());
    for (int i = 0; i < docs.size(); i++) {
      for (long v : docs.get(i)) {
        if (v >= lo && v <= hi) {
          bs.set(i);
          break;
        }
      }
    }
    return bs;
  }

  /** Codec bulk path, called directly. */
  private static FixedBitSet bulk(LeafReader leaf, String field, boolean multi, long lo, long hi)
      throws Exception {
    int maxDoc = leaf.maxDoc();
    FixedBitSet bs = new FixedBitSet(maxDoc);
    if (multi) {
      SortedNumericDocValues sndv = DocValues.getSortedNumeric(leaf, field);
      sndv.rangeIntoBitSet(0, maxDoc, lo, hi, bs, 0);
    } else {
      NumericDocValues ndv = DocValues.getNumeric(leaf, field);
      ndv.rangeIntoBitSet(0, maxDoc, lo, hi, bs, 0);
    }
    return bs;
  }

  /** Collects matching doc ids with a COMPLETE_NO_SCORES collector (same bulk path as count). */
  private static FixedBitSet collectDocs(IndexSearcher s, Query q, int maxDoc) throws Exception {
    final FixedBitSet bs = new FixedBitSet(maxDoc);
    s.search(
        q,
        new org.apache.lucene.search.CollectorManager<org.apache.lucene.search.Collector, Void>() {
          @Override
          public org.apache.lucene.search.Collector newCollector() {
            return new org.apache.lucene.search.SimpleCollector() {
              int base;

              @Override
              protected void doSetNextReader(org.apache.lucene.index.LeafReaderContext ctx) {
                base = ctx.docBase;
              }

              @Override
              public void collect(int doc) {
                bs.set(base + doc);
              }

              @Override
              public void collect(org.apache.lucene.search.DocIdStream stream)
                  throws java.io.IOException {
                stream.forEach(doc -> bs.set(base + doc));
              }

              @Override
              public ScoreMode scoreMode() {
                return ScoreMode.COMPLETE_NO_SCORES;
              }
            };
          }

          @Override
          public Void reduce(java.util.Collection<org.apache.lucene.search.Collector> c) {
            return null;
          }
        });
    return bs;
  }

  /** Iterates the query's scorer with nextDoc (no bulk intoBitSet). */
  private static int iterateCount(IndexSearcher s, Query q) throws Exception {
    Weight w = s.createWeight(s.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1f);
    int c = 0;
    for (var ctx : s.getIndexReader().leaves()) {
      Scorer sc = w.scorer(ctx);
      if (sc == null) continue;
      var tpi = sc.twoPhaseIterator();
      if (tpi == null) {
        for (int d = sc.iterator().nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = sc.iterator().nextDoc()) c++;
      } else {
        var approx = tpi.approximation();
        for (int d = approx.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = approx.nextDoc()) {
          if (tpi.matches()) c++;
        }
      }
    }
    return c;
  }

  private static String diff(FixedBitSet expected, FixedBitSet actual) {
    StringBuilder sb = new StringBuilder();
    int n = 0;
    for (int i = 0; i < expected.length() && n < 10; i++) {
      if (expected.get(i) != actual.get(i)) {
        sb.append(" doc").append(i).append(expected.get(i) ? "(missing)" : "(spurious)");
        n++;
      }
    }
    return sb.toString();
  }

  /** Result of one comparison. */
  record R(boolean apiOk, boolean queryOk) {}

  /**
   * Runs all comparisons and prints them. apiOk: direct XxxDocValues#rangeIntoBitSet equals the
   * decoded oracle. queryOk: IndexSearcher#count and a plain nextDoc()/matches() iteration of
   * newSlowRangeQuery both equal the oracle cardinality.
   */
  private R check(
      String name, List<long[]> docs, boolean multi, boolean skipIndex, long lo, long hi)
      throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader r = index(dir, "f", docs, multi, skipIndex)) {
      LeafReader leaf = r.leaves().get(0).reader();
      FixedBitSet exp = oracle(docs, lo, hi);
      FixedBitSet got = bulk(leaf, "f", multi, lo, hi);
      IndexSearcher s = new IndexSearcher(r);
      s.setQueryCache(null);
      Query q =
          multi
              ? SortedNumericDocValuesField.newSlowRangeQuery("f", lo, hi)
              : NumericDocValuesField.newSlowRangeQuery("f", lo, hi);
      int count = s.count(q);
      int iter = iterateCount(s, q);
      FixedBitSet collected = collectDocs(s, q, r.maxDoc());
      long topHits =
          s.search(q, new org.apache.lucene.search.TopScoreDocCollectorManager(10, Integer.MAX_VALUE))
              .totalHits
              .value();
      boolean apiOk = exp.equals(got);
      boolean queryOk =
          count == exp.cardinality()
              && iter == exp.cardinality()
              && topHits == exp.cardinality()
              && collected.equals(exp);
      System.out.println(
          String.format(
              Locale.ROOT,
              "%-46s [%d,%d] oracle=%d rangeIntoBitSet=%d%s | count()=%d nextDoc-iter=%d topDocs.totalHits=%d noScoresCollector=%d%s -> api:%s query:%s",
              name, lo, hi, exp.cardinality(), got.cardinality(),
              apiOk ? "" : " (diff:" + diff(exp, got) + ")", count, iter, topHits,
              collected.cardinality(),
              collected.equals(exp) ? "" : " (diff:" + diff(exp, collected) + ")",
              apiOk ? "OK" : "WRONG", queryOk ? "OK" : "WRONG"));
      return new R(apiOk, queryOk);
    }
  }

  private static List<long[]> singles(long v0, long v1, int n, long step, long base) {
    List<long[]> docs = new ArrayList<>();
    docs.add(new long[] {v0});
    docs.add(new long[] {v1});
    for (int i = 2; i < n; i++) docs.add(new long[] {base + i * step});
    return docs;
  }

  // ---- Case 1: dense numeric, gcd=1, max-min overflows -> 64 bpv, packed wraps negative ----
  public void testDenseNumericFalseNegative() throws Exception {
    List<long[]> docs = singles(-10, MAX - 5, 300, 1000, 0);
    R a = check("dense numeric (skip) [0,MAX]", docs, false, true, 0, MAX);
    R b = check("dense numeric (skip) [MAX-6,MAX]", docs, false, true, MAX - 6, MAX);
    R c = check("dense numeric (NO skip) [0,MAX]", docs, false, false, 0, MAX);
    assertTrue("query without skip index is not routed through rangeIntoBitSet", c.queryOk);
    assertTrue("rangeIntoBitSet API wrong", a.apiOk && b.apiOk && c.apiOk);
    assertTrue("query-level result wrong", a.queryOk && b.queryOk);
  }

  // ---- Case 2a: packed == Long.MAX_VALUE collides with the lower-bound sentinel (63 bpv) ----
  public void testDenseNumericSentinelApiOnly() throws Exception {
    List<long[]> docs = singles(-1, MAX - 1, 300, 1000, 0); // max-min == MAX exactly
    R a = check("dense numeric (skip) [MAX,MAX]", docs, false, true, MAX, MAX);
    assertTrue("rangeIntoBitSet API: [MAX,MAX] must not match MAX-1", a.apiOk);
  }

  // ---- Case 2b: same collision but reachable through a query (64 bpv, block max >= lo) ----
  public void testDenseNumericSentinelQueryLevel() throws Exception {
    List<long[]> docs = new ArrayList<>();
    docs.add(new long[] {-10}); // min
    docs.add(new long[] {MAX - 10}); // packed = MAX - 10 + 10 = Long.MAX_VALUE  (spurious hit)
    docs.add(new long[] {MAX - 5}); // packed wraps negative (missed hit)
    for (int i = 3; i < 300; i++) docs.add(new long[] {i * 1000L});
    R a = check("dense numeric (skip) [MAX-5,MAX]", docs, false, true, MAX - 5, MAX);
    assertTrue("rangeIntoBitSet API wrong", a.apiOk);
    assertTrue("query-level result wrong", a.queryOk);
  }

  // ---- Case 2c: pure query-level false positive (overcount): spurious hit, nothing missed ----
  public void testDenseNumericQueryLevelOvercount() throws Exception {
    List<long[]> docs = new ArrayList<>();
    docs.add(new long[] {-10}); // min
    docs.add(new long[] {MAX - 10}); // packed == Long.MAX_VALUE, NOT in [MAX-5, MAX-1]
    docs.add(new long[] {MAX}); // keeps the skipper block max >= lo; NOT in [MAX-5, MAX-1]
    for (int i = 3; i < 300; i++) docs.add(new long[] {i * 1000L});
    R a = check("dense numeric (skip) [MAX-5,MAX-1]", docs, false, true, MAX - 5, MAX - 1);
    assertTrue("rangeIntoBitSet API wrong", a.apiOk);
    assertTrue("query-level result wrong", a.queryOk);
  }

  // ---- Case 3: dense numeric, gcd=7, v-min == Long.MAX_VALUE ----
  public void testDenseNumericGcdSentinel() throws Exception {
    long lo0 = MIN / 2, hi0 = MAX / 2; // hi0 - lo0 == MAX == 7 * 1317624576693539401
    assertEquals(0, MAX % 7);
    List<long[]> docs = singles(lo0, hi0, 300, 7, lo0);
    R a = check("dense numeric gcd=7 (skip) [MAX/2+1,MAX]", docs, false, true, hi0 + 1, MAX);
    assertTrue("rangeIntoBitSet API: [MAX/2+1,MAX] must match nothing", a.apiOk);
  }

  // ---- Case 4: dense sorted-numeric fixed cardinality 2 (SIMD sortedNumericRangeIntoBitSet) ----
  public void testDenseSortedNumericFixedCardinality() throws Exception {
    List<long[]> docs = new ArrayList<>();
    docs.add(new long[] {-10, -9});
    docs.add(new long[] {MAX - 6, MAX - 5});
    for (int i = 2; i < 300; i++) docs.add(new long[] {i * 1000L, i * 1000L + 1});
    R a = check("dense sorted-numeric card=2 (skip) [0,MAX]", docs, true, true, 0, MAX);
    assertTrue("rangeIntoBitSet API wrong", a.apiOk);
    assertTrue("query-level result wrong", a.queryOk);
  }

  // ---- Case 5: sparse multi-valued sorted-numeric ----
  public void testSparseSortedNumeric() throws Exception {
    List<long[]> docs = new ArrayList<>();
    for (int i = 0; i < 600; i++) {
      if (i % 2 == 1) {
        docs.add(new long[0]);
      } else if (i == 0) {
        docs.add(new long[] {-10, -9});
      } else if (i == 2) {
        docs.add(new long[] {MAX - 6, MAX - 5, MAX - 4});
      } else {
        docs.add(new long[] {i * 1000L, i * 1000L + 1});
      }
    }
    R a = check("sparse sorted-numeric (skip) [0,MAX]", docs, true, true, 0, MAX);
    assertTrue("rangeIntoBitSet API wrong", a.apiOk);
    assertTrue("query-level result wrong", a.queryOk);
  }

  // ---- Case 6: realistic - latitudes as sortable longs (sign change, |lat| >= 2) ----
  public void testLatitudeSortableLongs() throws Exception {
    List<long[]> docs = new ArrayList<>();
    for (int i = 0; i < 500; i++) {
      double lat = -89.9 + i * (179.8 / 499); // -89.9 .. 89.9
      docs.add(new long[] {NumericUtils.doubleToSortableLong(lat)});
    }
    long lo = NumericUtils.doubleToSortableLong(45.0), hi = NumericUtils.doubleToSortableLong(90.0);
    R a = check("latitude sorted-numeric single (skip) [45,90]", docs, true, true, lo, hi);
    R b = check("latitude numeric (skip) [45,90]", docs, false, true, lo, hi);
    R c = check("latitude numeric (NO skip) [45,90]", docs, false, false, lo, hi);
    assertTrue("query without skip index is correct", c.queryOk);
    assertTrue("rangeIntoBitSet API wrong", a.apiOk && b.apiOk && c.apiOk);
    assertTrue("query-level result wrong", a.queryOk && b.queryOk);
  }

  // ---- Control: same shapes but span fits in a signed long: must be correct ----
  public void testControlNoOverflow() throws Exception {
    List<long[]> docs = singles(-10, MAX / 2, 300, 1000, 0);
    R a = check("CONTROL dense numeric span<MAX (skip) [0,MAX]", docs, false, true, 0, MAX);
    R b = check("CONTROL dense numeric span<MAX (skip) [MAX/4,MAX]", docs, false, true, MAX / 4, MAX);
    assertTrue(a.apiOk && a.queryOk && b.apiOk && b.queryOk);
  }

  // ---- Scale: 100k docs, random order, deterministic java.util.Random (not asserting counts) ----
  public void testScaleRandomLatitudesAndHashes() throws Exception {
    java.util.Random rnd = new java.util.Random(42);
    List<long[]> lat = new ArrayList<>();
    List<long[]> hash = new ArrayList<>();
    for (int i = 0; i < 100_000; i++) {
      lat.add(new long[] {NumericUtils.doubleToSortableLong(-90 + 180 * rnd.nextDouble())});
      hash.add(new long[] {rnd.nextLong()});
    }
    R a =
        check(
            "SCALE 100k random latitudes (skip) [45,90]",
            lat, false, true,
            NumericUtils.doubleToSortableLong(45.0), NumericUtils.doubleToSortableLong(90.0));
    R b =
        check(
            "SCALE 100k random latitudes (skip) [-10,10]",
            lat, false, true,
            NumericUtils.doubleToSortableLong(-10.0), NumericUtils.doubleToSortableLong(10.0));
    R c = check("SCALE 100k random 64-bit hashes (skip) [0,MAX]", hash, false, true, 0, MAX);
    R d = check("SCALE 100k random 64-bit hashes (skip) [MIN,-1]", hash, false, true, MIN, -1);
    assertTrue("scale: query-level wrong", a.queryOk && b.queryOk && c.queryOk && d.queryOk);
  }
}
