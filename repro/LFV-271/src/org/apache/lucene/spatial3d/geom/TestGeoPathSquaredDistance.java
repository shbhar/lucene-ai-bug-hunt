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

public class TestGeoPathSquaredDistance extends LuceneTestCase {

  public void testLinearSquaredIsSquareOfLinearInsideSegment() {
    PlanetModel pm = PlanetModel.SPHERE;
    GeoPath path =
        GeoPathFactory.makeGeoPath(
            pm, 0.1, new GeoPoint[] {new GeoPoint(pm, 0, 0), new GeoPoint(pm, 0, 0.5)});
    GeoPoint onSegment = new GeoPoint(pm, 0.05, 0.25);
    double linear = path.computeDistance(DistanceStyle.LINEAR, onSegment);
    // expected LINEAR_SQUARED = LINEAR^2; actual LINEAR^4
    assertEquals(
        "LINEAR_SQUARED vs LINEAR^2 (LINEAR=" + linear + ")",
        linear * linear,
        path.computeDistance(DistanceStyle.LINEAR_SQUARED, onSegment),
        1e-12);
  }
}
