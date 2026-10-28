package org.apache.lucene.spatial;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial.bbox.BBoxStrategy;
import org.apache.lucene.spatial.vector.PointVectorStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.context.SpatialContextFactory;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.impl.RectangleImpl;

/**
 * Value a distance source gives a document that has no indexed shape, in a non-geo (Cartesian)
 * context. Uses only public API: SpatialStrategy#makeDistanceValueSource and
 * SpatialStrategy#makeRecipDistanceValueSource of PointVectorStrategy and BBoxStrategy.
 *
 * <p>Index: NEAR at (100,0), FAR at (500,0), NONE without any spatial field. Query point (0,0).
 * World bounds -1000..1000, so Cartesian distances above 180 are legal.
 */
public class TestPointVectorNonGeoMissingDistance extends LuceneTestCase {

  private static final long NEAR = 1, FAR = 2, NONE = 3;

  private static String name(long id) {
    return id == NEAR ? "near" : id == FAR ? "far" : id == NONE ? "none" : "?" + id;
  }

  private static SpatialContext cartesian() {
    SpatialContextFactory f = new SpatialContextFactory();
    f.geo = false;
    f.worldBounds = new RectangleImpl(-1000, 1000, -1000, 1000, null);
    return f.newSpatialContext();
  }

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  /** Indexes NEAR=(100,0), FAR=(500,0), NONE with every given strategy on the same documents. */
  private void build(SpatialContext ctx, SpatialStrategy... strategies) throws IOException {
    build(ctx, 100, 500, strategies);
  }

  private void build(SpatialContext ctx, double nearX, double farX, SpatialStrategy... strategies)
      throws IOException {
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      addDoc(w, NEAR, ctx.getShapeFactory().pointXY(nearX, 0), strategies);
      addDoc(w, FAR, ctx.getShapeFactory().pointXY(farX, 0), strategies);
      addDoc(w, NONE, null, strategies);
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
    assertEquals(1, reader.leaves().size());
    searcher = newSearcher(reader);
  }

  private static void addDoc(IndexWriter w, long id, Shape shape, SpatialStrategy... strategies)
      throws IOException {
    Document doc = new Document();
    doc.add(new NumericDocValuesField("id", id));
    if (shape != null) {
      for (SpatialStrategy s : strategies) {
        for (Field f : s.createIndexableFields(shape)) {
          doc.add(f);
        }
      }
    }
    w.addDocument(doc);
  }

  @Override
  public void tearDown() throws Exception {
    if (reader != null) reader.close();
    if (dir != null) dir.close();
    super.tearDown();
  }

  private long idOf(int doc) throws IOException {
    LeafReaderContext leaf = reader.leaves().get(0);
    NumericDocValues ids = DocValues.getNumeric(leaf.reader(), "id");
    assertTrue(ids.advanceExact(doc));
    return ids.longValue();
  }

  /** id -> value of the source for every document. */
  private Map<Long, Double> values(DoubleValuesSource src) throws IOException {
    src = src.rewrite(searcher);
    Map<Long, Double> out = new HashMap<>();
    LeafReaderContext leaf = reader.leaves().get(0);
    DoubleValues v = src.getValues(leaf, null);
    for (int doc = 0; doc < leaf.reader().maxDoc(); doc++) {
      assertTrue("value source must have a value (default) for doc " + doc, v.advanceExact(doc));
      out.put(idOf(doc), v.doubleValue());
    }
    return out;
  }

