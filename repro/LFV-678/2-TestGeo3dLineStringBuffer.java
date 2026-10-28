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
import org.locationtech.spatial4j.io.ShapeIO;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.SpatialRelation;

/**
 * Geo3dShapeFactory's line-string buffer distance is handed to spatial3d's GeoPathFactory (which
 * takes radians) without the degrees-to-radians conversion that circle() applies, so a line buffered
 * by 1 degree is 1 radian (57.3 degrees) wide.
 */
public class TestGeo3dLineStringBuffer extends LuceneTestCase {

  private static final SpatialContext GEO3D = new Geo3dSpatialContextFactory().newSpatialContext();

  public void testBufferIsInDegreesLikeCircleRadius() {
    Point twentyDegreesNorth = GEO3D.getShapeFactory().pointXY(5, 20);
    // the same factory's circle takes its radius in degrees:
    assertEquals(
        SpatialRelation.DISJOINT,
        GEO3D.getShapeFactory().circle(5, 0, 1).relate(twentyDegreesNorth));
    // spatial4j's own factory agrees for the buffered line:
    assertEquals(
        SpatialRelation.DISJOINT,
        SpatialContext.GEO
            .getShapeFactory()
            .lineString()
            .pointXY(0, 0)
            .pointXY(10, 0)
            .buffer(1)
            .build()
            .relate(SpatialContext.GEO.getShapeFactory().pointXY(5, 20)));
    Shape line = GEO3D.getShapeFactory().lineString().pointXY(0, 0).pointXY(10, 0).buffer(1).build();
    assertEquals(
        "a point 20 degrees from a line buffered by 1 degree is outside the buffer",
        SpatialRelation.DISJOINT,
        line.relate(twentyDegreesNorth));
  }

  /**
   * spatial4j's polyshape format carries the buffer in degrees: a 1-degree buffered line written by
   * the spatial4j geo context and read back by the Geo3d context must still be 1 degree wide.
   */
  public void testPolyshapeBufferedLineString() throws Exception {
    Shape reference =
        SpatialContext.GEO.getShapeFactory().lineString().pointXY(0, 0).pointXY(10, 0).buffer(1).build();
    String poly = SpatialContext.GEO.getFormats().getWriter(ShapeIO.POLY).toString(reference);
    assertEquals(
        "spatial4j reads its own string back",
        SpatialRelation.DISJOINT,
        SpatialContext.GEO
            .getFormats()
            .getReader(ShapeIO.POLY)
            .read(poly)
            .relate(SpatialContext.GEO.getShapeFactory().pointXY(5, 20)));
    Shape line = GEO3D.getFormats().getReader(ShapeIO.POLY).read(poly);
    assertEquals(
        "read by the Geo3d context from " + poly,
        SpatialRelation.DISJOINT,
        line.relate(GEO3D.getShapeFactory().pointXY(5, 20)));
  }
}
