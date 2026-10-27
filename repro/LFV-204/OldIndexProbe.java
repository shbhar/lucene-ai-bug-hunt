import java.nio.file.Paths;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.XYShape;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Does a fixed Lucene repair shapes that an affected Lucene already indexed? Run "write" with one
 * jar, then "read" with another jar on the same directory (-Dprobe.dir, -Dprobe.mode). The read step
 * adds the same two polygons again (written by the reading jar), checks CONTAINS for the old and
 * the new copies, runs forceMerge(1) and checks again. Exit 1 if any CONTAINS answer is wrong.
 */
public class OldIndexProbe {
  // boundary-crossing box: the polygon does NOT contain it (expected CONTAINS 0)
  static final XYPolygon FP = new XYPolygon(new float[] {6, -1, -8, 0, 6}, new float[] {0, 7, 8, 1, 0});
  static final float[] FP_BOX = {-4.75f, -4.25f, 7.25f, 7.75f};
  // inner box, at least 0.8 from every edge: the polygon DOES contain it (expected CONTAINS 1)
  static final XYPolygon FN = new XYPolygon(new float[] {3, 6, 8, 1, 3}, new float[] {3, 0, 5, 8, 3});
  static final float[] FN_BOX = {3.25f, 3.75f, 3.75f, 4.25f};

  static void add(IndexWriter w, String id, XYPolygon p) throws Exception {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.NO));
    for (Field f : XYShape.createIndexableFields("shape", p)) {
      doc.add(f);
    }
    w.addDocument(doc);
  }

  static int contains(IndexSearcher s, String id, float[] box) throws Exception {
    Query shape = XYShape.newBoxQuery("shape", QueryRelation.CONTAINS, box[0], box[1], box[2], box[3]);
    Query q =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("id", id)), Occur.FILTER)
            .add(shape, Occur.FILTER)
            .build();
    return s.count(q);
  }

  static int check(Directory dir, String when) throws Exception {
    int wrong = 0;
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      for (String gen : new String[] {"old", "new"}) {
        if (s.count(new TermQuery(new Term("id", gen + "-fp"))) == 0) {
          continue;
        }
        int fp = contains(s, gen + "-fp", FP_BOX), fn = contains(s, gen + "-fn", FN_BOX);
        System.out.println("OBS " + when + " segments=" + r.leaves().size() + " docs=" + gen
            + " boundary_box_contains=" + fp + " (expected 0) inner_box_contains=" + fn
            + " (expected 1)");
        wrong += (fp != 0 ? 1 : 0) + (fn != 1 ? 1 : 0);
      }
    }
    return wrong;
  }

  public static void main(String[] args) throws Exception {
    String mode = System.getProperty("probe.mode");
    try (Directory dir = FSDirectory.open(Paths.get(System.getProperty("probe.dir")))) {
      if ("write".equals(mode)) {
        try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
          add(w, "old-fp", FP);
          add(w, "old-fn", FN);
        }
        System.exit(check(dir, "written") == 0 ? 0 : 1);
      }
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        add(w, "new-fp", FP);
        add(w, "new-fn", FN);
      }
      int wrong = check(dir, "reopened");
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.forceMerge(1);
      }
      wrong += check(dir, "after-forceMerge(1)");
      System.out.println("OBS wrong_answers=" + wrong);
      System.exit(wrong == 0 ? 0 : 1);
    }
  }
}
