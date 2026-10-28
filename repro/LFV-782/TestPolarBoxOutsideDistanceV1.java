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
package org.apache.lucene.spatial3d;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.spatial3d.geom.DistanceStyle;
import org.apache.lucene.spatial3d.geom.GeoBBox;
import org.apache.lucene.spatial3d.geom.GeoBBoxFactory;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Outside distance from a point to a lat/lon box that reaches a pole (maxLatitude 90 or
 * minLatitude -90, longitude extent below 180 degrees).
 *
 * <p>The box contains the pole, so the outside distance of any point can never exceed its distance
 * to that pole. On the sphere the exact distance to the box is the minimum distance to its boundary
 * (bottom/top parallel and the two meridian edges, which end at the pole); the reference below
 * samples that boundary densely with plain spherical trigonometry, independently of the geo3d
 * classes.
 */
public class TestPolarBoxOutsideDistanceV1 extends LuceneTestCase {

  private static double rad(double deg) {
    return Math.toRadians(deg);
  }

  /** Great-circle angle between two lat/lon points (radians), independent of geo3d. */
  private static double angle(double lat1, double lon1, double lat2, double lon2) {
    double x1 = Math.cos(lat1) * Math.cos(lon1), y1 = Math.cos(lat1) * Math.sin(lon1);
    double z1 = Math.sin(lat1);
    double x2 = Math.cos(lat2) * Math.cos(lon2), y2 = Math.cos(lat2) * Math.sin(lon2);
    double z2 = Math.sin(lat2);
    double cx = y1 * z2 - z1 * y2, cy = z1 * x2 - x1 * z2, cz = x1 * y2 - y1 * x2;
    return Math.atan2(Math.sqrt(cx * cx + cy * cy + cz * cz), x1 * x2 + y1 * y2 + z1 * z2);
  }

  /**
   * Reference: minimum angle from (lat, lon) to the boundary of the box [latLo, latHi] x [lonLo,
   * lonHi] (lonLo < lonHi, no dateline crossing), sampled with step {@code step} radians.
   */
  private static double refBoundaryDistance(
      double lat, double lon, double latLo, double latHi, double lonLo, double lonHi, int n) {
    double best = Double.POSITIVE_INFINITY;
    for (int i = 0; i <= n; i++) {
      double t = (double) i / n;
      double la = latLo + t * (latHi - latLo);
      double lo = lonLo + t * (lonHi - lonLo);
      best = Math.min(best, angle(lat, lon, la, lonLo)); // left meridian edge
      best = Math.min(best, angle(lat, lon, la, lonHi)); // right meridian edge
      best = Math.min(best, angle(lat, lon, latLo, lo)); // bottom parallel
      best = Math.min(best, angle(lat, lon, latHi, lo)); // top parallel (a point if at a pole)
    }
    return best;
  }

  private static boolean inside(
      double lat, double lon, double latLo, double latHi, double lonLo, double lonHi) {
    return lat >= latLo && lat <= latHi && lon >= lonLo && lon <= lonHi;
  }

  private static void checkBox(
      double latLoDeg, double latHiDeg, double lonLoDeg, double lonHiDeg, String expectedClass) {
    double latLo = rad(latLoDeg), latHi = rad(latHiDeg), lonLo = rad(lonLoDeg), lonHi = rad(lonHiDeg);
    GeoBBox box = GeoBBoxFactory.makeGeoBBox(PlanetModel.SPHERE, latHi, latLo, lonLo, lonHi);
    assertEquals(expectedClass, box.getClass().getSimpleName());
    // Pre-condition for polar boxes: the box contains its pole (then the outside distance can never
    // exceed the distance to that pole).
    boolean polar = latHiDeg == 90 || latLoDeg == -90;
    double poleLat = latHiDeg == 90 ? Math.PI / 2 : -Math.PI / 2;
    if (polar) {
      GeoPoint pole = latHiDeg == 90 ? PlanetModel.SPHERE.NORTH_POLE : PlanetModel.SPHERE.SOUTH_POLE;
      assertTrue("box must contain its pole", box.isWithin(pole));
    }

    int checked = 0, bad = 0;
    double maxErr = 0;
    String worst = null;
    // Grid of query points every 7 degrees latitude / 11 degrees longitude (offset to avoid box edges)
    for (double latDeg = -86.5; latDeg <= 86.5; latDeg += 7) {
      for (double lonDeg = -177.5; lonDeg < 180; lonDeg += 11) {
        double lat = rad(latDeg), lon = rad(lonDeg);
        if (inside(lat, lon, latLo, latHi, lonLo, lonHi)) {
          continue;
        }
        GeoPoint p = new GeoPoint(PlanetModel.SPHERE, lat, lon);
        double lucene = box.computeOutsideDistance(DistanceStyle.ARC, p);
        double ref = refBoundaryDistance(lat, lon, latLo, latHi, lonLo, lonHi, 4000);
        double toPole = polar ? angle(lat, lon, poleLat, 0) : Double.POSITIVE_INFINITY;
        checked++;
        double err = Math.abs(lucene - ref);
        if (err > 1e-3 || lucene > toPole + 1e-9) {
          bad++;
          if (err > maxErr) {
            maxErr = err;
            worst =
                String.format(
                    java.util.Locale.ROOT,
                    "point (%.1f, %.1f): lucene=%.4f deg, reference=%.4f deg, distance to pole=%.4f deg",
                    latDeg,
                    lonDeg,
                    Math.toDegrees(lucene),
                    Math.toDegrees(ref),
                    Math.toDegrees(toPole));
          }
        }
      }
    }
    assertTrue("vacuous sweep", checked > 300);
    assertEquals(
        box + ": " + bad + " of " + checked + " outside points wrong; worst " + worst, 0, bad);
  }

