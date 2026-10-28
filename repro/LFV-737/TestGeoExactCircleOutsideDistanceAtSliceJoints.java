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

import java.util.Locale;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * GeoOutsideDistance: "the distance from a point to the closest outside edge". GeoExactCircle
 * approximates the circle with slices; for a point whose closest edge point is where two slices
 * meet, the outside distance is +Infinity or the distance to the far side of the circle. Only the
 * public API is used (GeoCircleFactory.makeExactGeoCircle, GeoCircle.computeOutsideDistance). The
 * last test is a control: the same circles away from the slice joints give the right values.
 */
public class TestGeoExactCircleOutsideDistanceAtSliceJoints extends LuceneTestCase {

  /** Angle between two points (the ARC distance style). */
  private static double arc(GeoPoint a, GeoPoint b) {
    return a.arcDistance(b);
  }

  /**
   * Upper bound from the shape's own membership: the arc distance from q to the last point of the
   * straight latitude/longitude path from the centre to q that is inside the circle.
   */
  private static double edgePointDistance(
      GeoCircle circle, PlanetModel pm, double lat0, double lon0, GeoPoint q) {
    double dLat = q.getLatitude() - lat0, dLon = q.getLongitude() - lon0;
    double lo = 0, hi = 1;
    for (int i = 0; i < 60; i++) {
      double mid = 0.5 * (lo + hi);
      if (circle.isWithin(new GeoPoint(pm, lat0 + mid * dLat, lon0 + mid * dLon))) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    return arc(new GeoPoint(pm, lat0 + lo * dLat, lon0 + lo * dLon), q);
  }

  /** Checks the WGS84 exact circle at (0.3, 0.2), radius 0.1, for points at the given longitude. */
  private static String checkWgs84(double lon) {
    PlanetModel pm = PlanetModel.WGS84;
    // 0.1 planet units = about 637 km; accuracy 1e-6 (about 6 m)
    GeoCircle circle = GeoCircleFactory.makeExactGeoCircle(pm, 0.3, 0.2, 0.1, 1e-6);
    StringBuilder wrong = new StringBuilder();
    for (double dLat : new double[] {0.11, 0.15, 0.2, -0.11, -0.15, -0.2}) {
      GeoPoint q = new GeoPoint(pm, 0.3 + dLat, lon);
      assertFalse(circle.isWithin(q));
      double upperBound = edgePointDistance(circle, pm, 0.3, 0.2, q);
      double outside = circle.computeOutsideDistance(DistanceStyle.ARC, q);
      if (!(outside <= upperBound + 1e-6)) {
        wrong.append(
            String.format(
                Locale.ROOT,
                " [lat offset %+.2f: outside=%s, edge point at %.6f]",
                dLat,
                outside,
                upperBound));
      }
    }
    return wrong.toString();
  }

  /** WGS84: points due north and due south of the centre (on the slice joints). */
  public void testWgs84PointsDueNorthAndSouthOfTheCentre() {
    String wrong = checkWgs84(0.2);
    assertTrue(
        "outside distance larger than the distance to an edge point:" + wrong, wrong.isEmpty());
  }

  /** Sphere: points due west of a small exact circle (a slice joint, through rounding). */
  public void testSphereSmallCircleSliceJoint() {
    PlanetModel pm = PlanetModel.SPHERE;
    double r = 1e-4; // about 637 m on Earth
    GeoCircle circle = GeoCircleFactory.makeExactGeoCircle(pm, 0.0, 0.2, r, 1e-6);
    // points due west of the centre, d outside the circle, on the equator
    for (double d : new double[] {1e-3, 1e-2, 1e-1}) {
      GeoPoint q = new GeoPoint(pm, 0.0, 0.2 - r - d);
      assertEquals(
          "outside distance of a point " + d + " rad west of the circle edge",
          d,
          circle.computeOutsideDistance(DistanceStyle.ARC, q),
          1e-6);
    }
  }

  /**
   * Control: the same two circles away from the slice joints. WGS84: the same latitudes, 0.0005 rad
   * (about 3 km) east of the centre's meridian. Sphere: points at bearings that are not joints.
   */
  public void testControlAwayFromTheJoints() {
    String wrong = checkWgs84(0.2005);
    assertTrue(
        "outside distance larger than the distance to an edge point:" + wrong, wrong.isEmpty());

    PlanetModel pm = PlanetModel.SPHERE;
    double r = 1e-4;
    GeoCircle circle = GeoCircleFactory.makeExactGeoCircle(pm, 0.0, 0.2, r, 1e-6);
    for (double bearing : new double[] {0.37, 2.1, 4.4}) {
      for (double d : new double[] {1e-3, 1e-2, 1e-1}) {
        GeoPoint q = pm.surfacePointOnBearing(circle.getCenter(), r + d, bearing);
        assertEquals(
            "outside distance of a point " + d + " rad outside, bearing " + bearing,
            d,
            circle.computeOutsideDistance(DistanceStyle.ARC, q),
            1e-6);
      }
    }
  }
}
