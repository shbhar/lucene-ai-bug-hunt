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

public class TestGeoBBoxExpandAcrossDateline extends LuceneTestCase {

  public void testExpandRightBoundAcrossDateline() {
    GeoBBox box = GeoBBoxFactory.makeGeoBBox(PlanetModel.SPHERE, 0, -Math.PI / 4, 2.5, 3.0);
    GeoBBox expanded = box.expand(0.3);
    GeoPoint east = new GeoPoint(PlanetModel.SPHERE, -0.1, 3.2 - 2 * Math.PI);
    // expected right lon 3.0 + 0.3 to wrap to 3.3 - 2 PI (east is inside), actual: clamped at PI
    assertTrue(
        "expected the box to reach lon 3.3 - 2 PI and contain " + east + ", but was " + expanded,
        expanded.isWithin(east));
  }
}