  /** Document names in ascending order of the source (DoubleValuesSource#getSortField(false)). */
  private List<String> ascendingOrder(DoubleValuesSource src) throws IOException {
    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, new Sort(src.getSortField(false)));
    List<String> order = new ArrayList<>();
    for (ScoreDoc sd : td.scoreDocs) {
      order.add(name(idOf(sd.doc)));
    }
    return order;
  }

  // ---- controls (expected to pass) ----

  /** Sibling strategy in the same non-geo context: the missing document gets Double.MAX_VALUE. */
  public void testControlBBoxNonGeoMissingValue() throws IOException {
    SpatialContext ctx = cartesian();
    BBoxStrategy bbox = BBoxStrategy.newInstance(ctx, "bbox");
    build(ctx, bbox);
    Point origin = ctx.getShapeFactory().pointXY(0, 0);
    Map<Long, Double> v = values(bbox.makeDistanceValueSource(origin, 1.0));
    System.out.println("bbox non-geo values: " + v);
    assertEquals(100.0, v.get(NEAR), 1e-9);
    assertEquals(500.0, v.get(FAR), 1e-9);
    assertEquals(Double.MAX_VALUE, v.get(NONE), 0.0);
    assertEquals(List.of("near", "far", "none"), ascendingOrder(bbox.makeDistanceValueSource(origin)));
  }

  /** PointVectorStrategy in the geo context: 180 (the largest geo distance), as upstream tests. */
  public void testControlPointVectorGeoMissingValue() throws IOException {
    SpatialContext ctx = SpatialContext.GEO;
    PointVectorStrategy pv = PointVectorStrategy.newInstance(ctx, "pv");
    build(ctx, 10, 50, pv); // geo-legal longitudes; distances along the equator are 10 and 50
    Map<Long, Double> v = values(pv.makeDistanceValueSource(ctx.getShapeFactory().pointXY(0, 0)));
    System.out.println("pv geo values: " + v);
    assertEquals(10.0, v.get(NEAR), 1e-6);
    assertEquals(50.0, v.get(FAR), 1e-6);
    assertEquals(180.0, v.get(NONE), 0.0);
  }

  // ---- the claim (expected to fail on affected versions) ----

  /** Non-geo: the missing document should get Double.MAX_VALUE, like BBox/Serialized/PrefixTree. */
  public void testPointVectorNonGeoMissingValue() throws IOException {
    SpatialContext ctx = cartesian();
    PointVectorStrategy pv = PointVectorStrategy.newInstance(ctx, "pv");
    build(ctx, pv);
    Map<Long, Double> v = values(pv.makeDistanceValueSource(ctx.getShapeFactory().pointXY(0, 0)));
    System.out.println("pv non-geo values: " + v);
    assertEquals(100.0, v.get(NEAR), 1e-9);
    assertEquals(500.0, v.get(FAR), 1e-9);
    assertEquals(
        "non-geo PointVectorStrategy distance for a document without a point",
        Double.MAX_VALUE,
        v.get(NONE),
        0.0);
  }

  /** Non-geo: ascending distance sort should list the document without a point last. */
  public void testPointVectorNonGeoAscendingSort() throws IOException {
    SpatialContext ctx = cartesian();
    PointVectorStrategy pv = PointVectorStrategy.newInstance(ctx, "pv");
    build(ctx, pv);
    List<String> order =
        ascendingOrder(pv.makeDistanceValueSource(ctx.getShapeFactory().pointXY(0, 0)));
    System.out.println("pv non-geo ascending order: " + order);
    assertEquals("ascending distance order", List.of("near", "far", "none"), order);
  }

  /** Non-geo: recip distance score of the document without a point should not beat a real one. */
  public void testPointVectorNonGeoRecipScore() throws IOException {
    SpatialContext ctx = cartesian();
    PointVectorStrategy pv = PointVectorStrategy.newInstance(ctx, "pv");
    BBoxStrategy bbox = BBoxStrategy.newInstance(ctx, "bbox");
    build(ctx, pv, bbox);
    Shape queryShape = ctx.getShapeFactory().rect(-1000, 1000, -1000, 1000);
    Map<Long, Double> pvScores = values(pv.makeRecipDistanceValueSource(queryShape));
    Map<Long, Double> bboxScores = values(bbox.makeRecipDistanceValueSource(queryShape));
    System.out.println("pv non-geo recip scores: " + pvScores);
    System.out.println("bbox non-geo recip scores: " + bboxScores);
    assertTrue(
        "bbox control: missing doc scores below FAR", bboxScores.get(NONE) < bboxScores.get(FAR));
    assertTrue(
        "pv: document without a point scores "
            + pvScores.get(NONE)
            + " which beats FAR at distance 500 ("
            + pvScores.get(FAR)
            + ")",
        pvScores.get(NONE) < pvScores.get(FAR));
  }
}
