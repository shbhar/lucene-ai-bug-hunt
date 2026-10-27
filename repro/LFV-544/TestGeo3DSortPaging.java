/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package verify.v1;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.Field;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial3d.Geo3DDocValuesField;
import org.apache.lucene.spatial3d.Geo3DPoint;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check (public API only, outside the spatial3d package): paging a Geo3D distance-style
 * sort with {@link IndexSearcher#searchAfter(ScoreDoc, Query, int, Sort)} must return, page by page,
 * exactly the documents of the single full sorted search.
 *
 * <p>Index: 12 documents at latitude 1..12 degrees, longitude 0.5*i degrees, plus one document
 * without the location field (sorted last as +Infinity), split over two segments.
 */
public class TestGeo3DSortPaging extends LuceneTestCase {

  private static final String FIELD = "loc";
  private static final int N = 12;
  private static final int PAGE = 3;

  private static double lat(int i) {
    return 1.0 + i;
  }

  private static double lon(int i) {
    return 0.5 * i;
  }

  private Directory buildIndex(PlanetModel pm) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < N; i++) {
        Document d = new Document();
        d.add(new StringField("id", Integer.toString(i), Field.Store.YES));
        d.add(new Geo3DPoint(FIELD, pm, lat(i), lon(i)));
        GeoPoint p = new GeoPoint(pm, Math.toRadians(lat(i)), Math.toRadians(lon(i)));
        d.add(new Geo3DDocValuesField(FIELD, p, pm));
        w.addDocument(d);
        if (i == N / 2) {
          w.commit(); // two segments
        }
      }
      Document missing = new Document();
      missing.add(new StringField("id", "missing", Field.Store.YES));
      w.addDocument(missing);
    }
    return dir;
  }

  /** Concatenate searchAfter pages; compare doc ids to the single full search. */
  private static void assertPagingEqualsFull(IndexSearcher s, SortField sf) throws Exception {
    Sort sort = new Sort(sf);
    Query q = new MatchAllDocsQuery();
    TopDocs full = s.search(q, 1000, sort);
    // Non-vacuity: every indexed doc lies at a positive distance, so no cursor is 0.0.
    double firstValue = (Double) ((FieldDoc) full.scoreDocs[0]).fields[0];
    assertTrue(sf + " precondition: first hit distance > 0, was " + firstValue, firstValue > 0);
    List<Integer> expected = new ArrayList<>();
    for (ScoreDoc sd : full.scoreDocs) {
      expected.add(sd.doc);
    }
    List<Integer> paged = new ArrayList<>();
    List<String> afterValues = new ArrayList<>();
    FieldDoc after = null;
    for (int guard = 0; guard < 100; guard++) {
      TopDocs page = (after == null) ? s.search(q, PAGE, sort) : s.searchAfter(after, q, PAGE, sort);
      if (page.scoreDocs.length == 0) {
        break;
      }
      for (ScoreDoc sd : page.scoreDocs) {
        paged.add(sd.doc);
      }
      after = (FieldDoc) page.scoreDocs[page.scoreDocs.length - 1];
      afterValues.add(String.valueOf(after.fields[0]));
    }
    assertEquals(
        sf + " full-sort values(first)=" + firstValues(full) + " searchAfter cursors=" + afterValues,
        expected,
        paged);
  }

  private static String firstValues(TopDocs td) {
    StringBuilder b = new StringBuilder("[");
    for (int i = 0; i < Math.min(4, td.scoreDocs.length); i++) {
      if (i > 0) b.append(", ");
      b.append(((FieldDoc) td.scoreDocs[i]).fields[0]);
    }
    return b.append(", ...]").toString();
  }

  private void runPaging(PlanetModel pm, SortField sf) throws Exception {
    try (Directory dir = buildIndex(pm);
        DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals(2, r.leaves().size());
      assertPagingEqualsFull(new IndexSearcher(r), sf);
    }
  }

  // ---- WGS84 (the default Earth model): expected to fail on stock code if the claim is right ----

  public void testDistanceSortWgs84() throws Exception {
    runPaging(
        PlanetModel.WGS84,
        Geo3DDocValuesField.newDistanceSort(FIELD, 0.0, 0.0, 5_000_000.0, PlanetModel.WGS84));
  }

  public void testPathSortWgs84() throws Exception {
    runPaging(
        PlanetModel.WGS84,
        Geo3DDocValuesField.newPathSort(
            FIELD, new double[] {0.0, 20.0}, new double[] {-1.0, -1.0}, 1_000_000.0, PlanetModel.WGS84));
  }

  public void testOutsideDistanceSortWgs84() throws Exception {
    runPaging(
        PlanetModel.WGS84,
        Geo3DDocValuesField.newOutsideDistanceSort(FIELD, 0.0, 0.0, 10_000.0, PlanetModel.WGS84));
  }

  public void testOutsideBoxSortWgs84() throws Exception {
    runPaging(
        PlanetModel.WGS84,
        Geo3DDocValuesField.newOutsideBoxSort(FIELD, -0.5, 0.5, -0.5, 0.5, PlanetModel.WGS84));
  }

  public void testOutsidePolygonSortWgs84() throws Exception {
    // Square of +/-0.5 degrees around (0,0), listed (lon,lat) west edge north, east, then south
    // (geometrically clockwise as the Polygon javadoc asks). Geo3D is orientation-sensitive: the
    // opposite order is read as the complement (every doc inside, all values 0.0), which would make
    // this test vacuous; the non-zero precondition in assertPagingEqualsFull guards against that.
    Polygon square =
        new Polygon(
            new double[] {-0.5, 0.5, 0.5, -0.5, -0.5}, new double[] {-0.5, -0.5, 0.5, 0.5, -0.5});
    runPaging(
        PlanetModel.WGS84, Geo3DDocValuesField.newOutsidePolygonSort(FIELD, PlanetModel.WGS84, square));
  }

  // ---- Control: SPHERE has mean radius 1.0, so "meters" == planet units; must pass ----

  public void testDistanceSortSphereControl() throws Exception {
    runPaging(
        PlanetModel.SPHERE,
        Geo3DDocValuesField.newDistanceSort(FIELD, 0.0, 0.0, 1.0, PlanetModel.SPHERE));
  }

  /**
   * Sanity: the FieldDoc value really is in meters on WGS84 (as the factory javadoc says), i.e. the
   * value() side honours the contract. Doc 0 is at (1N, 0E): about 111 km from (0,0).
   */
  public void testFieldDocValueIsMetersWgs84() throws Exception {
    try (Directory dir = buildIndex(PlanetModel.WGS84);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      TopDocs td =
          s.search(
              new MatchAllDocsQuery(),
              1,
              new Sort(
                  Geo3DDocValuesField.newDistanceSort(
                      FIELD, 0.0, 0.0, 5_000_000.0, PlanetModel.WGS84)));
      double v = (Double) ((FieldDoc) td.scoreDocs[0]).fields[0];
      assertTrue("value should be ~111 km in meters, was " + v, v > 100_000 && v < 120_000);
    }
  }
}
