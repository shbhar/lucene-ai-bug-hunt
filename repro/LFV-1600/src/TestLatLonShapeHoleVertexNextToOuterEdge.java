import static org.junit.Assert.assertEquals;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LatLonShape;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.Test;

public class TestLatLonShapeHoleVertexNextToOuterEdge {

  @Test
  public void testPointInHoleDoesNotMatch() throws Exception {
    Polygon hole =
        new Polygon(
            new double[] {6.8, 5.6, 6.5, 6.8}, new double[] {4.5, 6.1, 0.5312500000000001, 4.5});
    Polygon polygon =
        new Polygon(
            new double[] {7.5, 7.5, 4.3, 4.2, 7.5},
            new double[] {13.5, 1.5, -1.6, 13.5, 13.5},
            hole);
    Query pointInHole =
        LatLonShape.newPointQuery("shape", QueryRelation.INTERSECTS, new double[] {6.5, 2.0});
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      for (Field field : LatLonShape.createIndexableFields("shape", polygon)) {
        doc.add(field);
      }
      writer.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(writer)) {
        // expected 0: (lat 6.5, lon 2.0) is in the hole (lat 6.26 to 6.61 at lon 2.0); actual 1
        assertEquals(0, new IndexSearcher(reader).count(pointInHole));
      }
    }
  }
}
