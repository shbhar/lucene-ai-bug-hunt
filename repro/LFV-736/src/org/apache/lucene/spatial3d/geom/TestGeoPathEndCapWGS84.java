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

public class TestGeoPathEndCapWGS84 extends LuceneTestCase {

  public void testOneKmPathDoesNotContainPoint20KmBeyondItsStart() {
    PlanetModel pm = PlanetModel.WGS84;
    GeoPoint start = new GeoPoint(pm, Math.toRadians(44.0), Math.toRadians(20.0));
    GeoPoint end = new GeoPoint(pm, Math.toRadians(45.0), Math.toRadians(20.0));
    GeoPath path = GeoPathFactory.makeGeoPath(pm, 1000 / pm.getMeanRadius(), new GeoPoint[] {start, end});
    GeoPoint south = new GeoPoint(pm, Math.toRadians(43.82), Math.toRadians(20.0));
    // expected: a 1 km wide path does not contain a point 20 km south of its first point; actual: it does
    assertFalse(
        "(43.82N,20E), 20 km south of the start of the 1 km path (44N,20E)-(45N,20E): expected outside, got isWithin=true",
        path.isWithin(south));
  }
}
