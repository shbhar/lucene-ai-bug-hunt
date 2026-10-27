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
 * Vector.normalDistance: "the normal (perpendicular) distance to a vector described by a vector taken
 * from the origin". On WGS84 surface points are not unit vectors, and the computed value for a point
 * and itself is not 0.
 */
public class TestNormalDistanceOnEllipsoid extends LuceneTestCase {

  /** Perpendicular distance from q to the line through the origin and p. */
  private static double perpendicular(Vector p, Vector q) {
    double t = p.dotProduct(q) / p.dotProduct(p);
    double dx = q.x - t * p.x, dy = q.y - t * p.y, dz = q.z - t * p.z;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  public void testSphereControl() {
    GeoPoint p = new GeoPoint(PlanetModel.SPHERE, 0.3, 0.2);
    assertEquals(0.0, p.normalDistance(p), 1e-12);
    assertEquals(0.0, DistanceStyle.NORMAL.computeDistance(p, p), 1e-12);
  }

  public void testNormalDistanceOfAPointToItselfOnWgs84() {
    GeoPoint p = new GeoPoint(PlanetModel.WGS84, 0.3, 0.2);
    assertEquals("perpendicular distance from p to the line through p", 0.0, perpendicular(p, p), 1e-12);
    assertEquals("Vector.normalDistance(p, p)", 0.0, p.normalDistance(p), 1e-9);
  }

  public void testCircleNormalDistanceOnWgs84() {
    // a 100 m circle (in planet units of the mean radius); the centre and a point 50 m away
    GeoCircle circle = GeoCircleFactory.makeGeoCircle(PlanetModel.WGS84, 0.3, 0.2, 100 / 6371008.7714);
    GeoPoint centre = circle.getCenter();
    GeoPoint q = new GeoPoint(PlanetModel.WGS84, 0.3 + 50 / 6371008.7714, 0.2);
    assertTrue(circle.isWithin(q));
    assertEquals(
        "NORMAL distance of a point 50 m from the centre (expected about 7.8e-6 planet units)",
        perpendicular(centre, q),
        circle.computeDistance(DistanceStyle.NORMAL, q),
        1e-9);
    assertEquals(
        "NORMAL distance of the centre itself", 0.0, circle.computeDistance(DistanceStyle.NORMAL, centre), 1e-9);
  }
}
