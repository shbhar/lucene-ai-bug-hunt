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
package lfvverify;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial3d.Geo3DDocValuesField;
import org.apache.lucene.spatial3d.geom.GeoCircle;
import org.apache.lucene.spatial3d.geom.GeoCircleFactory;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.spatial3d.geom.XYZBounds;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: documents whose Geo3D distance sort key is +Infinity (outside the maximum
 * radius / path width) must still be ordered among themselves by the next SortField, so that
 * search(q, n, sort) returns the first n hits of search(q, all, sort).
 *
 * <p>Public API only (package lfvverify, outside org.apache.lucene). One segment, plain
 * IndexWriterConfig, plain IndexSearcher, every document single-valued.
 */
public class TestGeo3DSortTieAtInfinityV1 extends LuceneTestCase {

  private static final String GEO = "geo";
  private static final String TIE = "tie";

  /** lat/lon in degrees; null lat means the document has no geo field. */
  private record Doc(Double lat, Double lon, int tie) {}

  private static Doc at(double lat, double lon, int tie) {
    return new Doc(lat, lon, tie);
  }

  private static Doc missing(int tie) {
    return new Doc(null, null, tie);
  }

  /** Returns docid order and sort values as "docid=value" strings. */
  private static List<String> hits(TopDocs td) {
    List<String> out = new ArrayList<>();
    for (ScoreDoc sd : td.scoreDocs) {
      FieldDoc fd = (FieldDoc) sd;
      out.add(sd.doc + "=" + fd.fields[0] + "/" + fd.fields[1]);
    }
    return out;
  }

