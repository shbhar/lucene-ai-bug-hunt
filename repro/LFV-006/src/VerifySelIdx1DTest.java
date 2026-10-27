import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.PointValues;
import org.apache.lucene.index.PointValues.IntersectVisitor;
import org.apache.lucene.index.PointValues.Relation;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.PointInSetQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.NumericUtils;

/**
 * Independent verification: a points field with 2 dimensions of which only the first is indexed
 * (FieldType.setDimensions(2, 1, 4)) and IntPoint.newSetQuery on it.
 *
 * <p>All tests are deterministic (fixed data, fixed java.util.Random seed, explicit codec,
 * explicit merge policy). Codec randomization of LuceneTestCase is bypassed by setting the codec on
 * the IndexWriterConfig.
 */
public class VerifySelIdx1DTest extends LuceneTestCase {

  static final FieldType SEL_TYPE = new FieldType();

  static {
    SEL_TYPE.setDimensions(2, 1, Integer.BYTES); // 2 dims, 1 index dim, 4 bytes/dim
    SEL_TYPE.freeze();
  }

  static final FieldType PLAIN_1D_TYPE = new FieldType();

  static {
    PLAIN_1D_TYPE.setDimensions(1, 1, Integer.BYTES);
    PLAIN_1D_TYPE.freeze();
  }

  static byte[] pack(int indexDim, int dataDim) {
    byte[] b = new byte[2 * Integer.BYTES];
    NumericUtils.intToSortableBytes(indexDim, b, 0);
    NumericUtils.intToSortableBytes(dataDim, b, Integer.BYTES);
    return b;
  }

  static Document doc(int indexDim, int dataDim) {
    Document d = new Document();
    d.add(new Field("f", pack(indexDim, dataDim), SEL_TYPE));
    d.add(new Field("g", pack1(indexDim), PLAIN_1D_TYPE)); // control: plain 1-D copy
    return d;
  }

  static byte[] pack1(int v) {
    byte[] b = new byte[Integer.BYTES];
    NumericUtils.intToSortableBytes(v, b, 0);
    return b;
  }

  static IndexWriterConfig iwc(Codec codec, boolean noMerge) {
    IndexWriterConfig c = new IndexWriterConfig();
    c.setCodec(codec);
    if (noMerge) {
      c.setMergePolicy(NoMergePolicy.INSTANCE);
    }
    c.setMaxBufferedDocs(1_000_000);
    c.setRAMBufferSizeMB(256);
    return c;
  }

  /** Index-dim values in the order the default intersect() visits them (all cells "crossing"). */
  static List<Integer> visitOrder(LeafReader leaf, String field) throws IOException {
    PointValues pv = leaf.getPointValues(field);
    List<Integer> order = new ArrayList<>();
    pv.intersect(
        new IntersectVisitor() {
          @Override
          public void visit(int docID) {
            throw new AssertionError("unexpected visit(docID) with CROSSES");
          }

          @Override
          public void visit(int docID, byte[] packedValue) {
            order.add(NumericUtils.sortableBytesToInt(packedValue, 0));
          }

          @Override
          public Relation compare(byte[] min, byte[] max) {
            return Relation.CELL_CROSSES_QUERY;
          }
        });
    return order;
  }

  static boolean nonDecreasing(List<Integer> l) {
    for (int i = 1; i < l.size(); i++) {
      if (l.get(i - 1) > l.get(i)) return false;
    }
    return true;
  }

  static int countExactOr(IndexSearcher s, String field, int... values) throws IOException {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (int v : values) {
      b.add(IntPoint.newExactQuery(field, v), BooleanClause.Occur.SHOULD);
    }
    return s.count(b.build());
  }

  static IndexSearcher searcher(DirectoryReader r) {
    IndexSearcher s = new IndexSearcher(r);
    s.setQueryCache(null);
    return s;
  }

  // docs: (idx=30,data=5) (idx=10,data=5) (idx=20,data=5) (idx=40,data=9)
  static final int[][] FOUR = {{30, 5}, {10, 5}, {20, 5}, {40, 9}};

