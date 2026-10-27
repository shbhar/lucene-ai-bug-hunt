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
 * GeoBBox.expand(angle) moves every bound by the angle (TestGeoBBox.testBBoxExpand checks lat +- 0.1 and lon +- 0.1).
 * When the moved longitude bound passes +-PI it is clamped to +-PI instead of wrapping across the dateline.
 */
public class TestGeoBBoxExpandAcrossDateline extends LuceneTestCase {

  /** Control: the same box away from the dateline is widened by 0.3 on both sides. */
  public void testExpandAwayFromDateline() {
    GeoBBox box = GeoBBoxFactory.makeGeoBBox(PlanetModel.SPHERE, 0.0, -Math.PI * 0.25, 0.5, 1.0).expand(0.3);
    assertTrue(box.isWithin(new GeoPoint(PlanetModel.SPHERE, -0.1, 1.2)));
    assertTrue(box.isWithin(new GeoPoint(PlanetModel.SPHERE, -0.1, 0.3)));
    assertFalse(box.isWithin(new GeoPoint(PlanetModel.SPHERE, -0.1, 1.4)));
  }

  /** Right bound 3.0 + 0.3 = 3.3 must wrap to 3.3 - 2 PI = -2.983. */
  public void testExpandRightBoundAcrossDateline() {
    GeoBBox box = GeoBBoxFactory.makeGeoBBox(PlanetModel.SPHERE, 0.0, -Math.PI * 0.25, 2.5, 3.0);
    GeoBBox expanded = box.expand(0.3);
    // 0.2 east of the right bound, i.e. lon 3.2 - 2 PI
    GeoPoint east = new GeoPoint(PlanetModel.SPHERE, -0.1, 3.2 - 2 * Math.PI);
    assertTrue(expanded + " must contain " + east, expanded.isWithin(east));
  }

  /** Left bound -3.0 - 0.3 = -3.3 must wrap to -3.3 + 2 PI = 2.983 (any class, here a GeoWideRectangle). */
  public void testExpandLeftBoundAcrossDatelineWideBox() {
    GeoBBox box = GeoBBoxFactory.makeGeoBBox(PlanetModel.WGS84, 0.5, -0.5, -3.0, 1.0);
    GeoBBox expanded = box.expand(0.3);
    // 0.2 west of the left bound, i.e. lon -3.2 + 2 PI
    GeoPoint west = new GeoPoint(PlanetModel.WGS84, 0.0, -3.2 + 2 * Math.PI);
    assertTrue(expanded + " must contain " + west, expanded.isWithin(west));
  }
}
