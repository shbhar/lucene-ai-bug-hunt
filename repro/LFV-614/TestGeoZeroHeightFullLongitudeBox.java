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
 * A box whose top and bottom latitude are equal and which spans all longitudes,
 * GeoBBoxFactory.makeGeoBBox(pm, L, L, -PI, PI), is the line of latitude L. Each test has a
 * control: the same line over a slightly narrower longitude range, which GeoBBoxFactory builds as a
 * different class and which gives the right answer.
 */
public class TestGeoZeroHeightFullLongitudeBox extends LuceneTestCase {

  private static GeoBBox line(PlanetModel pm, double lat, double leftLon, double rightLon) {
    return GeoBBoxFactory.makeGeoBBox(pm, lat, lat, leftLon, rightLon);
  }

  /** WGS84: the line of latitude 0.5 rad must contain the points on latitude 0.5 rad. */
  public void testWgs84LineContainsPointsOnItsLatitude() {
    PlanetModel pm = PlanetModel.WGS84;
    double lat = 0.5; // radians, about 28.65 degrees
    GeoBBox full = line(pm, lat, -Math.PI, Math.PI);
    GeoBBox narrower = line(pm, lat, -3.0, 3.0);
    GeoPoint onLatitude = new GeoPoint(pm, lat, 1.0);

    assertTrue("control: " + narrower + " contains " + onLatitude, narrower.isWithin(onLatitude));
    assertTrue(
        full + " must contain its own center " + full.getCenter(), full.isWithin(full.getCenter()));
    assertTrue(full + " must contain " + onLatitude, full.isWithin(onLatitude));
  }

  /**
   * Default model (SPHERE): a line can never CONTAIN a rectangle that is 1.4 rad tall. The
   * rectangle's top edge lies 5e-11 rad (0.3 mm on the Earth) below the line.
   */
  public void testSphereLineDoesNotContainRectangleJustBelowIt() {
    PlanetModel pm = PlanetModel.SPHERE;
    double lat = 0.4;
    GeoArea rectangle = GeoAreaFactory.makeGeoArea(pm, lat - 5e-11, -1.0, -1.0, 1.0);
    GeoBBox full = line(pm, lat, -Math.PI, Math.PI);
    GeoBBox narrower = line(pm, lat, -Math.PI + 0.01, Math.PI - 0.01);

    GeoPoint deepInside = new GeoPoint(pm, -0.5, 0.0);
    assertTrue(rectangle.isWithin(deepInside));
    assertFalse(full.isWithin(deepInside));

    assertEquals("control: " + narrower, GeoArea.DISJOINT, rectangle.getRelationship(narrower));
    assertTrue(
        rectangle + ".getRelationship(" + full + ") must not be CONTAINS (0)",
        rectangle.getRelationship(full) != GeoArea.CONTAINS);
  }
}
