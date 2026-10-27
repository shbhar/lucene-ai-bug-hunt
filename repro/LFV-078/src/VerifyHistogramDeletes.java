import java.io.IOException;
import java.util.function.Consumer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SoftDeletesDirectoryReaderWrapper;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.internal.hppc.LongIntHashMap;
import org.apache.lucene.sandbox.facet.plain.histograms.HistogramCollectorManager;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexOrDocValuesQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PointRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;

/**
 * Independent check: does the sandbox HistogramCollector count deleted documents when the
 * top-level query is a PointRangeQuery on the histogram field?
 *
 * <p>Index: N docs, doc i has value i (point + doc values), one segment, default codec, plain
 * IndexWriter/IndexSearcher (no randomized wrappers). The expected histogram is brute-forced from
 * live docs + doc values.
 */
public class VerifyHistogramDeletes extends LuceneTestCase {

  static final int N = 2000;
  static final long WIDTH = 1000;
  static final String F = "f";

  /** Builds a one-segment index, then deletes the docs whose id is in [delLo, delHi]. */
  private static DirectoryReader buildIndex(
      Directory dir, boolean useLongField, int delLo, int delHi, boolean softDeletes)
      throws IOException {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setRAMBufferSizeMB(256)
            .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    if (softDeletes) {
      iwc.setSoftDeletesField("soft_del");
    }
    IndexWriter w = new IndexWriter(dir, iwc);
    for (int i = 0; i < N; i++) {
      w.addDocument(makeDoc(i, useLongField));
    }
    w.commit(); // one flush -> one segment
    for (int i = delLo; i <= delHi; i++) {
      Term id = new Term("id", Integer.toString(i));
      if (softDeletes) {
        // soft-delete in place: set the soft-deletes doc-values field (no tombstone doc added)
        w.updateDocValues(id, new NumericDocValuesField("soft_del", 1));
      } else {
        w.deleteDocuments(id);
      }
    }
    w.commit();
    w.close();
    DirectoryReader r = DirectoryReader.open(dir);
    return softDeletes ? new SoftDeletesDirectoryReaderWrapper(r, "soft_del") : r;
  }

  static boolean intPoints = false; // 4-byte IntPoint instead of LongPoint (set by one sub-case)

  private static Document makeDoc(int i, boolean useLongField) {
    Document doc = new Document();
    doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
    if (intPoints) {
      doc.add(new IntPoint(F, i));
      doc.add(new NumericDocValuesField(F, i));
    } else if (useLongField) {
      doc.add(new LongField(F, i, Field.Store.NO)); // point + SORTED_NUMERIC dv
    } else {
      doc.add(new LongPoint(F, i));
      doc.add(new NumericDocValuesField(F, i));
    }
    return doc;
  }

  /** Brute-force histogram over live docs whose value is in [lo, hi]. */
  private static LongIntHashMap bruteForce(DirectoryReader r, long lo, long hi) throws IOException {
    return bruteForce(r, lo, hi, WIDTH);
  }

  private static LongIntHashMap bruteForce(DirectoryReader r, long lo, long hi, long width)
      throws IOException {
    LongIntHashMap m = new LongIntHashMap();
    for (LeafReaderContext ctx : r.leaves()) {
      Bits live = ctx.reader().getLiveDocs();
      SortedNumericDocValues dv = DocValues.getSortedNumeric(ctx.reader(), F);
      for (int d = dv.nextDoc(); d != SortedNumericDocValues.NO_MORE_DOCS; d = dv.nextDoc()) {
        if (live != null && live.get(d) == false) continue;
        long v = dv.nextValue();
        if (v >= lo && v <= hi) m.addTo(Math.floorDiv(v, width), 1);
      }
    }
    return m;
  }

  private static String fmt(LongIntHashMap m) {
    StringBuilder sb = new StringBuilder("{");
    if (m.size() > 8) return sb.append(m.size()).append(" buckets total=").append(sum(m)).append('}').toString();
    for (long k = -2; k <= 5; k++) {
      if (m.containsKey(k)) sb.append(k).append('=').append(m.get(k)).append(' ');
    }
    return sb.append("total=").append(sum(m)).append('}').toString();
  }

