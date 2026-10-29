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

public class TestNormalDistanceOnEllipsoid extends LuceneTestCase {

  public void testNormalDistanceOfAPointToItself() {
    GeoPoint p = new GeoPoint(PlanetModel.WGS84, 0.3, 0.2);
    // perpendicular distance from p to the line through the origin and p: expected 0, actual 0.00165
    assertEquals(0.0, p.normalDistance(p), 1e-12);
  }
}
