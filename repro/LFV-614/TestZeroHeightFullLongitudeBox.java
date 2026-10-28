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
 * A box of zero height that spans all longitudes, built with the public factory
 * GeoBBoxFactory.makeGeoBBox(pm, L, L, -PI, PI). Only public API is used.
 */
public class TestZeroHeightFullLongitudeBox extends LuceneTestCase {

  private static GeoBBox fullLonLine(PlanetModel pm, double lat) {
    GeoBBox box = GeoBBoxFactory.makeGeoBBox(pm, lat, lat, -Math.PI, Math.PI);
    assertEquals("GeoDegenerateLatitudeZone", box.getClass().getSimpleName());
    return box;
  }

  /** Points on latitude L must be inside the box [L, L] x [-180, 180]. */
  public void testWgs84PointsOnTheLatitudeAreInside() {
    PlanetModel pm = PlanetModel.WGS84;
    List<String> wrong = new ArrayList<>();
    for (double lat : new double[] {0.5, -0.5, 1.2, 0.1}) {
      GeoBBox line = fullLonLine(pm, lat);
      // control: same latitude, slightly narrower longitude range (a different class)
      GeoBBox narrower = GeoBBoxFactory.makeGeoBBox(pm, lat, lat, -Math.PI + 0.01, Math.PI - 0.01);
      for (double lon : new double[] {0.0, 1.0, -2.0}) {
        GeoPoint p = new GeoPoint(pm, lat, lon);
        assertTrue("control " + narrower + " " + p, narrower.isWithin(p));
        if (line.isWithin(p) == false) {
          wrong.add(line + " rejects " + p);
        }
      }
      // the box's own center point (constructed on the latitude) must be inside it
      if (line.isWithin(line.getCenter()) == false) {
        wrong.add(line + " rejects its own center " + line.getCenter());
      }
    }
    assertTrue(wrong.size() + " wrong answers:\n" + String.join("\n", wrong), wrong.isEmpty());
  }

  /**
   * A point on the WGS84 surface whose z equals sin(L), i.e. on another parallel, is not on
   * latitude L and must not be inside [L, L] x [-180, 180].
   */
  public void testWgs84PointOnAnotherParallelIsOutside() {
    PlanetModel pm = PlanetModel.WGS84;
    double lat = 0.5;
    double lon = 1.0;
    GeoBBox line = fullLonLine(pm, lat);
    double target = Math.sin(lat);
    double lo = 0.4, hi = 0.6;
    for (int i = 0; i < 200; i++) {
      double mid = 0.5 * (lo + hi);
      if (new GeoPoint(pm, mid, lon).z < target) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    GeoPoint other = new GeoPoint(pm, lo, lon);
    double offDeg = Math.toDegrees(Math.abs(lo - lat));
    assertTrue("bisection did not land off the line: " + offDeg, offDeg > 1e-3);
    assertFalse(
        line
            + " accepts "
            + other
            + " which is "
            + offDeg
            + " degrees of latitude away from the line",
        line.isWithin(other));
  }

  /**
   * GeoArea.getRelationship may return CONTAINS only if the shape contains the whole area. A
   * zero-height line cannot contain a rectangle 1.4 rad tall: a point of the rectangle far from
   * the line is not in the line. Default model (SPHERE).
   */
  public void testSphereLineDoesNotContainTallRectangle() {
    PlanetModel pm = PlanetModel.SPHERE;
    List<String> wrong = new ArrayList<>();
    // (a) line 2e-12 rad above the rectangle's top edge (makeGeoBBox treats top/bottom as equal)
    // (b) line exactly at lat 0.4, rectangle top 5e-11 rad below it
    double[][] cases = {{0.4 + 2e-12, 0.4, 0.4}, {0.4, 0.4, 0.4 - 5e-11}};
    for (double[] c : cases) {
      GeoBBox line = GeoBBoxFactory.makeGeoBBox(pm, c[0], c[1], -Math.PI, Math.PI);
      assertEquals("GeoDegenerateLatitudeZone", line.getClass().getSimpleName());
      GeoArea area = GeoAreaFactory.makeGeoArea(pm, c[2], -1.0, -1.0, 1.0);
      GeoPoint farInside = new GeoPoint(pm, -0.5, 0.0);
      assertTrue(area.isWithin(farInside));
      assertFalse(line.isWithin(farInside));
      int rel = area.getRelationship(line);
      if (rel == GeoArea.CONTAINS) {
        wrong.add(
            area
                + ".getRelationship("
                + line
                + ") = CONTAINS, but "
                + farInside
                + " is in the area and not in the line");
      }
    }
    assertTrue(wrong.size() + " wrong answers:\n" + String.join("\n", wrong), wrong.isEmpty());
  }
}
