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

public class TestGeoDegeneratePathSquaredStyles extends LuceneTestCase {

  public void testZeroWidthPathLinearSquared() {
    PlanetModel pm = PlanetModel.SPHERE;
    GeoPath path =
        GeoPathFactory.makeGeoPath(
            pm, 0, new GeoPoint[] {new GeoPoint(pm, 0, 0), new GeoPoint(pm, 0, 0.1)});
    GeoPoint q = new GeoPoint(pm, 0.01, 0.05);
    // expected LINEAR^2 (~1e-4) from both calls; actual LINEAR (~1e-2), then LINEAR^4 (~1e-8)
    double center = path.computePathCenterDistance(DistanceStyle.LINEAR, q);
    assertEquals(
        "computePathCenterDistance(LINEAR_SQUARED)",
        center * center,
        path.computePathCenterDistance(DistanceStyle.LINEAR_SQUARED, q),
        1e-12);
    double outside = path.computeOutsideDistance(DistanceStyle.LINEAR, q);
    assertEquals(
        "computeOutsideDistance(LINEAR_SQUARED)",
        outside * outside,
        path.computeOutsideDistance(DistanceStyle.LINEAR_SQUARED, q),
        1e-12);
  }
}
