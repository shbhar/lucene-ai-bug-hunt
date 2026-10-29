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

public class TestGeoExactCircleOutsideDistance extends LuceneTestCase {

  public void testPointDueNorthOfCentre() {
    GeoCircle circle = GeoCircleFactory.makeExactGeoCircle(PlanetModel.WGS84, 0.3, 0.2, 0.1, 1e-6);
    GeoPoint point = new GeoPoint(PlanetModel.WGS84, 0.41, 0.2);
    // expected: 0.009558 to the circle's north edge point (latitude 0.400442); actual: +Infinity
    assertEquals(0.009558, circle.computeOutsideDistance(DistanceStyle.ARC, point), 1e-6);
  }
}
