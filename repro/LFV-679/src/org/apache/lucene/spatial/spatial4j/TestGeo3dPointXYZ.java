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
package org.apache.lucene.spatial.spatial4j;

import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.SpatialRelation;

/**
 * Geo3dShapeFactory.pointXYZ(x, y, z) and its builders' pointXYZ treat (x, y, z) as geocentric
 * Cartesian coordinates, while spatial4j's ShapeFactory.pointXYZ is "a point of 3 dimensions" whose x
 * and y are the same longitude and latitude as in pointXY (spatial4j's readers pass [lon, lat, alt]).
 */
public class TestGeo3dPointXYZ extends LuceneTestCase {

  private static final SpatialContext GEO3D = new Geo3dSpatialContextFactory().newSpatialContext();

  public void testPointXYZKeepsLongitudeAndLatitude() {
    Point reference = SpatialContext.GEO.getShapeFactory().pointXYZ(30, 10, 0);
    assertEquals(30, reference.getX(), 0);
    assertEquals(10, reference.getY(), 0);
    Point p = GEO3D.getShapeFactory().pointXYZ(30, 10, 0);
    assertEquals("longitude", 30, p.getX(), 1e-9);
    assertEquals("latitude", 10, p.getY(), 1e-9);
  }

  public void testPolygonBuilderPointXYZ() {
    Shape viaXY =
        GEO3D.getShapeFactory().polygon().pointXY(10, 10).pointXY(20, 10).pointXY(20, 20).pointXY(10, 20).pointXY(10, 10).build();
    assertEquals(SpatialRelation.CONTAINS, viaXY.relate(GEO3D.getShapeFactory().pointXY(15, 15)));
    // the same ring with an elevation of 0 for every vertex
    Shape viaXYZ =
        GEO3D
            .getShapeFactory()
            .polygon()
            .pointXYZ(10, 10, 0)
            .pointXYZ(20, 10, 0)
            .pointXYZ(20, 20, 0)
            .pointXYZ(10, 20, 0)
            .pointXYZ(10, 10, 0)
            .build();
    assertEquals(SpatialRelation.CONTAINS, viaXYZ.relate(GEO3D.getShapeFactory().pointXY(15, 15)));
  }
}