  private void runFour(Codec codec, String label) throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, iwc(codec, true))) {
        for (int[] p : FOUR) w.addDocument(doc(p[0], p[1]));
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        IndexSearcher s = searcher(r);
        List<Integer> order = visitOrder(r.leaves().get(0).reader(), "f");
        List<Integer> orderControl = visitOrder(r.leaves().get(0).reader(), "g");
        int setSel = s.count(IntPoint.newSetQuery("f", 10, 30));
        int setCtl = s.count(IntPoint.newSetQuery("g", 10, 30));
        int orSel = countExactOr(s, "f", 10, 30);
        int rangeSel = s.count(IntPoint.newRangeQuery("f", 10, 30)); // 10,20,30 -> 3
        System.out.println(
            "["
                + label
                + "] codec="
                + codec.getName()
                + " visitOrder(f, 2dims/1idx)="
                + order
                + " visitOrder(g, 1dim)="
                + orderControl
                + " | newSetQuery(f,{10,30})="
                + setSel
                + " newSetQuery(g,{10,30})="
                + setCtl
                + " OR-of-exact(f)="
                + orSel
                + " range(f,[10,30])="
                + rangeSel);
        CheckIndex.Status st;
        try (CheckIndex ci = new CheckIndex(dir)) {
          st = ci.checkIndex();
        }
        System.out.println("[" + label + "] CheckIndex clean=" + st.clean);
        assertEquals("control 1-D set query", 2, setCtl);
        assertEquals("OR of exact PointRangeQuery on f", 2, orSel);
        assertEquals("range on f", 3, rangeSel);
        assertTrue(
            "intersect() on a numIndexDims==1 field should visit in increasing order: " + order,
            nonDecreasing(order));
        assertEquals("PointInSetQuery on f must match docs with idx 10 and 30", 2, setSel);
      }
    }
  }

  /** Flush path (Lucene90PointsWriter.writeField -> BKDWriter.writeField -> writeFieldNDims). */
  public void testFlushDefaultCodec() throws IOException {
    runFour(TestUtil.getDefaultCodec(), "flush");
  }

  /** Same data, SimpleText codec (uses SimpleTextBKDWriter.add()+finish()). */
  public void testFlushSimpleText() throws IOException {
    runFour(Codec.forName("SimpleText"), "simpletext");
  }

  /**
   * Merge path: each source segment is individually fine (results correct before merge); after
   * forceMerge(1) (Lucene90PointsWriter.merge -> mergeOneField -> BKDWriter.add/finish -> build)
   * the same query silently loses a hit.
   */
  public void testMergeBreaksPreviouslyCorrectResults() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(TestUtil.getDefaultCodec(), false))) {
      w.addDocument(doc(30, 5));
      w.addDocument(doc(10, 5));
      w.commit();
      w.addDocument(doc(20, 5));
      w.addDocument(doc(40, 9));
      w.commit();
      int before, after;
      List<Integer> orderAfter;
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(2, r.leaves().size());
        before = searcher(r).count(IntPoint.newSetQuery("f", 10, 30));
      }
      w.forceMerge(1);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(1, r.leaves().size());
        after = searcher(r).count(IntPoint.newSetQuery("f", 10, 30));
        orderAfter = visitOrder(r.leaves().get(0).reader(), "f");
      }
      System.out.println(
          "[merge] newSetQuery(f,{10,30}) before forceMerge="
              + before
              + " after forceMerge="
              + after
              + " visitOrder after="
              + orderAfter);
      assertEquals("before merge", 2, before);
      assertEquals("after merge", 2, after);
    }
  }

  /**
   * Larger seeded data set with default leaf size (512): index dim drawn from a small range
   * ("price"-like, many duplicates), data dim from 4 categories. Compare PointInSetQuery hit
   * counts with brute force and with the plain 1-D control field.
   */
  public void testScaleSeeded() throws IOException {
    final int n = 200_000;
    Random rnd = new Random(20261022L);
    int[] idx = new int[n];
    int[] data = new int[n];
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(TestUtil.getDefaultCodec(), false))) {
      for (int i = 0; i < n; i++) {
        idx[i] = rnd.nextInt(5_000);
        data[i] = rnd.nextInt(4);
        w.addDocument(doc(idx[i], data[i]));
      }
      w.forceMerge(1);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = searcher(r);
        LeafReader leaf = r.leaves().get(0).reader();
        List<Integer> order = visitOrder(leaf, "f");
        int inversions = 0;
        for (int i = 1; i < order.size(); i++) if (order.get(i - 1) > order.get(i)) inversions++;
        int queries = 200, wrong = 0;
        long missedTotal = 0, expectedTotal = 0;
        Random qr = new Random(7L);
        for (int q = 0; q < queries; q++) {
          int k = 1 + qr.nextInt(50);
          int[] vals = new int[k];
          Set<Integer> vs = new HashSet<>();
          for (int j = 0; j < k; j++) {
            vals[j] = qr.nextInt(5_000);
            vs.add(vals[j]);
          }
          int expected = 0;
          for (int i = 0; i < n; i++) if (vs.contains(idx[i])) expected++;
          int got = s.count(IntPoint.newSetQuery("f", vals));
          int ctl = s.count(IntPoint.newSetQuery("g", vals));
          assertEquals("control 1-D field must be exact", expected, ctl);
          expectedTotal += expected;
          if (got != expected) {
            wrong++;
            missedTotal += (expected - got);
            if (wrong <= 3) {
              System.out.println(
                  "[scale] query#"
                      + q
                      + " k="
                      + k
                      + " expected="
                      + expected
                      + " PointInSetQuery(f)="
                      + got
                      + " PointInSetQuery(g, 1-D control)="
                      + ctl);
            }
          }
        }
        System.out.println(
            "[scale] n="
                + n
                + " segments="
                + r.leaves().size()
                + " visit-order inversions="
                + inversions
                + " wrongQueries="
                + wrong
                + "/"
                + queries
                + " missedHits="
                + missedTotal
                + "/"
                + expectedTotal);
        assertEquals("visit order inversions", 0, inversions);
        assertEquals("queries with wrong hit count", 0, wrong);
      }
    }
  }

  static byte[] ints(int... v) {
    byte[] b = new byte[v.length * Integer.BYTES];
    for (int i = 0; i < v.length; i++) NumericUtils.intToSortableBytes(v[i], b, i * Integer.BYTES);
    return b;
  }

  /** 2-D PointInSetQuery via the public subclassing API (this takes the SinglePointVisitor path). */
  static Query setQuery2D(String field, int[]... points) {
    byte[][] enc = new byte[points.length][];
    for (int i = 0; i < points.length; i++) enc[i] = ints(points[i]);
    Arrays.sort(enc, Arrays::compareUnsigned);
    return new PointInSetQuery(
        field,
        2,
        Integer.BYTES,
        new PointInSetQuery.Stream() {
          int upto = 0;

          @Override
          public BytesRef next() {
            return upto == enc.length ? null : new BytesRef(enc[upto++]);
          }
        }) {
      @Override
      protected String toString(byte[] value) {
        return Arrays.toString(value);
      }
    };
  }

  /**
   * Secondary observation (not part of the candidate): 3 dims / 2 index dims. A 2-D
   * PointInSetQuery is accepted (numIndexDims == 2) but SinglePointVisitor compares the full
   * packed value (3 dims) with the 2-dim query point: AssertionError with -ea, 0 hits without.
   */
  public void testMultiIndexDimSetQuery() throws IOException {
    FieldType ft = new FieldType();
    ft.setDimensions(3, 2, Integer.BYTES);
    ft.freeze();
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, iwc(TestUtil.getDefaultCodec(), true))) {
        for (int[] p : new int[][] {{1, 1, 7}, {2, 2, 8}, {3, 3, 9}}) {
          Document d = new Document();
          d.add(new Field("f", ints(p), ft));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = searcher(r);
        String got;
        try {
          got = String.valueOf(s.count(setQuery2D("f", new int[] {1, 1}, new int[] {2, 2})));
        } catch (AssertionError e) {
          got = "AssertionError at " + e.getStackTrace()[0];
        }
        System.out.println("[3d2i] 2-D PointInSetQuery{(1,1),(2,2)} got=" + got + " expected=2");
        assertEquals("2", got);
      }
    }
  }

  /**
   * Secondary observation: MemoryIndex reports getNumIndexDimensions() == numDims for a
   * selectively indexed field, so 1-D point queries that work on an IndexWriter index throw IAE.
   */
  public void testMemoryIndexSelectiveField() {
    MemoryIndex mi = new MemoryIndex();
    mi.addField(new Field("f", pack(30, 5), SEL_TYPE), null);
    mi.addField(new Field("f", pack(10, 5), SEL_TYPE), null);
    String got;
    try {
      got = String.valueOf(mi.search(IntPoint.newExactQuery("f", 10)) > 0);
    } catch (IllegalArgumentException e) {
      got = "IAE: " + e.getMessage();
    }
    System.out.println("[memoryindex] newExactQuery(f,10) on (30,5),(10,5): " + got);
    assertEquals("true", got);
  }
}
