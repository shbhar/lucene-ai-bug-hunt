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
 * GeoBBox.expand(angle) moves every bound out by the angle (as TestGeoBBox.testBBoxExpand checks
 * for a box away from the dateline). A longitude bound that is moved past +-PI must wrap across the
 * dateline, as it already does for a box that crosses the dateline.
 */
public class TestGeoBBoxExpandAcrossDateline extends LuceneTestCase {

  /** Control (passes): away from the dateline, [0.5, 1.0] expanded by 0.3 reaches 0.2 and 1.3. */
  public void testControlAwayFromDateline() {
    GeoBBox box = GeoBBoxFactory.makeGeoBBox(PlanetModel.SPHERE, 0.0, -0.5, 0.5, 1.0).expand(0.3);
    assertTrue(box.isWithin(new GeoPoint(PlanetModel.SPHERE, -0.1, 1.2)));
    assertTrue(box.isWithin(new GeoPoint(PlanetModel.SPHERE, -0.1, 0.3)));
    assertFalse(box.isWithin(new GeoPoint(PlanetModel.SPHERE, -0.1, 1.4)));
  }

  /** East edge: [2.5, 3.0] expanded by 0.3 must reach 3.3 - 2 PI = -2.983 rad. */
  public void testRightBoundWrapsAcrossDateline() {
    GeoBBox box = GeoBBoxFactory.makeGeoBBox(PlanetModel.SPHERE, 0.0, -0.5, 2.5, 3.0).expand(0.3);
    // 0.2 rad east of the old east edge: lon 3.2 - 2 PI = -3.083 rad
    GeoPoint east = new GeoPoint(PlanetModel.SPHERE, -0.1, 3.2 - 2 * Math.PI);
    assertTrue(box + " must contain " + east, box.isWithin(east));
  }

  /** West edge, another class and planet model: a GeoWideRectangle [-3.0, 1.0] on WGS84. */
  public void testLeftBoundWrapsAcrossDateline() {
    GeoBBox box = GeoBBoxFactory.makeGeoBBox(PlanetModel.WGS84, 0.5, -0.5, -3.0, 1.0).expand(0.3);
    // 0.2 rad west of the old west edge: lon -3.2 + 2 PI = 3.083 rad
    GeoPoint west = new GeoPoint(PlanetModel.WGS84, 0.0, -3.2 + 2 * Math.PI);
    assertTrue(box + " must contain " + west, box.isWithin(west));
  }

  /** Control (passes): a box whose east edge already crosses the dateline is widened across it. */
  public void testControlCrossingBoxIsWidened() {
    GeoBBox box =
        GeoBBoxFactory.makeGeoBBox(PlanetModel.SPHERE, 0.0, -0.5, 2.5, -Math.PI + 0.001)
            .expand(0.3);
    GeoPoint p = new GeoPoint(PlanetModel.SPHERE, -0.1, -Math.PI + 0.2);
    assertTrue(box + " must contain " + p, box.isWithin(p));
  }

  /** The same box 0.002 rad narrower, east edge just short of the dateline: not widened. */
  public void testAdjacentBoxIsWidened() {
    GeoBBox box =
        GeoBBoxFactory.makeGeoBBox(PlanetModel.SPHERE, 0.0, -0.5, 2.5, Math.PI - 0.001).expand(0.3);
    GeoPoint p = new GeoPoint(PlanetModel.SPHERE, -0.1, -Math.PI + 0.2);
    assertTrue(box + " must contain " + p, box.isWithin(p));
  }
}
