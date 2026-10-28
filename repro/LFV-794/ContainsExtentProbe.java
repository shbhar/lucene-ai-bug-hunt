package probe;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LatLonShape;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.document.XYShape;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;

/**
 * How far a CONTAINS box may stick out through the boundary piece (2,0)-(5,0) and still match.
 * Default IndexWriterConfig, one document. Prints one line per box; exits 1 if any box that is not
 * contained matches.
 */
public class ContainsExtentProbe {

  static int count(Field[] fields, Query q) throws Exception {
    try (ByteBuffersDirectory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : fields) doc.add(f);
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        return new IndexSearcher(r).count(q);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    XYPolygon xy =
        new XYPolygon(
            new float[] {0, 7, 12, 0},
            new float[] {0, 12, 0, 0},
            new XYPolygon(new float[] {2, 5, 3, 2}, new float[] {0, 1, 3, 0}),
            new XYPolygon(new float[] {5, 8, 6, 5}, new float[] {0, 1, 3, 0}));
    Polygon ll =
        new Polygon(
            new double[] {0, 12, 0, 0},
            new double[] {0, 7, 12, 0},
            new Polygon(new double[] {0, 1, 3, 0}, new double[] {2, 5, 3, 2}),
            new Polygon(new double[] {0, 1, 3, 0}, new double[] {5, 8, 6, 5}));
    Field[] xyFields = XYShape.createIndexableFields("s", xy);
    Field[] llFields = LatLonShape.createIndexableFields("s", ll);
    // {minX, maxX, minY, maxY, expected}
    double[][] boxes = {
      {3, 4, 0.05, 0.3, 1}, // inside (control)
      {3, 4, -0.5, 0.3, 0}, // 0.5 of its 0.8 height below y = 0
      {2.5, 4.5, -1, 0.1, 0}, // 2.0 of its 2.2 area below y = 0
      {3, 4, -10, 0.3, 0},
      {3, 4, -80, 0.3, 0},
      {1, 1.5, -0.5, 0.3, 0}, // crosses the outer edge left of hole A (control)
      {9, 10, -0.5, 0.3, 0}, // crosses the outer edge right of hole B (control)
    };
    boolean wrong = false;
    for (double[] b : boxes) {
      int xyC =
          count(
              xyFields,
              XYShape.newBoxQuery(
                  "s", QueryRelation.CONTAINS, (float) b[0], (float) b[1], (float) b[2], (float) b[3]));
      int xyI =
          count(
              xyFields,
              XYShape.newBoxQuery(
                  "s",
                  QueryRelation.INTERSECTS,
                  (float) b[0],
                  (float) b[1],
                  (float) b[2],
                  (float) b[3]));
      int llC =
          count(llFields, LatLonShape.newBoxQuery("s", QueryRelation.CONTAINS, b[2], b[3], b[0], b[1]));
      int llI =
          count(
              llFields, LatLonShape.newBoxQuery("s", QueryRelation.INTERSECTS, b[2], b[3], b[0], b[1]));
      int exp = (int) b[4];
      boolean bad = xyC != exp || llC != exp;
      wrong |= bad;
      System.out.printf(
          "box x[%s,%s] y[%s,%s]: expected CONTAINS=%d | XY CONTAINS=%d INTERSECTS=%d | LatLon CONTAINS=%d INTERSECTS=%d%s%n",
          b[0], b[1], b[2], b[3], exp, xyC, xyI, llC, llI, bad ? "  <-- wrong" : "");
    }
    System.exit(wrong ? 1 : 0);
  }
}
