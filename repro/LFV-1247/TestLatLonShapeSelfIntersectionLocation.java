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

import org.apache.lucene.geo.Polygon;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLatLonShapeSelfIntersectionLocation extends LuceneTestCase {

  public void testRetracedEdgeReportsFiniteLocation() {
    // (lon, lat) points (0.55, 3.85) and (0.2, 1.4) lie on the first edge (lat = 7 * lon)
    Polygon polygon =
        new Polygon(
            new double[] {0, 7, 4, 3.85, 1.4, 2, 0}, new double[] {0, 1, 8, 0.55, 0.2, -7, 0});
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class,
            () -> LatLonShape.createIndexableFields("shape", polygon, true));
    assertFalse(e.getMessage(), e.getMessage().contains("Infinity"));
  }
}
