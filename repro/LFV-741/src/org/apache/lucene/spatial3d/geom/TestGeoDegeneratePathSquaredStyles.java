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
 * A zero-width path (GeoPathFactory.makeGeoPath with width 0) returns, for the squared distance
 * styles, the LINEAR value from computePathCenterDistance and the fourth power from
 * computeOutsideDistance, instead of the square that LINEAR_SQUARED / NORMAL_SQUARED stand for (and
 * that a path of width 1e-6 returns).
 */
public class TestGeoDegeneratePathSquaredStyles extends LuceneTestCase {

  private static final PlanetModel PM = PlanetModel.SPHERE;
  private static final GeoPoint[] POINTS = {new GeoPoint(PM, 0, 0), new GeoPoint(PM, 0, 0.1)};
  // 0.01 rad beside the middle of the path
  private static final GeoPoint Q = new GeoPoint(PM, 0.01, 0.05);

  public void testPathCenterDistance() {
    GeoPath zeroWidth = GeoPathFactory.makeGeoPath(PM, 0.0, POINTS);
    GeoPath narrow = GeoPathFactory.makeGeoPath(PM, 1e-6, POINTS);
    double linear = zeroWidth.computePathCenterDistance(DistanceStyle.LINEAR, Q);
    assertEquals(
        "narrow path, LINEAR_SQUARED = LINEAR^2",
        linear * linear,
        narrow.computePathCenterDistance(DistanceStyle.LINEAR_SQUARED, Q),
        1e-12);
    assertEquals(
        "zero-width path, LINEAR_SQUARED",
        linear * linear,
        zeroWidth.computePathCenterDistance(DistanceStyle.LINEAR_SQUARED, Q),
        1e-12);
  }

  public void testOutsideDistance() {
    GeoPath zeroWidth = GeoPathFactory.makeGeoPath(PM, 0.0, POINTS);
    assertFalse(zeroWidth.isWithin(Q));
    double linear = zeroWidth.computeOutsideDistance(DistanceStyle.LINEAR, Q);
    assertEquals(0.01, linear, 1e-5);
    assertEquals(
        "zero-width path, outside LINEAR_SQUARED = LINEAR^2",
        linear * linear,
        zeroWidth.computeOutsideDistance(DistanceStyle.LINEAR_SQUARED, Q),
        1e-12);
  }
}
