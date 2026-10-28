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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * GeoOutsideDistance: "the distance from a point to the closest outside edge". For a point outside
 * an exact circle, that distance can be no larger than the distance to any point on the shape's own
 * edge. The edge point used here is found from the shape's own isWithin, by bisection along the ray
 * from the centre through the query point. Only public API is used (GeoCircleFactory, PlanetModel,
 * GeoPoint, DistanceStyle).
 */
public class TestExactCircleOutsideDistanceJointsV1 extends LuceneTestCase {

  /** Edge point of the shape on the given bearing from the centre: last inside point of a bisection. */
  private static GeoPoint edgePointOnBearing(
      PlanetModel pm, GeoCircle shape, double bearing, double outsideDistanceFromCentre) {
    final GeoPoint centre = shape.getCenter();
    assertTrue(shape.isWithin(centre));
    double inside = 0.0;
    double outside = outsideDistanceFromCentre;
    assertFalse(shape.isWithin(pm.surfacePointOnBearing(centre, outside, bearing)));
    for (int i = 0; i < 100; i++) {
      final double mid = 0.5 * (inside + outside);
      if (shape.isWithin(pm.surfacePointOnBearing(centre, mid, bearing))) {
        inside = mid;
      } else {
        outside = mid;
      }
    }
    return pm.surfacePointOnBearing(centre, inside, bearing);
  }

  /**
   * Checks computeOutsideDistance for the point at surface distance radius + offset on the bearing,
   * and returns a description of the problem, or null if the result is plausible.
   */
  private static String check(
      PlanetModel pm,
      GeoCircle circle,
      double radius,
      double bearing,
      double offset,
      DistanceStyle style,
      String styleName) {
    final GeoPoint p = pm.surfacePointOnBearing(circle.getCenter(), radius + offset, bearing);
    if (circle.isWithin(p)) {
      return "query point unexpectedly inside (test setup error)";
    }
    final GeoPoint edge = edgePointOnBearing(pm, circle, bearing, radius + offset);
    final double upperBound = style.computeDistance(edge, p.x, p.y, p.z);
    final double got = circle.computeOutsideDistance(style, p);
    // Slack: on an ellipsoid the plane-based distances are approximations (observed up to ~6e-6
    // above this bound at non-joint bearings, see the control test); the defect tested here is
    // +Infinity or the distance to the far side of the circle, orders of magnitude larger.
    if (Double.isFinite(got) && got <= upperBound * (1 + 1e-3) + 1e-6) {
      return null;
    }
    return String.format(
        java.util.Locale.ROOT,
        "[%s bearing=%.4f offset=%.4g: outside distance=%s, but a point on the circle's own edge is at %.9f]",
        styleName,
        bearing,
        offset,
        got,
        upperBound);
  }

  /** WGS84, exact circle of radius 0.1 (about 637 km): points due north and due south. */
  public void testWgs84PointsOnTheCentreMeridian() {
    final PlanetModel pm = PlanetModel.WGS84;
    final double radius = 0.1;
    final GeoCircle circle = GeoCircleFactory.makeExactGeoCircle(pm, 0.3, 0.5, radius, 1e-6);
    final List<String> problems = new ArrayList<>();
    for (double bearing : new double[] {0.0, Math.PI}) {
      for (double offset : new double[] {0.005, 0.02, 0.1}) {
        String a = check(pm, circle, radius, bearing, offset, DistanceStyle.ARC, "ARC");
        if (a != null) problems.add(a);
        String b = check(pm, circle, radius, bearing, offset, DistanceStyle.LINEAR, "LINEAR");
        if (b != null) problems.add(b);
      }
    }
    assertTrue(circle + " " + problems, problems.isEmpty());
  }

  /** Sphere, exact circle of radius 1e-4 (accuracy 1e-6): the outside distance is exactly the offset. */
  public void testSphereSmallCircleCardinalPoints() {
    final PlanetModel pm = PlanetModel.SPHERE;
    final double radius = 1e-4;
    // {lat, lon, bearing}
    final double[][] cases = {
      {0.0, 0.5, 1.5 * Math.PI}, // due west
      {0.9, 0.5, 0.0}, // due north
      {-1.0, 0.5, 0.5 * Math.PI}, // due east
    };
    final List<String> problems = new ArrayList<>();
    for (double[] c : cases) {
      final GeoCircle exact = GeoCircleFactory.makeExactGeoCircle(pm, c[0], c[1], radius, 1e-6);
      final GeoCircle standard = GeoCircleFactory.makeGeoCircle(pm, c[0], c[1], radius);
      for (double offset : new double[] {1e-3, 1e-2}) {
        final GeoPoint p = pm.surfacePointOnBearing(exact.getCenter(), radius + offset, c[2]);
        final double got = exact.computeOutsideDistance(DistanceStyle.ARC, p);
        final double std = standard.computeOutsideDistance(DistanceStyle.ARC, p);
        if (!(Math.abs(got - offset) <= 1e-6)) {
          problems.add(
              String.format(
                  java.util.Locale.ROOT,
                  "[lat=%s lon=%s bearing=%.4f offset=%s: exact circle outside distance=%.12f,"
                      + " standard circle=%.12f]",
                  c[0],
                  c[1],
                  c[2],
                  offset,
                  got,
                  std));
        }
      }
    }
    assertTrue(problems.toString(), problems.isEmpty());
  }

  /** Control: the same checks at bearings that are not slice joints pass (oracle is not too strict). */
  public void testControlOtherBearings() {
    final List<String> problems = new ArrayList<>();
    final GeoCircle wgs = GeoCircleFactory.makeExactGeoCircle(PlanetModel.WGS84, 0.3, 0.5, 0.1, 1e-6);
    for (double bearing : new double[] {0.37, 2.1, 4.4}) {
      for (double offset : new double[] {0.005, 0.02, 0.1}) {
        String a = check(PlanetModel.WGS84, wgs, 0.1, bearing, offset, DistanceStyle.ARC, "ARC");
        if (a != null) problems.add(a);
      }
    }
    final GeoCircle sph =
        GeoCircleFactory.makeExactGeoCircle(PlanetModel.SPHERE, 0.0, 0.5, 1e-4, 1e-6);
    for (double bearing : new double[] {0.37, 2.1, 4.4}) {
      final GeoPoint p = PlanetModel.SPHERE.surfacePointOnBearing(sph.getCenter(), 1e-4 + 1e-3, bearing);
      final double got = sph.computeOutsideDistance(DistanceStyle.ARC, p);
      if (!(Math.abs(got - 1e-3) <= 1e-6)) problems.add("[sphere bearing=" + bearing + " got " + got + "]");
    }
    assertTrue(problems.toString(), problems.isEmpty());
  }
}
