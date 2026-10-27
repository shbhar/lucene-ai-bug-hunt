import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PointInSetQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;

/**
 * the PointInSetQuery side leads next to a related finding (selective indexing).
 *
 * <ul>
 *   <li>B1 (= run-3 #2): 2 dims / 1 index dim, IntPoint.newSetQuery misses hits (MergePointVisitor).
 *   <li>S1: 3 dims / 2 index dims, a 2-D PointInSetQuery (protected ctor, anonymous subclass as
 *       IntPoint.newSetQuery itself does) compares the full packed value in SinglePointVisitor.
 *   <li>S2: MemoryIndex.getNumIndexDimensions() returns the total dimension count.
 * </ul>
 *
 * Deterministic: default codec, NoMergePolicy, ByteBuffersDirectory, fixed data.
 */
public class TestF4SelectiveIndexingSetQuery extends LuceneTestCase {

  static byte[] pack(int... v) {
    byte[] b = new byte[v.length * Integer.BYTES];
    for (int i = 0; i < v.length; i++) {
      NumericUtils.intToSortableBytes(v[i], b, i * Integer.BYTES);
    }
    return b;
  }

  static FieldType type(int dims, int indexDims) {
    FieldType ft = new FieldType();
    ft.setDimensions(dims, indexDims, Integer.BYTES);
    ft.freeze();
    return ft;
  }

  /** A PointInSetQuery over the first {@code indexDims} int dimensions, like IntPoint.newSetQuery. */
  static Query setQuery(String field, int indexDims, int[]... points) {
    int[][] sorted = points.clone();
    Arrays.sort(
        sorted,
        (x, y) -> Arrays.compareUnsigned(pack(x), pack(y))); // packed order, as the ctor requires
    final BytesRef[] packed = new BytesRef[sorted.length];
    for (int i = 0; i < sorted.length; i++) packed[i] = new BytesRef(pack(sorted[i]));
    return new PointInSetQuery(
        field,
        indexDims,
        Integer.BYTES,
        new PointInSetQuery.Stream() {
          int upto;

          @Override
          public BytesRef next() {
            return upto == packed.length ? null : packed[upto++];
          }
        }) {
      @Override
      protected String toString(byte[] value) {
        return Arrays.toString(value);
      }
    };
  }

  /** index docs; each inner int[][] is one flushed segment */
  static Directory index(FieldType ft, int[][]... segments) throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int[][] seg : segments) {
        for (int[] p : seg) {
          Document d = new Document();
          d.add(new Field("f", pack(p), ft));
          w.addDocument(d);
        }
        w.commit();
      }
    }
    return dir;
  }

  static int count(Directory dir, Query q) throws Exception {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      return new IndexSearcher(r).count(q);
    }
  }

  static String run(Directory dir, Query q) {
    try {
      return String.valueOf(count(dir, q));
    } catch (Throwable t) {
      return t.getClass().getSimpleName() + "@" + (t.getStackTrace().length > 0 ? t.getStackTrace()[0] : "?");
    }
  }

  /** B1 = run-3 #2: 2 dims / 1 index dim, IntPoint.newSetQuery(f, 10, 30) must find 2 docs. */
  public void testB1OneIndexDim() throws Exception {
    try (Directory dir = index(type(2, 1), new int[][] {{30, 5}, {10, 5}, {20, 5}, {40, 9}})) {
      String got = run(dir, IntPoint.newSetQuery("f", 10, 30));
      String range = run(dir, IntPoint.newRangeQuery("f", 10, 30));
      System.out.println("[B1 2d/1i] newSetQuery(f,{10,30}) got=" + got + " expected=2 (range[10,30]=" + range + ")");
      assertEquals("2", got);
    }
  }

  /** S1: 3 dims / 2 index dims, one segment whose leaf crosses the query point. */
  public void testS1TwoIndexDimsOneSegment() throws Exception {
    try (Directory dir =
        index(type(3, 2), new int[][] {{1, 2, 100}, {1, 2, 200}, {3, 4, 300}, {5, 6, 7}})) {
      String one = run(dir, setQuery("f", 2, new int[] {1, 2}));
      String two = run(dir, setQuery("f", 2, new int[] {1, 2}, new int[] {3, 4}));
      System.out.println("[S1 3d/2i] set{(1,2)} got=" + one + " expected=2 ; set{(1,2),(3,4)} got=" + two + " expected=3");
      assertEquals("2", one);
      assertEquals("3", two);
    }
  }

  /**
   * S1, layout dependence: segment A holds only (1,2,*) so its cell is CELL_INSIDE (no value
   * comparison); segment B mixes values, so its leaf crosses and every value comparison fails.
   */
  public void testS1LayoutDependence() throws Exception {
    try (Directory dir =
        index(
            type(3, 2),
            new int[][] {{1, 2, 100}, {1, 2, 200}},
            new int[][] {{1, 2, 300}, {3, 4, 400}})) {
      String got = run(dir, setQuery("f", 2, new int[] {1, 2}));
      System.out.println("[S1 layout] 2 segments, set{(1,2)} got=" + got + " expected=3");
      assertEquals("3", got);
    }
  }

  /** Control: fully indexed 2-D field (2 dims / 2 index dims): SinglePointVisitor is correct. */
  public void testControlFullyIndexed2D() throws Exception {
    try (Directory dir = index(type(2, 2), new int[][] {{1, 2}, {1, 2}, {3, 4}, {5, 6}})) {
      String got = run(dir, setQuery("f", 2, new int[] {1, 2}));
      System.out.println("[control 2d/2i] set{(1,2)} got=" + got + " expected=2");
      assertEquals("2", got);
    }
  }

  static String memSearch(Query q) {
    MemoryIndex mi = new MemoryIndex();
    mi.addField(new Field("f", pack(30, 5), type(2, 1)), null);
    mi.addField(new Field("f", pack(10, 5), type(2, 1)), null);
    try {
      return mi.search(q) > 0 ? "match" : "no-match";
    } catch (Throwable t) {
      return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
  }

  /** S2: MemoryIndex with a 2 dims / 1 index dim field, values 30 and 10 in that order. */
  public void testS2MemoryIndexExact() {
    String got = memSearch(IntPoint.newExactQuery("f", 10));
    System.out.println("[S2 MemoryIndex] newExactQuery(f,10) -> " + got + " (expected match)");
    assertEquals("match", got);
  }

  /** S2 + B1 on MemoryIndex: newSetQuery(f, 10) must match (value 10 was added second). */
  public void testS2MemoryIndexSet() {
    String got = memSearch(IntPoint.newSetQuery("f", 10));
    System.out.println("[S2 MemoryIndex] newSetQuery(f,{10}) -> " + got + " (expected match)");
    assertEquals("match", got);
  }
}
