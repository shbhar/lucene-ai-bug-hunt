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
package org.apache.lucene.document;

import org.apache.lucene.geo.Rectangle;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Controls for TestDistanceQueryBoxNaN: neighbouring inputs build a box (expected to PASS on main). */
public class TestDistanceQueryBoxNaNControl extends LuceneTestCase {
  static final double LAT = TestDistanceQueryBoxNaN.LAT;
  static final double RADIUS = TestDistanceQueryBoxNaN.RADIUS;

  public void testRadiusOneMetreSmallerOrLarger() {
    for (double r : new double[] {RADIUS - 1, RADIUS + 1}) {
      Rectangle box = Rectangle.fromPointDistance(LAT, 0, r);
      assertTrue(box.minLat <= LAT && LAT <= box.maxLat);
    }
  }

  public void testCentreSlightlyNorthOrSouth() {
    // the knot switch is at 0.439453125 degrees; a centre 1e-9 degree away no longer straddles it
    for (double lat : new double[] {LAT - 1e-9, 0.439453125, LAT + 1e-9}) {
      Rectangle box = Rectangle.fromPointDistance(lat, 0, RADIUS);
      assertTrue(box.minLat <= lat && lat <= box.maxLat);
    }
  }
}