  /** North box 45..90 x -20..30 on the sphere; e.g. (75, 170) is 15 degrees from the box's pole. */
  public void testNorthPolarBoxSphere() {
    GeoBBox box =
        GeoBBoxFactory.makeGeoBBox(PlanetModel.SPHERE, Math.PI / 2, rad(45), rad(-20), rad(30));
    GeoPoint p = new GeoPoint(PlanetModel.SPHERE, rad(75), rad(170));
    double d = Math.toDegrees(box.computeOutsideDistance(DistanceStyle.ARC, p));
    assertEquals(box + " outside distance of (75, 170) in degrees", 15.0, d, 1e-6);
  }

  /** South box -90..-40 x 100..160 on the sphere; (-70, -60) is 20 degrees from the south pole. */
  public void testSouthPolarBoxSphere() {
    GeoBBox box =
        GeoBBoxFactory.makeGeoBBox(PlanetModel.SPHERE, rad(-40), -Math.PI / 2, rad(100), rad(160));
    GeoPoint p = new GeoPoint(PlanetModel.SPHERE, rad(-70), rad(-60));
    double d = Math.toDegrees(box.computeOutsideDistance(DistanceStyle.ARC, p));
    assertEquals(box + " outside distance of (-70, -60) in degrees", 20.0, d, 1e-6);
  }

  /** Grid sweep against the sampled-boundary reference: north polar box. */
  public void testNorthPolarBoxSweep() {
    checkBox(45, 90, -20, 30, "GeoNorthRectangle");
  }

  /** Grid sweep against the sampled-boundary reference: south polar box. */
  public void testSouthPolarBoxSweep() {
    checkBox(-90, -40, 100, 160, "GeoSouthRectangle");
  }

  /** Control: the same sweep with maxLatitude 89 (a plain GeoRectangle) is expected to pass. */
  public void testControlNonPolarBoxSweep() {
    checkBox(45, 89, -20, 30, "GeoRectangle");
  }

  /** Returns "name=sortValue" for the hits in sort order. */
  private static String[] sortedByOutsideDistance(Directory dir, double maxLat) throws Exception {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      Sort sort =
          new Sort(
              Geo3DDocValuesField.newOutsideBoxSort("p", 45, maxLat, -20, 30, PlanetModel.WGS84));
      TopFieldDocs td = s.search(new MatchAllDocsQuery(), 10, sort);
      String[] out = new String[td.scoreDocs.length];
      for (int i = 0; i < out.length; i++) {
        out[i] =
            s.storedFields().document(td.scoreDocs[i].doc).get("name")
                + "="
                + ((FieldDoc) td.scoreDocs[i]).fields[0];
      }
      return out;
    }
  }

  private static String name(String hit) {
    return hit.substring(0, hit.indexOf('='));
  }

  private static double value(String hit) {
    return Double.parseDouble(hit.substring(hit.indexOf('=') + 1));
  }

  private static Directory buildIndex() throws Exception {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      // name, lat, lon
      Object[][] docs = {
        {"inside", 60.0, 0.0}, // inside the box: 0.0
        {"farside", 75.0, 170.0}, // about 15 degrees (~1,670 km) from the box (via the pole)
        {"south", 20.0, 5.0}, // about 25 degrees (~2,780 km) south of the box
      };
      for (Object[] d : docs) {
        Document doc = new Document();
        doc.add(new StoredField("name", (String) d[0]));
        GeoPoint gp = new GeoPoint(PlanetModel.WGS84, rad((Double) d[1]), rad((Double) d[2]));
        doc.add(new Geo3DDocValuesField("p", gp, PlanetModel.WGS84));
        w.addDocument(doc);
      }
    }
    return dir;
  }

  /** WGS84 end to end: newOutsideBoxSort with maxLatitude 90. */
  public void testOutsideBoxSortWgs84PolarBox() throws Exception {
    try (Directory dir = buildIndex()) {
      String[] hits = sortedByOutsideDistance(dir, 90);
      String order = String.join(" ", hits);
      assertEquals(3, hits.length);
      assertEquals("order: " + order, "inside", name(hits[0]));
      assertEquals("order: " + order, "farside", name(hits[1]));
      assertEquals("order: " + order, "south", name(hits[2]));
      assertTrue("farside outside distance (m) should be about 1.67e6: " + order, value(hits[1]) < 2.0e6);
    }
  }

  /** Control: the same index sorted with maxLatitude 89.9 (a GeoRectangle) gives the right order. */
  public void testControlOutsideBoxSortWgs84NonPolarBox() throws Exception {
    try (Directory dir = buildIndex()) {
      String[] hits = sortedByOutsideDistance(dir, 89.9);
      String order = String.join(" ", hits);
      System.out.println("control maxLat=89.9: " + order);
      assertEquals(3, hits.length);
      assertEquals("order: " + order, "inside", name(hits[0]));
      assertEquals("order: " + order, "farside", name(hits[1]));
      assertEquals("order: " + order, "south", name(hits[2]));
    }
  }
}
