import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.PointValues;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PointInSetQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;

/**
 * Plain main, public API only, default IndexWriterConfig and default codec. Run with or without
 * -ea. Prints facts for the selective-indexing PointInSetQuery bug. Args: "small" (default) or
 * "scale".
 */
public class SelIdxDemo {

  static FieldType type(int dims, int indexDims) {
    FieldType t = new FieldType();
    t.setDimensions(dims, indexDims, Integer.BYTES);
    t.freeze();
    return t;
  }

  static byte[] pack(int... v) {
    byte[] b = new byte[v.length * Integer.BYTES];
    for (int i = 0; i < v.length; i++) {
      IntPoint.encodeDimension(v[i], b, i * Integer.BYTES);
    }
    return b;
  }

  static Document doc(FieldType t, int... v) {
    Document d = new Document();
    d.add(new Field("f", pack(v), t));
    return d;
  }

  /** (index value, data value...) pairs in the order intersect() visits them, all cells crossing. */
  static List<String> visitOrder(LeafReader leaf, final int dims) throws Exception {
    final List<String> out = new ArrayList<>();
    leaf.getPointValues("f")
        .intersect(
            new PointValues.IntersectVisitor() {
              @Override
              public void visit(int docID) {
                throw new AssertionError("visit(docID) with CROSSES");
              }

              @Override
              public void visit(int docID, byte[] packed) {
                StringBuilder sb = new StringBuilder("doc" + docID + "(");
                for (int d = 0; d < dims; d++) {
                  if (d > 0) sb.append(',');
                  sb.append(IntPoint.decodeDimension(packed, d * Integer.BYTES));
                }
                out.add(sb.append(')').toString());
              }

              @Override
              public PointValues.Relation compare(byte[] min, byte[] max) {
                return PointValues.Relation.CELL_CROSSES_QUERY;
              }
            });
    return out;
  }

