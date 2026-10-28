package org.apache.lucene.document;

import java.util.Arrays;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Probe: does a degenerate (zero-size) box query at the vertex answer correctly where the point query misses? */
public class TestXYShapeVertexWorkaroundProbe extends LuceneTestCase {
  public void testDegenerateBoxAtVertex() throws Exception {
    XYPolygon triangle = new XYPolygon(new float[] {0, 1, 3, 0}, new float[] {1, 2, 3, 1});
    XYPolygon hexagon =
        new XYPolygon(
            new float[] {539509, 522792, 517494, 518885, 528386, 528562, 539509},
            new float[] {4392295, 4390728, 4393155, 4395432, 4407722, 4411071, 4392295});
    StringBuilder out = new StringBuilder();
    for (XYPolygon poly : new XYPolygon[] {triangle, hexagon}) {
      try (Directory dir = newDirectory();
          IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : XYShape.createIndexableFields("shape", poly)) {
          doc.add(f);
        }
        w.addDocument(doc);
        try (DirectoryReader r = DirectoryReader.open(w)) {
          IndexSearcher s = newSearcher(r);
          int n = poly.numPoints() - 1;
          int[] point = new int[n], box = new int[n], pointDisjoint = new int[n], boxDisjoint = new int[n];
          for (int i = 0; i < n; i++) {
            float x = poly.getPolyX(i), y = poly.getPolyY(i);
            point[i] = s.count(XYShape.newPointQuery("shape", QueryRelation.INTERSECTS, new float[] {x, y}));
            pointDisjoint[i] = s.count(XYShape.newPointQuery("shape", QueryRelation.DISJOINT, new float[] {x, y}));
            Query b = XYShape.newBoxQuery("shape", QueryRelation.INTERSECTS, x, x, y, y);
            box[i] = s.count(b);
            boxDisjoint[i] = s.count(XYShape.newBoxQuery("shape", QueryRelation.DISJOINT, x, x, y, y));
          }
          out.append("vertices=").append(n)
              .append(" point INTERSECTS ").append(Arrays.toString(point))
              .append(" point DISJOINT ").append(Arrays.toString(pointDisjoint))
              .append(" zero-size box INTERSECTS ").append(Arrays.toString(box))
              .append(" zero-size box DISJOINT ").append(Arrays.toString(boxDisjoint)).append('\n');
        }
      }
    }
    System.out.println("PROBE\n" + out);
  }
}
