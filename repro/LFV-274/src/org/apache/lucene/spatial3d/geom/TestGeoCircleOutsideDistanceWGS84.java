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

public class TestGeoCircleOutsideDistanceWGS84 extends LuceneTestCase {

  public void testOutsideDistanceToSmallCircle() {
    PlanetModel pm = PlanetModel.WGS84;
    double lat = Math.toRadians(40), lon = Math.toRadians(-74);
    GeoCircle circle = GeoCircleFactory.makeGeoCircle(pm, lat, lon, 1000 / pm.getMeanRadius());
    GeoPoint east = new GeoPoint(pm, lat, lon + 3000 / (pm.getMeanRadius() * Math.cos(lat)));
    double meters = circle.computeOutsideDistance(DistanceStyle.ARC, east) * pm.getMeanRadius();
    // expected: ~2000 m from a point 3 km east of the centre to the 1 km circle; actual: Infinity
    assertEquals(2000, meters, 100);
  }
}
