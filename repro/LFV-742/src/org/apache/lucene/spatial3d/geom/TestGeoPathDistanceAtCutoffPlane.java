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

public class TestGeoPathDistanceAtCutoffPlane extends LuceneTestCase {

  public void testDistanceOfPointInsidePath() {
    PlanetModel pm = PlanetModel.SPHERE;
    GeoPath path =
        GeoPathFactory.makeGeoPath(
            pm,
            1e-4,
            new GeoPoint[] {
              new GeoPoint(pm, 0, 0), new GeoPoint(pm, 0, 0.1), new GeoPoint(pm, 0, 0.2)
            });
    GeoPoint p = new GeoPoint(pm, -3.448684587613504E-6, 0.10000000000100001);
    assertTrue(path.isWithin(p));
    // expected: a distance of about 0.1 + 3.4e-6; actual: RuntimeException "Can't find world intersection"
    assertEquals(0.1 + 3.448684587613504E-6, path.computeDistance(DistanceStyle.ARC, p), 1e-9);
  }
}