  static Query setQuery2D(String field, final int[]... points) {
    final byte[][] enc = new byte[points.length][];
    for (int i = 0; i < points.length; i++) enc[i] = pack(points[i]);
    Arrays.sort(
        enc,
        new Comparator<byte[]>() {
          @Override
          public int compare(byte[] a, byte[] b) {
            for (int i = 0; i < a.length; i++) {
              int c = (a[i] & 0xff) - (b[i] & 0xff);
              if (c != 0) return c;
            }
            return 0;
          }
        });
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

  static IndexSearcher searcher(DirectoryReader r) {
    IndexSearcher s = new IndexSearcher(r);
    s.setQueryCache(null);
    return s;
  }

  static String count(IndexSearcher s, Query q) {
    try {
      return String.valueOf(s.count(q));
    } catch (Throwable t) {
      StackTraceElement[] st = t.getStackTrace();
      return t.getClass().getSimpleName() + " at " + (st.length > 0 ? st[0] : "?");
    }
  }

  public static void main(String[] args) throws Exception {
    boolean ea = false;
    assert ea = true;
    System.out.println(
        "Lucene "
            + Version.LATEST
            + " codec="
            + Codec.getDefault().getName()
            + " java="
            + System.getProperty("java.version")
            + " asserts="
            + ea);
    if (args.length > 0 && args[0].equals("scale")) {
      scale();
      return;
    }
    Path tmp = Files.createTempDirectory("selidx");
    try {
      small(tmp);
    } finally {
      // best-effort cleanup
      try (java.util.stream.Stream<Path> w = Files.walk(tmp)) {
        w.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
      }
    }
  }

  static void small(Path tmp) throws Exception {
    FieldType t21 = type(2, 1);
    // A: one flushed segment
    try (Directory dir = new NIOFSDirectory(tmp.resolve("a"))) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(doc(t21, 30, 5));
        w.addDocument(doc(t21, 10, 5));
        w.addDocument(doc(t21, 20, 5));
        w.addDocument(doc(t21, 40, 9));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = searcher(r);
        BooleanQuery.Builder or = new BooleanQuery.Builder();
        or.add(IntPoint.newExactQuery("f", 10), BooleanClause.Occur.SHOULD);
        or.add(IntPoint.newExactQuery("f", 30), BooleanClause.Occur.SHOULD);
        System.out.println(
            "[A flush, 2 dims/1 index dim] segments="
                + r.leaves().size()
                + " visitOrder="
                + visitOrder(r.leaves().get(0).reader(), 2));
        System.out.println(
            "[A] newSetQuery(f,10,30)="
                + count(s, IntPoint.newSetQuery("f", 10, 30))
                + " (expected 2)  newExactQuery(f,10) OR newExactQuery(f,30)="
                + count(s, or.build())
                + "  newRangeQuery(f,10,30)="
                + count(s, IntPoint.newRangeQuery("f", 10, 30))
                + " (expected 3)  newSetQuery(f,10)="
                + count(s, IntPoint.newSetQuery("f", 10))
                + " newSetQuery(f,30)="
                + count(s, IntPoint.newSetQuery("f", 30))
                + " newSetQuery(f,20,40)="
                + count(s, IntPoint.newSetQuery("f", 20, 40)));
      }
    }
    // B: two segments, then forceMerge(1)
    try (Directory dir = new NIOFSDirectory(tmp.resolve("b"));
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(doc(t21, 30, 5));
      w.addDocument(doc(t21, 10, 5));
      w.commit();
      w.addDocument(doc(t21, 20, 5));
      w.addDocument(doc(t21, 40, 9));
      w.commit();
      String before;
      try (DirectoryReader r = DirectoryReader.open(w)) {
        before = r.leaves().size() + " segments, newSetQuery(f,10,30)=" + count(searcher(r), IntPoint.newSetQuery("f", 10, 30));
      }
      w.forceMerge(1);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        System.out.println(
            "[B merge] before: "
                + before
                + "  after forceMerge(1): "
                + r.leaves().size()
                + " segment, newSetQuery(f,10,30)="
                + count(searcher(r), IntPoint.newSetQuery("f", 10, 30))
                + " visitOrder="
                + visitOrder(r.leaves().get(0).reader(), 2));
      }
    }
    // C: 3 dims / 2 index dims, 2-D set query
    FieldType t32 = type(3, 2);
    try (Directory dir = new NIOFSDirectory(tmp.resolve("c"))) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(doc(t32, 1, 2, 100));
        w.addDocument(doc(t32, 1, 2, 200));
        w.addDocument(doc(t32, 3, 4, 300));
        w.addDocument(doc(t32, 5, 6, 7));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        System.out.println(
            "[C 3 dims/2 index dims] 2-D set{(1,2)}="
                + count(searcher(r), setQuery2D("f", new int[] {1, 2}))
                + " (expected 2)");
      }
    }
  }

  /** Same data and query generator as an earlier check's testScaleSeeded. */
  static void scale() throws Exception {
    final int n = 200_000;
    Random rnd = new Random(20261022L);
    int[] idx = new int[n];
    FieldType t21 = type(2, 1);
    FieldType t11 = type(1, 1);
    Path tmp = Files.createTempDirectory("selidx-scale");
    try (Directory dir = new NIOFSDirectory(tmp);
        IndexWriter w =
            new IndexWriter(
                dir, new IndexWriterConfig().setMaxBufferedDocs(1_000_000).setRAMBufferSizeMB(256))) {
      for (int i = 0; i < n; i++) {
        idx[i] = rnd.nextInt(5_000);
        int data = rnd.nextInt(4);
        Document d = new Document();
        d.add(new Field("f", pack(idx[i], data), t21));
        d.add(new Field("g", pack(idx[i]), t11)); // control: plain 1-D copy
        w.addDocument(d);
      }
      w.forceMerge(1);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = searcher(r);
        int queries = 200, wrong = 0, ctlWrong = 0;
        long expectedTotal = 0, gotTotal = 0;
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
          expectedTotal += expected;
          gotTotal += got;
          if (got != expected) wrong++;
          if (ctl != expected) ctlWrong++;
        }
        System.out.println(
            "[scale] docs="
                + n
                + " segments="
                + r.leaves().size()
                + " wrongQueries="
                + wrong
                + "/"
                + queries
                + " hitsReturned="
                + gotTotal
                + " expectedHits="
                + expectedTotal
                + " missed="
                + (expectedTotal - gotTotal)
                + " controlFieldWrongQueries="
                + ctlWrong);
      }
    } finally {
      try (java.util.stream.Stream<Path> wk = Files.walk(tmp)) {
        wk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
      }
    }
  }
}