  /**
   * Indexes docs in order (one segment), runs the full sort (n larger than the hit count, so the
   * queue never fills) and the top-n sort, and asserts the top-n is the prefix of the full order.
   * Returns the full order for further checks.
   */
  private List<String> checkPrefix(PlanetModel pm, SortField geoSort, int n, Doc... docs)
      throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (Doc d : docs) {
          Document doc = new Document();
          if (d.lat() != null) {
            doc.add(
                new Geo3DDocValuesField(
                    GEO,
                    new GeoPoint(pm, Math.toRadians(d.lat()), Math.toRadians(d.lon())),
                    pm));
          }
          doc.add(new NumericDocValuesField(TIE, d.tie()));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals("one segment", 1, r.leaves().size());
        assertEquals(docs.length, r.maxDoc());
        IndexSearcher s = new IndexSearcher(r);
        Sort sort = new Sort(geoSort, new SortField(TIE, SortField.Type.INT));
        List<String> full = hits(s.search(new MatchAllDocsQuery(), docs.length + 10, sort));
        List<String> top = hits(s.search(new MatchAllDocsQuery(), n, sort));
        assertEquals(
            "top " + n + " must be the first " + n + " hits of the full order " + full,
            full.subList(0, n),
            top);
        return full;
      }
    }
  }

  /** About 0.9 degrees of arc, in the planet model's own distance unit (meters on WGS84). */
  private static double radius(PlanetModel pm) {
    return 0.0157 * pm.getMeanRadius();
  }

  private static SortField circle(PlanetModel pm) {
    // circle of ~100 km (on WGS84) around (0, 0)
    return Geo3DDocValuesField.newDistanceSort(GEO, 0.0, 0.0, radius(pm), pm);
  }

  /** Five documents far outside the circle; secondary key prefers later documents. */
  private void allOutside(PlanetModel pm) throws Exception {
    List<String> full =
        checkPrefix(
            pm,
            circle(pm),
            2,
            at(50, 10, 10), // doc 0
            at(50, 11, 9), // doc 1
            at(50, 12, 8), // doc 2
            at(50, 13, 7), // doc 3
            at(50, 14, 6)); // doc 4
    // sanity on the full (unpruned) order: all +Infinity, ordered by tie ascending
    assertTrue(full.toString(), full.get(0).startsWith("4=Infinity/"));
    assertTrue(full.toString(), full.get(4).startsWith("0=Infinity/"));
  }

  public void testAllOutsideRadiusWgs84() throws Exception {
    allOutside(PlanetModel.WGS84);
  }

  public void testAllOutsideRadiusSphere() throws Exception {
    allOutside(PlanetModel.SPHERE);
  }

  /** One document inside the circle (last in doc order) plus five outside: top 3. */
  public void testInsidePlusOutsideWgs84() throws Exception {
    PlanetModel pm = PlanetModel.WGS84;
    List<String> full =
        checkPrefix(
            pm,
            circle(pm),
            3,
            at(50, 10, 10), // doc 0
            at(50, 11, 9), // doc 1
            at(50, 12, 8), // doc 2
            at(50, 13, 7), // doc 3
            at(50, 14, 6), // doc 4
            at(0.1, 0.1, 0)); // doc 5, inside the circle
    assertTrue(full.toString(), full.get(0).startsWith("5="));
    assertFalse(full.toString(), full.get(0).startsWith("5=Infinity"));
  }

  /** Control: documents without the field (+Infinity too) do reach the secondary sort. */
  public void testControlAllMissingWgs84() throws Exception {
    PlanetModel pm = PlanetModel.WGS84;
    checkPrefix(pm, circle(pm), 2, missing(10), missing(9), missing(8), missing(7), missing(6));
  }

  /**
   * Mixed: out-of-radius and missing documents both have +Infinity; the missing ones are compared
   * on the secondary key, the out-of-radius ones are not.
   */
  public void testOutsideAndMissingMixedWgs84() throws Exception {
    PlanetModel pm = PlanetModel.WGS84;
    checkPrefix(
        pm,
        circle(pm),
        2,
        at(50, 10, 10), // doc 0
        at(50, 11, 9), // doc 1
        missing(8), // doc 2
        missing(7), // doc 3
        at(50, 14, 6)); // doc 4 -> expected first
  }

  /**
   * Mechanism check: two out-of-radius documents (both +Infinity) are treated differently depending
   * on whether their point lies inside the circle's xyz bounding box. Circle around (45, 0); doc 3
   * is just outside the circle near a corner of its bounding box, doc 2 is far away.
   */
  public void testBoundingBoxDecidesWhichInfinityCompetesWgs84() throws Exception {
    PlanetModel pm = PlanetModel.WGS84;
    double centerLat = 45.0;
    double radiusRad = 0.0157;
    double cornerLat = 45.8;
    double cornerLon = 1.147;
    GeoCircle c =
        GeoCircleFactory.makeGeoCircle(pm, Math.toRadians(centerLat), 0.0, radiusRad);
    XYZBounds b = new XYZBounds();
    c.getBounds(b);
    GeoPoint corner = new GeoPoint(pm, Math.toRadians(cornerLat), Math.toRadians(cornerLon));
    GeoPoint far = new GeoPoint(pm, Math.toRadians(10), Math.toRadians(12));
    assertFalse("corner point must be outside the circle", c.isWithin(corner));
    assertTrue("corner point must be inside the circle's bounds", inside(b, corner));
    assertFalse("far point must be outside the circle's bounds", inside(b, far));
    SortField sf =
        Geo3DDocValuesField.newDistanceSort(GEO, centerLat, 0.0, radiusRad * pm.getMeanRadius(), pm);
    List<String> full =
        checkPrefix(
            pm,
            sf,
            2,
            at(10, 10, 10), // doc 0, far
            at(10, 11, 9), // doc 1, far
            at(10, 12, 7), // doc 2, far, best tie
            at(cornerLat, cornerLon, 8)); // doc 3, near corner, outside the circle
    assertEquals(full.toString(), "2=Infinity/7", full.get(0));
    assertEquals(full.toString(), "3=Infinity/8", full.get(1));
  }

  private static boolean inside(XYZBounds b, GeoPoint p) {
    return p.x >= b.getMinimumX()
        && p.x <= b.getMaximumX()
        && p.y >= b.getMinimumY()
        && p.y <= b.getMaximumY()
        && p.z >= b.getMinimumZ()
        && p.z <= b.getMaximumZ();
  }

  /** Path sort uses the same comparator: documents beyond the path width are +Infinity. */
  public void testPathSortOutsideWidthWgs84() throws Exception {
    PlanetModel pm = PlanetModel.WGS84;
    SortField path =
        Geo3DDocValuesField.newPathSort(
            GEO, new double[] {0.0, 0.0}, new double[] {0.0, 1.0}, radius(pm) / 10, pm);
    checkPrefix(
        pm,
        path,
        2,
        at(50, 10, 10),
        at(50, 11, 9),
        at(50, 12, 8),
        at(50, 13, 7),
        at(50, 14, 6));
  }
}
