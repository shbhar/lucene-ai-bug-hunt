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

import java.util.Locale;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.io.ShapeIO;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.SpatialRelation;

/**
 * The buffer of a line string in the Geo3d spatial context is a distance in degrees, like the
 * radius of a circle from the same factory and every other spatial4j distance in a geo context.
 */
public class TestGeo3dLineStringBuffer extends LuceneTestCase {

  private static final SpatialContext GEO3D = new Geo3dSpatialContextFactory().newSpatialContext();

  /** The line (0,0)-(10,0) with the given buffer. */
  private static Shape line(SpatialContext ctx, double buffer) {
    return ctx.getShapeFactory().lineString().pointXY(0, 0).pointXY(10, 0).buffer(buffer).build();
  }

  private static Point point(SpatialContext ctx, double x, double y) {
    return ctx.getShapeFactory().pointXY(x, y);
  }

  private static String box(Shape shape) {
    Rectangle r = shape.getBoundingBox();
    return String.format(
        Locale.ROOT,
        "[%.1f, %.1f] x [%.1f, %.1f]",
        r.getMinX(),
        r.getMaxX(),
        r.getMinY(),
        r.getMaxY());
  }

  /** A line buffered by 1 degree reaches 1 degree from the line, not 1 radian (57.3 degrees). */
  public void testBufferIsInDegrees() {
    // controls: spatial4j's own geo context, and this factory's circle, whose radius is in degrees
    assertEquals(
        SpatialRelation.DISJOINT,
        line(SpatialContext.GEO, 1).relate(point(SpatialContext.GEO, 5, 20)));
    assertEquals(
        SpatialRelation.DISJOINT,
        GEO3D.getShapeFactory().circle(5, 0, 1).relate(point(GEO3D, 5, 20)));

    Shape line = line(GEO3D, 1);
    assertEquals(SpatialRelation.CONTAINS, line.relate(point(GEO3D, 5, 0.5)));
    assertEquals(
        "(5, 20) is 20 degrees from a line buffered by 1 degree; bounding box " + box(line),
        SpatialRelation.DISJOINT,
        line.relate(point(GEO3D, 5, 20)));
  }

  /** spatial4j's polyshape format carries the buffer in degrees, as it does a circle's radius. */
  public void testPolyshapeBufferIsInDegrees() throws Exception {
    String poly =
        SpatialContext.GEO
            .getFormats()
            .getWriter(ShapeIO.POLY)
            .toString(line(SpatialContext.GEO, 1));
    assertEquals(
        SpatialRelation.DISJOINT,
        SpatialContext.GEO
            .getFormats()
            .getReader(ShapeIO.POLY)
            .read(poly)
            .relate(point(SpatialContext.GEO, 5, 20)));

    Shape line = GEO3D.getFormats().getReader(ShapeIO.POLY).read(poly);
    assertEquals(
        "polyshape " + poly + " read by the Geo3d context; bounding box " + box(line),
        SpatialRelation.DISJOINT,
        line.relate(point(GEO3D, 5, 20)));
  }

  /** A 2-degree buffer is legal; it must not be rejected as a 2-radian (115 degree) path. */
  public void testTwoDegreeBuffer() {
    assertEquals(
        SpatialRelation.DISJOINT,
        line(SpatialContext.GEO, 2).relate(point(SpatialContext.GEO, 5, 20)));

    Shape line;
    try {
      line = line(GEO3D, 2);
    } catch (IllegalArgumentException e) {
      throw new AssertionError("buffer(2) was rejected: " + e.getMessage(), e);
    }
    assertEquals(SpatialRelation.CONTAINS, line.relate(point(GEO3D, 5, 1.5)));
    assertEquals(SpatialRelation.DISJOINT, line.relate(point(GEO3D, 5, 20)));
  }
}
