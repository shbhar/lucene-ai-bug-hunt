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

public class TestGeoZeroHeightFullLongitudeBox extends LuceneTestCase {

  public void testContainsPointOnItsLatitude() {
    GeoBBox box = GeoBBoxFactory.makeGeoBBox(PlanetModel.WGS84, 0.5, 0.5, -Math.PI, Math.PI);
    GeoPoint point = new GeoPoint(PlanetModel.WGS84, 0.5, 1.0);
    // expected: true, as for the same box over longitudes [-3, 3]; actual: false
    assertTrue("expected " + box + " to contain " + point + ", got false", box.isWithin(point));
  }
}
