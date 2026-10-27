package geo3dverify;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.spatial3d.Geo3DDocValuesField;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.tests.index.AssertingDirectoryReader;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Independent check of the Geo3D sort comparators on a segment whose Geo3DDocValuesField is
 * multi-valued. Public API only (the test lives outside the Lucene packages).
 *
 * <p>All points lie on the meridian lon=0; the sort origin is (0,0). The arc distance from the
 * origin is therefore monotonic in |lat|, so the correct order is "ascending min |lat| per
 * document" and can be computed without Lucene. Documented contract
 * (Geo3DDocValuesField#newDistanceSort and the other factories): "If a document contains multiple
 * values for the field, the closest distance ... is used".
 */
public class TestGeo3DSortRereadV1 extends LuceneTestCase {

  private static final String F = "loc";

  /** one segment, default production codec, documents in docID order */
  private static DirectoryReader index(Directory dir, PlanetModel pm, double[][] latsDeg)
      throws IOException {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (double[] lats : latsDeg) {
        Document d = new Document();
        for (double lat : lats) {
          d.add(new Geo3DDocValuesField(F, new GeoPoint(pm, Math.toRadians(lat), 0.0), pm));
        }
        w.addDocument(d);
      }
      w.commit();
    }
    DirectoryReader r = DirectoryReader.open(dir);
    assertEquals("expected a single segment", 1, r.leaves().size());
    return r;
  }

  private static double minAbsLat(double[] lats) {
    double m = Double.POSITIVE_INFINITY;
    for (double l : lats) {
      m = Math.min(m, Math.abs(l));
    }
    return m;
  }

  /** the correct full order, computed without Lucene */
  private static int[] expectedOrder(double[][] latsDeg) {
    Integer[] ids = new Integer[latsDeg.length];
    for (int i = 0; i < ids.length; i++) {
      ids[i] = i;
    }
    Arrays.sort(
        ids,
        Comparator.<Integer>comparingDouble(i -> minAbsLat(latsDeg[i])).thenComparingInt(i -> i));
    return Arrays.stream(ids).mapToInt(Integer::intValue).toArray();
  }

  private static int[] docs(TopDocs td) {
    return Arrays.stream(td.scoreDocs).mapToInt(sd -> sd.doc).toArray();
  }

  /**
   * Asserts the top-n order; if checkValues, also that each returned distance is the arc distance
   * of the document's closest value (meridian arc, so ~ toRadians(minLat) * meanRadius).
   */
  private static void checkTopN(
      IndexSearcher s,
      Sort sort,
      double[][] latsDeg,
      int n,
      PlanetModel pm,
      boolean checkValues,
      double relTol)
      throws IOException {
    TopFieldDocs td = s.search(new MatchAllDocsQuery(), n, sort);
    int[] exp = Arrays.copyOf(expectedOrder(latsDeg), n);
    int[] act = docs(td);
    StringBuilder vals = new StringBuilder();
    for (int i = 0; i < td.scoreDocs.length; i++) {
      vals.append(' ').append(((FieldDoc) td.scoreDocs[i]).fields[0]);
    }
    assertArrayEquals(
        sort + " n=" + n + " expected order " + Arrays.toString(exp) + " got " + Arrays.toString(act)
            + " values:" + vals,
        exp,
        act);
    if (checkValues) {
      for (int i = 0; i < n; i++) {
        double got = (Double) ((FieldDoc) td.scoreDocs[i]).fields[0];
        double want = Math.toRadians(minAbsLat(latsDeg[act[i]])) * pm.getMeanRadius();
        assertEquals("distance of doc " + act[i], want, got, relTol * want);
      }
    }
  }

  // Data: doc2 is the closest (min 1 deg) but is multi-valued {10, 1}; it is reached only after
  // the top-2 queue is already full with doc0 (3 deg) and doc1 (4 deg).
  private static final double[][] MID_MULTI = {{3}, {4}, {10, 1}, {6}, {7}};

  public void testDistanceSortSphereTop2() throws IOException {
    PlanetModel pm = PlanetModel.SPHERE;
    try (Directory dir = new MMapDirectory(createTempDir());
        DirectoryReader r = index(dir, pm, MID_MULTI)) {
      Sort sort = new Sort(Geo3DDocValuesField.newDistanceSort(F, 0, 0, 1.0, pm));
      checkTopN(new IndexSearcher(r), sort, MID_MULTI, 2, pm, true, 1e-4);
    }
  }

  public void testDistanceSortWgs84Top2() throws IOException {
    PlanetModel pm = PlanetModel.WGS84;
    try (Directory dir = new MMapDirectory(createTempDir());
        DirectoryReader r = index(dir, pm, MID_MULTI)) {
      Sort sort = new Sort(Geo3DDocValuesField.newDistanceSort(F, 0, 0, 3_000_000, pm));
      // ellipsoid: meridian arc differs from the mean-radius arc by < 1%
      checkTopN(new IndexSearcher(r), sort, MID_MULTI, 2, pm, true, 1e-2);
    }
  }

  public void testPathSortSphereTop2() throws IOException {
    PlanetModel pm = PlanetModel.SPHERE;
    try (Directory dir = new MMapDirectory(createTempDir());
        DirectoryReader r = index(dir, pm, MID_MULTI)) {
      // a short east-west path through the origin; distance along the path for points on lon=0
      // is still monotonic in |lat| (path distance = distance to the path + along-path offset 0)
      Sort sort =
          new Sort(
              Geo3DDocValuesField.newPathSort(
                  F, new double[] {0, 0}, new double[] {-1, 1}, 1.0, pm));
      checkTopN(new IndexSearcher(r), sort, MID_MULTI, 2, pm, false, 0);
    }
  }

  public void testOutsideDistanceSortSphereTop2() throws IOException {
    PlanetModel pm = PlanetModel.SPHERE;
    try (Directory dir = new MMapDirectory(createTempDir());
        DirectoryReader r = index(dir, pm, MID_MULTI)) {
      // tiny circle around the origin; every point is outside, outside distance grows with |lat|
      Sort sort = new Sort(Geo3DDocValuesField.newOutsideDistanceSort(F, 0, 0, 1e-4, pm));
      checkTopN(new IndexSearcher(r), sort, MID_MULTI, 2, pm, false, 0);
    }
  }

  public void testOutsideBoxSortSphereTop2() throws IOException {
    PlanetModel pm = PlanetModel.SPHERE;
    try (Directory dir = new MMapDirectory(createTempDir());
        DirectoryReader r = index(dir, pm, MID_MULTI)) {
      Sort sort =
          new Sort(Geo3DDocValuesField.newOutsideBoxSort(F, -0.1, 0.1, -0.1, 0.1, pm));
      checkTopN(new IndexSearcher(r), sort, MID_MULTI, 2, pm, false, 0);
    }
  }

  /** the competitive document itself has ONE value; another document makes the field multi-valued */
  public void testSingleValuedDocInMultiValuedSegment() throws IOException {
    PlanetModel pm = PlanetModel.SPHERE;
    double[][] lats = {{3}, {4}, {1}, {6}, {7, 8}};
    try (Directory dir = new MMapDirectory(createTempDir());
        DirectoryReader r = index(dir, pm, lats)) {
      Sort sort = new Sort(Geo3DDocValuesField.newDistanceSort(F, 0, 0, 1.0, pm));
      checkTopN(new IndexSearcher(r), sort, lats, 2, pm, true, 1e-4);
    }
  }

  /** the competitive multi-valued document is the last one with values */
  public void testLastDocMultiValuedMMap() throws IOException {
    PlanetModel pm = PlanetModel.SPHERE;
    double[][] lats = {{3}, {4}, {10, 1}};
    try (Directory dir = new MMapDirectory(createTempDir());
        DirectoryReader r = index(dir, pm, lats)) {
      Sort sort = new Sort(Geo3DDocValuesField.newDistanceSort(F, 0, 0, 1.0, pm));
      checkTopN(new IndexSearcher(r), sort, lats, 2, pm, true, 1e-4);
    }
  }

  public void testLastDocMultiValuedByteBuffers() throws IOException {
    PlanetModel pm = PlanetModel.SPHERE;
    double[][] lats = {{3}, {4}, {10, 1}};
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader r = index(dir, pm, lats)) {
      Sort sort = new Sort(Geo3DDocValuesField.newDistanceSort(F, 0, 0, 1.0, pm));
      checkTopN(new IndexSearcher(r), sort, lats, 2, pm, true, 1e-4);
    }
  }

  /**
   * searchAfter path (compareTop then copy, before the queue fills). SPHERE only: mean radius 1,
   * so the FieldDoc value and the comparator's internal unit coincide and a units issue in
   * compareTop cannot interfere.
   */
  public void testSearchAfterSphere() throws IOException {
    PlanetModel pm = PlanetModel.SPHERE;
    try (Directory dir = new MMapDirectory(createTempDir());
        DirectoryReader r = index(dir, pm, MID_MULTI)) {
      Sort sort = new Sort(Geo3DDocValuesField.newDistanceSort(F, 0, 0, 1.0, pm));
      IndexSearcher s = new IndexSearcher(r);
      // everything is after 0.5 deg: page must equal the head of the full order
      FieldDoc after = new FieldDoc(0, Float.NaN, new Object[] {Math.toRadians(0.5)});
      TopDocs td = s.searchAfter(after, new MatchAllDocsQuery(), 3, sort);
      int[] exp = Arrays.copyOf(expectedOrder(MID_MULTI), 3);
      StringBuilder vals = new StringBuilder();
      for (int i = 0; i < td.scoreDocs.length; i++) {
        vals.append(' ').append(((FieldDoc) td.scoreDocs[i]).fields[0]);
      }
      assertArrayEquals(
          "searchAfter expected " + Arrays.toString(exp) + " got " + Arrays.toString(docs(td))
              + " values:" + vals,
          exp,
          docs(td));
    }
  }

  /**
   * Contract probe: the test framework's AssertingLeafReader asserts the SortedNumericDocValues
   * rule "Do not call [nextValue] more than docValueCount times for the document". With -ea this
   * shows the comparator itself violates the contract; with -da the wrapper is inert and the
   * wrong order shows instead.
   */
  public void testContractProbeAssertingReader() throws IOException {
    PlanetModel pm = PlanetModel.SPHERE;
    try (Directory dir = new MMapDirectory(createTempDir());
        DirectoryReader r = new AssertingDirectoryReader(index(dir, pm, MID_MULTI))) {
      Sort sort = new Sort(Geo3DDocValuesField.newDistanceSort(F, 0, 0, 1.0, pm));
      checkTopN(new IndexSearcher(r), sort, MID_MULTI, 2, pm, true, 1e-4);
    }
  }
}
