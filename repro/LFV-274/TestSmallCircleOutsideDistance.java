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
package org.apache.lucene.spatial3d.geom;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.spatial3d.Geo3DDocValuesField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.LuceneTestCase.SuppressSysoutChecks;

/**
 * Independent check of: "computeOutsideDistance of a small geo3d circle on a non-spherical planet
 * model is +Infinity for points that are clearly outside the circle".
 *
 * <p>Oracle: the outside distance of a point outside the shape must be a finite number close to the
 * distance from the point to the shape's boundary. The boundary is located independently, by
 * bisecting the shape's own {@link Membership#isWithin} along 720 rays from the centre, so the
 * oracle does not depend on the plane-intersection code under test.
 *
 * <p>Test points are generated with a local geodetic offset (meridional / prime-vertical radii of
 * the ellipsoid), independently of PlanetModel's own bearing code.
 *
 * <p>All failing assertions use public API only (GeoCircleFactory, GeoOutsideDistance,
 * DistanceStyle, Geo3DDocValuesField). The diagnostic test {@link #testMechanismDiagnostic} reads
 * the protected circlePlane field from the same package, as Lucene's own geom tests do.
 */
@SuppressSysoutChecks(bugUrl = "diagnostic output only")
@ThreadLeakScope(ThreadLeakScope.Scope.SUITE)
public class TestSmallCircleOutsideDistance extends LuceneTestCase {

  private static final double LAT_DEG = 40.0;
  private static final double LON_DEG = 10.0;

  /** Geodetic offset from (latDeg, lonDeg) by distM meters at bearing (clockwise from north). */
  private static GeoPoint offset(
      PlanetModel pm, double latDeg, double lonDeg, double distM, double bearingDeg) {
    final double a = 6378137.0;
    final double b = 6356752.314245;
    final double e2 = 1.0 - (b * b) / (a * a);
    final double phi = Math.toRadians(latDeg);
    final double s = Math.sin(phi);
    final double w = Math.sqrt(1.0 - e2 * s * s);
    final double m = a * (1.0 - e2) / (w * w * w); // meridional radius of curvature
    final double n = a / w; // prime-vertical radius of curvature
    final double dn = distM * Math.cos(Math.toRadians(bearingDeg));
    final double de = distM * Math.sin(Math.toRadians(bearingDeg));
    final double lat2 = phi + dn / m;
    final double lon2 = Math.toRadians(lonDeg) + de / (n * Math.cos(phi));
    return new GeoPoint(pm, lat2, lon2);
  }

  /**
   * Brute-force distance from p to the boundary of shape: bisect isWithin along 720 rays from the
   * centre (rays out to maxRayM meters) and take the minimum over the boundary points of
   * dist(p, boundary point) under the given distance style.
   */
  private static double bruteForceOutsideDistance(
      PlanetModel pm, Membership shape, DistanceStyle style, GeoPoint p, double maxRayM) {
    double best = Double.POSITIVE_INFINITY;
    for (int k = 0; k < 720; k++) {
      final double bearing = k * 0.5;
      double lo = 0.0;
      double hi = maxRayM;
      assertTrue(shape.isWithin(offset(pm, LAT_DEG, LON_DEG, lo, bearing)));
      assertFalse(
          "ray end must be outside, bearing " + bearing,
          shape.isWithin(offset(pm, LAT_DEG, LON_DEG, hi, bearing)));
      for (int it = 0; it < 60; it++) {
        final double mid = 0.5 * (lo + hi);
        if (shape.isWithin(offset(pm, LAT_DEG, LON_DEG, mid, bearing))) {
          lo = mid;
        } else {
          hi = mid;
        }
      }
      final GeoPoint edge = offset(pm, LAT_DEG, LON_DEG, 0.5 * (lo + hi), bearing);
      best = Math.min(best, style.computeDistance(edge, p));
    }
    return best;
  }

