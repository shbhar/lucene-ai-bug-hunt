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

import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * GeoOutsideDistance: "the distance from a point to the closest outside edge". GeoExactCircle
 * approximates the circle with slices; for a point whose closest edge point is where two slices
 * meet, the outside distance is +Infinity or the distance to the far side of the circle. Only the
 * public API is used (GeoCircleFactory.makeExactGeoCircle, GeoCircle.computeOutsideDistance).
 */
public class TestGeoExactCircleOutsideDistanceAtSliceJoints extends LuceneTestCase {

  /** Angle between two points (the ARC distance style). */
  private static double arc(GeoPoint a, GeoPoint b) {
    return a.arcDistance(b);
  }

  public void testWgs84PointsDueNorthAndSouthOfTheCentre() {
    PlanetModel pm = PlanetModel.WGS84;
    // 0.1 planet units = about 637 km; accuracy 1e-6 (about 6 m)
    GeoCircle circle = GeoCircleFactory.makeExactGeoCircle(pm, 0.3, 0.2, 0.1, 1e-6);
    StringBuilder wrong = new StringBuilder();
    for (double dLat : new double[] {0.11, 0.15, 0.2, -0.11, -0.15, -0.2}) {
      GeoPoint q = new GeoPoint(pm, 0.3 + dLat, 0.2);
      assertFalse(circle.isWithin(q));
      // upper bound from the shape's own membership: the last point of the meridian from the centre
      // to q that is inside the circle
      double lo = 0, hi = dLat;
      for (int i = 0; i < 60; i++) {
        double mid = 0.5 * (lo + hi);
        if (circle.isWithin(new GeoPoint(pm, 0.3 + mid, 0.2))) {
          lo = mid;
        } else {
          hi = mid;
        }
      }
      double upperBound = arc(new GeoPoint(pm, 0.3 + lo, 0.2), q);
      double outside = circle.computeOutsideDistance(DistanceStyle.ARC, q);
      if (!(outside <= upperBound + 1e-6)) {
        wrong.append(String.format(" [lat offset %+.2f: outside=%s, edge point at %.6f]", dLat, outside, upperBound));
      }
    }
    assertTrue("outside distance larger than the distance to an edge point:" + wrong, wrong.length() == 0);
  }

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
}
