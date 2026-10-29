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
package org.apache.lucene.spatial3d;

import org.apache.lucene.geo.Polygon;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Accountable;

public class TestGeo3DShapeQueryRamBytesUsed extends LuceneTestCase {

  private static long ramBytesUsed(int edges) {
    double[] lats = new double[edges + 1];
    double[] lons = new double[edges + 1];
    for (int i = 0; i < edges; i++) {
      lats[i] = 30 * Math.sin(-2 * Math.PI * i / edges);
      lons[i] = 40 * Math.cos(-2 * Math.PI * i / edges);
    }
    lats[edges] = lats[0];
    lons[edges] = lons[0];
    Polygon polygon = new Polygon(lats, lons);
    return ((Accountable) Geo3DPoint.newLargePolygonQuery("f", PlanetModel.WGS84, polygon))
        .ramBytesUsed();
  }

  public void testRamBytesUsedGrowsWithPolygonSize() {
    long small = ramBytesUsed(8);
    long large = ramBytesUsed(2000);
    // expected: the 2000-edge query reports more heap than the 8-edge one; actual: both report 592
    assertTrue("8 edges: " + small + " bytes, 2000 edges: " + large + " bytes", large > small);
  }
}
