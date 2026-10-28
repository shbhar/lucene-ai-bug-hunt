import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.spatial.ShapeValues;
import org.apache.lucene.spatial.bbox.BBoxStrategy;
import org.apache.lucene.spatial.spatial4j.Geo3dSpatialContextFactory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;

/**
 * Prints, per spatial context, the BBoxStrategy shape value and the self-overlap score (query box =
 * indexed box, queryTargetProportion 0.5) of one document. Box 1 lies on one side of longitude 0;
 * box 2 spans longitude 0, where the separate getCrossesDateLine bug of Geo3dRectangleShape applies.
 */
public class FixOrderProbe {
  static final double[][] BOXES = {{10, 20, 30, 40}, {-10, 10, -10, 10}};

  public static void main(String[] args) throws Exception {
    SpatialContext geo3d = new Geo3dSpatialContextFactory().newSpatialContext();
    for (SpatialContext ctx : new SpatialContext[] {SpatialContext.GEO, geo3d}) {
      String name = ctx == geo3d ? "geo3d" : "spatial4j";
      for (double[] b : BOXES) {
        System.out.println("PROBE " + name + " box=" + str(b) + " " + selfOverlap(ctx, b));
      }
    }
  }

  static String str(double[] b) {
    return "(" + b[0] + ", " + b[1] + ", " + b[2] + ", " + b[3] + ")";
  }

  static String selfOverlap(SpatialContext ctx, double[] b) throws Exception {
    BBoxStrategy strategy = BBoxStrategy.newInstance(ctx, "bbox");
    Rectangle box = ctx.getShapeFactory().rect(b[0], b[1], b[2], b[3]);
    Path tmp = Files.createTempDirectory("fixorder");
    try (Directory dir = FSDirectory.open(tmp)) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(box)) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LeafReaderContext leaf = reader.leaves().get(0);
        ShapeValues sv = strategy.makeShapeValueSource().getValues(leaf);
        sv.advanceExact(0);
        Rectangle r = (Rectangle) sv.value();
        String shape = "shapeValue=(" + r.getMinX() + ", " + r.getMaxX() + ", " + r.getMinY() + ", "
            + r.getMaxY() + ") crossesDateLine=" + r.getCrossesDateLine();
        DoubleValues dv = strategy.makeOverlapRatioValueSource(box, 0.5).getValues(leaf, null);
        dv.advanceExact(0);
        String score;
        try {
          score = "selfOverlapScore=" + dv.doubleValue();
        } catch (AssertionError e) {
          score = "selfOverlapScore=AssertionError(" + e.getMessage() + ")";
        }
        return shape + " " + score;
      }
    } finally {
      for (Path p : Files.list(tmp).toList()) {
        Files.delete(p);
      }
      Files.delete(tmp);
    }
  }
}