  /**
   * Checks computeOutsideDistance against the brute-force oracle for points pointDistM from the
   * centre at 12 bearings. Returns a failure description, or null if all are fine.
   */
  private static String check(
      PlanetModel pm, GeoCircle circle, DistanceStyle style, double pointDistM, double maxRayM) {
    StringBuilder bad = new StringBuilder();
    int badCount = 0;
    for (int bearing = 0; bearing < 360; bearing += 30) {
      final GeoPoint p = offset(pm, LAT_DEG, LON_DEG, pointDistM, bearing);
      assertFalse("test point must be outside the circle", circle.isWithin(p));
      final double actual = circle.computeOutsideDistance(style, p);
      final double oracle = bruteForceOutsideDistance(pm, circle, style, p, maxRayM);
      assertTrue(Double.isFinite(oracle) && oracle > 0);
      final double rel = Math.abs(actual - oracle) / oracle;
      if (!(Double.isFinite(actual) && rel <= 0.05)) {
        badCount++;
        bad.append(
            String.format(
                java.util.Locale.ROOT,
                "%n  bearing %3d: computeOutsideDistance=%s oracle=%.6e",
                bearing,
                actual,
                oracle));
      }
    }
    return badCount == 0 ? null : (badCount + " of 12 bearings wrong:" + bad);
  }

  private static double radians(PlanetModel pm, double meters) {
    // Same conversion as Geo3DUtil.fromDistance (radius / mean radius); for the unit sphere the
    // WGS84 mean radius is used so the circle has the same angular size.
    return meters / PlanetModel.WGS84.getMeanRadius();
  }

  /** Main claim: 1 km WGS84 standard circle, points 3 km from the centre, ARC style. */
  public void testWgs84SmallStandardCircleArc() {
    final PlanetModel pm = PlanetModel.WGS84;
    final GeoCircle c =
        GeoCircleFactory.makeGeoCircle(
            pm, Math.toRadians(LAT_DEG), Math.toRadians(LON_DEG), radians(pm, 1000));
    final String r = check(pm, c, DistanceStyle.ARC, 3000, 2000);
    assertNull("WGS84 1 km circle, ARC outside distance at 3 km: " + r, r);
  }

  /** Same with LINEAR style (Plane.linearDistance). */
  public void testWgs84SmallStandardCircleLinear() {
    final PlanetModel pm = PlanetModel.WGS84;
    final GeoCircle c =
        GeoCircleFactory.makeGeoCircle(
            pm, Math.toRadians(LAT_DEG), Math.toRadians(LON_DEG), radians(pm, 1000));
    final String r = check(pm, c, DistanceStyle.LINEAR, 3000, 2000);
    assertNull("WGS84 1 km circle, LINEAR outside distance at 3 km: " + r, r);
  }

  /** The "exact" circle the factory javadoc recommends for non-spherical planets. */
  public void testWgs84SmallExactCircleArc() {
    final PlanetModel pm = PlanetModel.WGS84;
    final GeoCircle c =
        GeoCircleFactory.makeExactGeoCircle(
            pm, Math.toRadians(LAT_DEG), Math.toRadians(LON_DEG), radians(pm, 1000), 1e-7);
    final String r = check(pm, c, DistanceStyle.ARC, 3000, 2000);
    assertNull("WGS84 1 km exact circle, ARC outside distance at 3 km: " + r, r);
  }

  /** Control: identical geometry on the unit sphere. Expected to pass. */
  public void testControlSphereSmallCircleArc() {
    final PlanetModel pm = PlanetModel.SPHERE;
    final GeoCircle c =
        GeoCircleFactory.makeGeoCircle(
            pm, Math.toRadians(LAT_DEG), Math.toRadians(LON_DEG), radians(pm, 1000));
    final String r = check(pm, c, DistanceStyle.ARC, 3000, 2000);
    assertNull("SPHERE control: " + r, r);
  }

  /** Control: a 500 km WGS84 circle, points 1500 km from the centre. Expected to pass. */
  public void testControlWgs84LargeCircleArc() {
    final PlanetModel pm = PlanetModel.WGS84;
    final GeoCircle c =
        GeoCircleFactory.makeGeoCircle(
            pm, Math.toRadians(LAT_DEG), Math.toRadians(LON_DEG), radians(pm, 500_000));
    final String r = check(pm, c, DistanceStyle.ARC, 1_500_000, 1_000_000);
    assertNull("WGS84 500 km control: " + r, r);
  }

