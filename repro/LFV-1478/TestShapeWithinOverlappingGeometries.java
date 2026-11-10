package org.apache.lucene.document;

import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.Line;
import org.apache.lucene.geo.Rectangle;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestShapeWithinOverlappingGeometries extends LuceneTestCase {

  // Expected: a line inside the union of two overlapping boxes is WITHIN them (1 hit); actual: 0.
  public void testLineWithinUnionOfOverlappingBoxes() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      for (Field f :
          LatLonShape.createIndexableFields("shape", new Line(new double[] {1.5, 1.5}, new double[] {0.5, 3.5}))) {
        doc.add(f);
      }
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        assertEquals(1, searcher.count(LatLonShape.newBoxQuery("shape", QueryRelation.WITHIN, 0, 3, 0, 4)));
        assertEquals(
            1,
            searcher.count(
                LatLonShape.newGeometryQuery(
                    "shape", QueryRelation.WITHIN, new Rectangle(0, 3, 0, 3), new Rectangle(0, 3, 1, 4))));
      }
    }
  }
}