  private static int sum(LongIntHashMap m) {
    int s = 0;
    for (var c : m) s += c.value;
    return s;
  }

  /** Runs one query and returns true if the histogram equals brute force. */
  private static boolean check(
      String label, IndexSearcher s, DirectoryReader r, Query q, long lo, long hi)
      throws IOException {
    return check(label, s, r, q, lo, hi, WIDTH);
  }

  private static String cls(Query q) {
    Class<?> c = q.getClass();
    return c.isAnonymousClass() ? c.getSuperclass().getSimpleName() + "(anon)" : c.getSimpleName();
  }

  private static boolean check(
      String label, IndexSearcher s, DirectoryReader r, Query q, long lo, long hi, long width)
      throws IOException {
    LongIntHashMap got = s.search(q, new HistogramCollectorManager(F, width));
    LongIntHashMap exp = bruteForce(r, lo, hi, width);
    int count = s.count(q);
    boolean ok = got.equals(exp);
    System.out.printf(
        "  %-58s rewritten=%-38s count=%4d expected=%s got=%s -> %s%n",
        label,
        cls(s.rewrite(q)),
        count,
        fmt(exp),
        fmt(got),
        ok ? "OK" : "MISMATCH");
    return ok;
  }

  private static void withIndex(
      boolean longField, int delLo, int delHi, boolean soft, Consumer<Object[]> body)
      throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader r = buildIndex(dir, longField, delLo, delHi, soft)) {
      assertEquals("expected exactly one segment", 1, r.leaves().size());
      System.out.printf(
          "index: longField=%s softDeletes=%s maxDoc=%d numDocs=%d deleted ids [%d..%d]%n",
          longField, soft, r.maxDoc(), r.numDocs(), delLo, delHi);
      body.accept(new Object[] {r, new IndexSearcher(r)});
    }
  }

  // ---- Main reproduction: plain LongPoint.newRangeQuery on a segment with hard deletes ----
  public void testPlainPointRangeQueryWithHardDeletes() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader r = buildIndex(dir, false, 0, 99, false)) {
      assertEquals(1, r.leaves().size());
      assertTrue(r.leaves().get(0).reader().hasDeletions());
      IndexSearcher s = new IndexSearcher(r);
      Query q = LongPoint.newRangeQuery(F, 0, 1499);
      assertTrue(q instanceof PointRangeQuery);
      LongIntHashMap got = s.search(q, new HistogramCollectorManager(F, WIDTH));
      System.out.println(
          "MAIN: maxDoc="
              + r.maxDoc()
              + " numDocs="
              + r.numDocs()
              + " count(q)="
              + s.count(q)
              + " histogram="
              + fmt(got)
              + " bruteForce="
              + fmt(bruteForce(r, 0, 1499)));
      assertEquals("bucket 0 must exclude the 100 deleted docs", 900, got.get(0));
      assertEquals(500, got.get(1));
      assertEquals(s.count(q), sum(got));
    }
  }

  // ---- Matrix of query shapes / index shapes; asserts each against brute force ----
  public void testMatrix() throws IOException {
    final int[] failures = {0};
    // 1) LongPoint + NumericDocValuesField, hard deletes in bucket 0
    withIndex(
        false,
        0,
        99,
        false,
        o -> {
          DirectoryReader r = (DirectoryReader) o[0];
          IndexSearcher s = (IndexSearcher) o[1];
          try {
            Query prq = LongPoint.newRangeQuery(F, 0, 1499);
            if (!check("LongPoint.newRangeQuery [0,1499]", s, r, prq, 0, 1499)) failures[0]++;
            if (!check(
                "IndexOrDocValuesQuery(LongPoint, NumericDV slow range)",
                s,
                r,
                new IndexOrDocValuesQuery(
                    prq, NumericDocValuesField.newSlowRangeQuery(F, 0, 1499)),
                0,
                1499)) failures[0]++;
            // single FILTER clause: BooleanQuery rewrites to BoostQuery(ConstantScoreQuery(prq), 0),
            // which HistogramCollector unwraps back to the PRQ -> also takes the bulk path
            if (!check(
                "BooleanQuery(FILTER prq) [single clause]",
                s,
                r,
                new BooleanQuery.Builder().add(prq, BooleanClause.Occur.FILTER).build(),
                0,
                1499)) failures[0]++;
            // controls: same matching docs, but forced onto the naive per-doc collectors
            if (!check(
                "control: BooleanQuery(FILTER prq, FILTER [-5,1499])",
                s,
                r,
                new BooleanQuery.Builder()
                    .add(prq, BooleanClause.Occur.FILTER)
                    .add(LongPoint.newRangeQuery(F, -5, 1499), BooleanClause.Occur.FILTER)
                    .build(),
                0,
                1499)) failures[0]++;
            if (!check(
                "control: prq, bucketWidth=2 (canCollectEfficiently=false)",
                s,
                r,
                prq,
                0,
                1499,
                2)) failures[0]++;
            if (!check(
                "control: MatchAllDocsQuery",
                s,
                r,
                MatchAllDocsQuery.INSTANCE,
                Long.MIN_VALUE,
                Long.MAX_VALUE)) failures[0]++;
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
    // 2) LongField (point + SORTED_NUMERIC dv), hard deletes in bucket 1
    withIndex(
        true,
        1000,
        1049,
        false,
        o -> {
          DirectoryReader r = (DirectoryReader) o[0];
          IndexSearcher s = (IndexSearcher) o[1];
          try {
            if (!check(
                "LongField docs + LongPoint.newRangeQuery [500,1999]",
                s,
                r,
                LongPoint.newRangeQuery(F, 500, 1999),
                500,
                1999)) failures[0]++;
            if (!check(
                "IndexOrDocValuesQuery(LongPoint, SortedNumericDV slow)",
                s,
                r,
                new IndexOrDocValuesQuery(
                    LongPoint.newRangeQuery(F, 500, 1999),
                    SortedNumericDocValuesField.newSlowRangeQuery(F, 500, 1999)),
                500,
                1999)) failures[0]++;
            if (!check(
                "control: LongField.newRangeQuery (IndexSort wrapper)",
                s,
                r,
                LongField.newRangeQuery(F, 500, 1999),
                500,
                1999)) failures[0]++;
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
    // 3) soft deletes (the mode used by Elasticsearch/OpenSearch-style engines)
    withIndex(
        false,
        0,
        99,
        true,
        o -> {
          DirectoryReader r = (DirectoryReader) o[0];
          IndexSearcher s = (IndexSearcher) o[1];
          try {
            if (!check(
                "soft deletes: LongPoint.newRangeQuery [0,1499]",
                s,
                r,
                LongPoint.newRangeQuery(F, 0, 1499),
                0,
                1499)) failures[0]++;
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
    // 3b) 4-byte points: IntPoint + NumericDocValuesField, hard deletes in bucket 0
    intPoints = true;
    try {
      withIndex(
          false,
          0,
          99,
          false,
          o -> {
            DirectoryReader r = (DirectoryReader) o[0];
            IndexSearcher s = (IndexSearcher) o[1];
            try {
              if (!check(
                  "IntPoint.newRangeQuery [0,1499]",
                  s,
                  r,
                  IntPoint.newRangeQuery(F, 0, 1499),
                  0,
                  1499)) failures[0]++;
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });
    } finally {
      intPoints = false;
    }
    // 4) control: no deletions at all
    withIndex(
        false,
        1,
        0,
        false,
        o -> {
          DirectoryReader r = (DirectoryReader) o[0];
          IndexSearcher s = (IndexSearcher) o[1];
          try {
            if (!check(
                "control (no deletes): LongPoint.newRangeQuery [0,1499]",
                s,
                r,
                LongPoint.newRangeQuery(F, 0, 1499),
                0,
                1499)) failures[0]++;
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
    System.out.println("matrix mismatches: " + failures[0]);
    assertEquals("number of query/index shapes whose histogram != brute force", 0, failures[0]);
  }
}
