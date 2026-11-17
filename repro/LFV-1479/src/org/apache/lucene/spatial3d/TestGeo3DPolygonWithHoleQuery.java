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

public class TestGeo3DPolygonWithHoleQuery extends LuceneTestCase {

  public void testRectangleWithExtraEdgeVertexAndHole() {
    Polygon hole =
        new Polygon(
            new double[] {5.0004, 5.0004, 5.0006, 5.0006, 5.0004},
            new double[] {10.0009, 10.0011, 10.0011, 10.0009, 10.0009});
    Polygon rectangle =
        new Polygon(
            new double[] {5.0, 5.001, 5.001, 5.0, 5.0, 5.0},
            new double[] {10.0, 10.0, 10.002, 10.002, 10.001, 10.0},
            hole);
    // expected: a query, as for the same rectangle without the hole; actual:
    // IllegalArgumentException "... effectively parallel despite filtering; give up on tiling"
    assertNotNull(Geo3DPoint.newPolygonQuery("point", PlanetModel.WGS84, rectangle));
  }
}
