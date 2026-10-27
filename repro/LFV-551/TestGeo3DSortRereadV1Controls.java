package geo3dverify;

import java.io.IOException;
import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.spatial3d.Geo3DDocValuesField;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Controls for TestGeo3DSortRereadV1; all expected to PASS on main. They show the oracle and data
 * set-up are sound and bound the trigger: (a) a segment whose docs all have one value, (b) the same
 * multi-valued data when the queue never fills before the competitive doc (n >= hits).
 */
public class TestGeo3DSortRereadV1Controls extends LuceneTestCase {

  private static final String F = "loc";

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
    assertEquals(1, r.leaves().size());
    return r;
  }

  private static int[] docs(TopFieldDocs td) {
    return Arrays.stream(td.scoreDocs).mapToInt(sd -> sd.doc).toArray();
  }

  public void testAllSingleValuedSegmentTop2() throws IOException {
    PlanetModel pm = PlanetModel.SPHERE;
    double[][] lats = {{3}, {4}, {1}, {6}, {7}};
    try (Directory dir = new MMapDirectory(createTempDir());
        DirectoryReader r = index(dir, pm, lats)) {
      Sort sort = new Sort(Geo3DDocValuesField.newDistanceSort(F, 0, 0, 1.0, pm));
      TopFieldDocs td = new IndexSearcher(r).search(new MatchAllDocsQuery(), 2, sort);
      assertArrayEquals(new int[] {2, 0}, docs(td));
      assertEquals(Math.toRadians(1), (Double) ((FieldDoc) td.scoreDocs[0]).fields[0], 1e-6);
    }
  }

  public void testMultiValuedSegmentQueueNeverFull() throws IOException {
    PlanetModel pm = PlanetModel.SPHERE;
    double[][] lats = {{3}, {4}, {10, 1}, {6}, {7}};
    try (Directory dir = new MMapDirectory(createTempDir());
        DirectoryReader r = index(dir, pm, lats)) {
      Sort sort = new Sort(Geo3DDocValuesField.newDistanceSort(F, 0, 0, 1.0, pm));
      TopFieldDocs td = new IndexSearcher(r).search(new MatchAllDocsQuery(), 5, sort);
      assertArrayEquals(new int[] {2, 0, 1, 3, 4}, docs(td));
      assertEquals(Math.toRadians(1), (Double) ((FieldDoc) td.scoreDocs[0]).fields[0], 1e-6);
    }
  }
}
