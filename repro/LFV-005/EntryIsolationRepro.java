import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Version;

/**
 * Plain-Java repro (public API only, default IndexWriterConfig and default codec).
 *
 * <p>1450 docs, 16-dim EUCLIDEAN vectors. Doc 171 ("X") sits at -10 on axis 0, cluster A (docs
 * 0..29 and 215, 31 docs) at 0, cluster B (the other 1418 docs) at +10; noise sigma 0.3. Delete
 * cluster A by term (2.1% of docs), then forceMerge(1). Prints recall@10 of 50 queries in cluster B
 * before and after the merge. Arg "control" deletes 31 B docs instead of cluster A.
 */
public class EntryIsolationRepro {
  static final int N = 1450, X = 171, DIM = 16, EXTRA = 200;
  static final Set<Integer> A = new HashSet<>();

  static {
    for (int i = 0; i < 30; i++) A.add(i);
    A.add(215);
  }

  public static void main(String[] args) throws Exception {
    boolean control = args.length > 0 && args[0].equals("control");
    // "ci": stop after the first forceMerge(1) and print CheckIndex's HNSW connectedness lines
    boolean ciOnly = args.length > 0 && args[0].equals("ci");
    Random r = new Random(20261022L);
    float[][] v = new float[N + EXTRA][DIM];
    for (int i = 0; i < N; i++) {
      float center = i == X ? -10f : (A.contains(i) ? 0f : 10f);
      for (int d = 0; d < DIM; d++) v[i][d] = (float) (r.nextGaussian() * 0.3);
      v[i][0] += center;
    }
    Random rx = new Random(99L); // EXTRA docs added after the first merge, all in cluster B
    for (int i = N; i < N + EXTRA; i++) {
      for (int d = 0; d < DIM; d++) v[i][d] = (float) (rx.nextGaussian() * 0.3);
      v[i][0] += 10f;
    }
    Random rq = new Random(7L);
    float[][] qs = new float[50][DIM];
    for (float[] q : qs) {
      for (int d = 0; d < DIM; d++) q[d] = (float) (rq.nextGaussian() * 0.3);
      q[0] += 10f;
    }
    Set<Integer> deleted = new HashSet<>();
    if (control) {
      for (int i = 1000; i < 1031; i++) deleted.add(i); // 31 docs of cluster B
    } else {
      deleted.addAll(A);
    }
    System.out.println("Lucene " + Version.LATEST + (control ? " CONTROL (31 cluster-B docs deleted)" : ""));
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
      for (int i = 0; i < N; i++) {
        Document doc = new Document();
        doc.add(new StoredField("id", i));
        doc.add(new StringField("del", deleted.contains(i) ? "yes" : "no", Field.Store.NO));
        doc.add(new KnnFloatVectorField("v", v[i], VectorSimilarityFunction.EUCLIDEAN));
        w.addDocument(doc);
      }
      w.commit(); // one segment of 1450 docs
      w.deleteDocuments(new Term("del", "yes"));
      w.commit();
      Set<Integer> live = new HashSet<>();
      for (int i = 0; i < N; i++) if (!deleted.contains(i)) live.add(i);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        System.out.println("before forceMerge(1): " + report(reader, v, live, qs));
      }
      w.forceMerge(1);
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        System.out.println("after  forceMerge(1): " + report(reader, v, live, qs));
      }
      if (ciOnly) {
        w.close();
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        CheckIndex.Status cst;
        try (CheckIndex ci = new CheckIndex(dir)) {
          ci.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS); // includes the HNSW graph checks
          ci.setInfoStream(new java.io.PrintStream(bytes, true, "UTF-8"), true);
          cst = ci.checkIndex();
        }
        for (String line : bytes.toString("UTF-8").split("\n")) {
          if (line.contains("connected") || line.contains("hnsw") || line.contains("No problems")) {
            System.out.println("CheckIndex: " + line.trim());
          }
        }
        System.out.println("CheckIndex clean=" + cst.clean);
        return;
      }
      // later life of the index: add EXTRA new cluster-B docs in a new segment, merge again
      for (int i = N; i < N + EXTRA; i++) {
        Document doc = new Document();
        doc.add(new StoredField("id", i));
        doc.add(new StringField("del", "no", Field.Store.NO));
        doc.add(new KnnFloatVectorField("v", v[i], VectorSimilarityFunction.EUCLIDEAN));
        w.addDocument(doc);
        live.add(i);
      }
      w.commit();
      w.forceMerge(1);
      w.commit();
      w.close();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        System.out.println(
            "after adding " + EXTRA + " docs + 2nd forceMerge(1), reopened from the Directory: "
                + report(reader, v, live, qs));
      }
      CheckIndex.Status st;
      try (CheckIndex ci = new CheckIndex(dir)) {
        ci.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS); // includes the HNSW graph checks
        st = ci.checkIndex();
      }
      System.out.println("CheckIndex clean=" + st.clean);
    }
  }

  static float dist2(float[] a, float[] b) {
    float s = 0;
    for (int i = 0; i < a.length; i++) s += (a[i] - b[i]) * (a[i] - b[i]);
    return s;
  }

  static String report(DirectoryReader reader, float[][] v, Set<Integer> live, float[][] qs)
      throws Exception {
    IndexSearcher s = new IndexSearcher(reader);
    double sum = 0;
    String q0 = null;
    for (int qi = 0; qi < qs.length; qi++) {
      float[] q = qs[qi];
      TopDocs td = s.search(new KnnFloatVectorQuery("v", q, 10), 10);
      List<Integer> got = new ArrayList<>();
      for (ScoreDoc sd : td.scoreDocs) {
        got.add(s.storedFields().document(sd.doc).getField("id").numericValue().intValue());
      }
      List<Integer> ids = new ArrayList<>(live);
      ids.sort((x, y) -> Float.compare(dist2(v[x], q), dist2(v[y], q)));
      Set<Integer> exact = new HashSet<>(ids.subList(0, 10));
      int hit = 0;
      for (int id : got) if (exact.contains(id)) hit++;
      sum += hit / 10.0;
      if (qi == 0) q0 = "query0 returned " + got.size() + " hits, ids=" + got;
    }
    return String.format(
        Locale.ROOT,
        "segments=%d liveDocs=%d recall@10=%.3f (50 queries); %s",
        reader.leaves().size(),
        reader.numDocs(),
        sum / qs.length,
        q0);
  }
}