  /**
   * End to end: Geo3DDocValuesField.newOutsideDistanceSort, default WGS84, 1 km circle, documents
   * 21 km from the centre (20 km outside). At this distance the doc-value quantisation of the points
   * (about 1e-6 of the planet radius) cannot move a document inside the circle, so this test does
   * not depend on how off-surface decoded points are handled. (No document inside the circle is
   * indexed: for a 1 km circle the cut plane is only centimetres below the surface, so the
   * membership of a decoded inside point is a separate question.)
   */
  public void testOutsideDistanceSortEndToEnd() throws Exception {
    final PlanetModel pm = PlanetModel.WGS84;
    final String field = "loc";
    try (Directory dir = newDirectory()) {
      final RandomIndexWriter w = new RandomIndexWriter(random(), dir);
      for (int bearing = 0; bearing < 360; bearing += 30) {
        Document d = new Document();
        d.add(new Geo3DDocValuesField(field, offset(pm, LAT_DEG, LON_DEG, 21000, bearing), pm));
        w.addDocument(d);
      }
      final IndexReader r = w.getReader();
      w.close();
      try {
        final IndexSearcher s = newSearcher(r);
        final SortField sf =
            Geo3DDocValuesField.newOutsideDistanceSort(field, LAT_DEG, LON_DEG, 1000, pm);
        final TopFieldDocs td = s.search(new MatchAllDocsQuery(), 12, new Sort(sf));
        assertEquals(12, td.scoreDocs.length);
        int infinite = 0;
        int outOfRange = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < td.scoreDocs.length; i++) {
          final double v = (Double) ((FieldDoc) td.scoreDocs[i]).fields[0];
          sb.append(String.format(java.util.Locale.ROOT, "%n  hit %2d: %s m", i, v));
          if (Double.isInfinite(v)) {
            infinite++;
          } else if (!(v >= 18000 && v <= 22000)) {
            outOfRange++;
          }
        }
        assertEquals(
            "documents 21 km from the centre of a 1 km circle must sort with a finite outside"
                + " distance (~20000 m), but got +Infinity (the 'missing value' sentinel); values:"
                + sb,
            0,
            infinite);
        assertEquals("finite outside distances should be ~20000 m; values:" + sb, 0, outOfRange);
      } finally {
        r.close();
      }
    }
  }

  /**
   * Diagnostic (always passes): for each bearing, the number of intersection points of the
   * circle's cut plane with the plane that Plane.arcDistance builds (origin, normal n x p), versus a
   * plane through the origin, the centre and p.
   */
  public void testMechanismDiagnostic() {
    for (PlanetModel pm : new PlanetModel[] {PlanetModel.WGS84, PlanetModel.SPHERE}) {
      final GeoStandardCircle c =
          (GeoStandardCircle)
              GeoCircleFactory.makeGeoCircle(
                  pm, Math.toRadians(LAT_DEG), Math.toRadians(LON_DEG), radians(pm, 1000));
      final Plane cp = c.circlePlane;
      StringBuilder sb = new StringBuilder();
      sb.append(pm == PlanetModel.WGS84 ? "WGS84" : "SPHERE")
          .append(" circlePlane=")
          .append(cp)
          .append('\n');
      for (int bearing = 0; bearing < 360; bearing += 30) {
        final GeoPoint p = offset(pm, LAT_DEG, LON_DEG, 3000, bearing);
        final Plane perp =
            new Plane(
                cp.y * p.z - cp.z * p.y, cp.z * p.x - cp.x * p.z, cp.x * p.y - cp.y * p.x, 0.0);
        final GeoPoint[] viaPerp = cp.findIntersections(pm, perp);
        final GeoPoint[] viaCentre = cp.findIntersections(pm, new Plane(c.getCenter(), p));
        sb.append(
            String.format(
                java.util.Locale.ROOT,
                "  bearing %3d: perp-plane intersections=%s, centre-plane intersections=%s,"
                    + " ARC outside=%s%n",
                bearing,
                viaPerp == null ? "null" : viaPerp.length,
                viaCentre == null ? "null" : viaCentre.length,
                c.computeOutsideDistance(DistanceStyle.ARC, p)));
      }
      System.out.println(sb);
    }
  }
}
