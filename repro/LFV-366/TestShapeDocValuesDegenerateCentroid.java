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

import org.apache.lucene.geo.Line;
import org.apache.lucene.geo.Point;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.Rectangle;
import org.apache.lucene.geo.XYLine;
import org.apache.lucene.geo.XYPoint;
import org.apache.lucene.geo.XYRectangle;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The centroid of a shape doc value is an average of the shape's own points, so it must lie inside
 * the shape's own bounding box. Shapes whose total length or area is zero after encoding get
 * (0, 0) instead. Uses only public API.
 */
public class TestShapeDocValuesDegenerateCentroid extends LuceneTestCase {

  private static void assertInside(Point c, Rectangle box) {
    assertTrue(
        "centroid " + c + " must be inside the bounding box " + box,
        c.getLat() >= box.minLat
            && c.getLat() <= box.maxLat
            && c.getLon() >= box.minLon
            && c.getLon() <= box.maxLon);
  }

  /** a two-point line whose points are equal (Line accepts repeated points) */
  public void testLineWithRepeatedPoint() {
    Line line = new Line(new double[] {40, 40}, new double[] {-70, -70});
    LatLonShapeDocValuesField dv = LatLonShape.createDocValueField("shape", line);
    assertInside(dv.getCentroid(), dv.getBoundingBox());
  }

  /** two distinct points closer than one encoding step (about 1 cm) */
  public void testLineShorterThanOneEncodingStep() {
    Line line = new Line(new double[] {40, 40.000000001}, new double[] {-70, -70});
    LatLonShapeDocValuesField dv = LatLonShape.createDocValueField("shape", line);
    assertInside(dv.getCentroid(), dv.getBoundingBox());
  }

  /** the same line through the other public factory gets the right centroid */
  public void testFactoriesAgree() {
    Line line = new Line(new double[] {40, 40}, new double[] {-70, -70});
    Point fromLine = LatLonShape.createDocValueField("shape", line).getCentroid();
    Point fromFields =
        LatLonShape.createDocValueField("shape", LatLonShape.createIndexableFields("shape", line))
            .getCentroid();
    assertEquals(fromFields, fromLine);
  }

  /** a polygon thinner than one latitude encoding step: its triangles have zero area */
  public void testSliverPolygon() {
    Polygon sliver =
        new Polygon(new double[] {40, 40, 40.000000001, 40}, new double[] {-70, -60, -65, -70});
    LatLonShapeDocValuesField dv = LatLonShape.createDocValueField("shape", sliver);
    assertInside(dv.getCentroid(), dv.getBoundingBox());
  }

  public void testXYLineWithRepeatedPoint() {
    XYLine line = new XYLine(new float[] {5, 5}, new float[] {7, 7});
    XYShapeDocValuesField dv = XYShape.createDocValueField("shape", line);
    XYPoint c = dv.getCentroid();
    XYRectangle box = dv.getBoundingBox();
    assertTrue(
        "centroid " + c + " must be inside the bounding box " + box,
        c.getX() >= box.minX && c.getX() <= box.maxX && c.getY() >= box.minY && c.getY() <= box.maxY);
  }
}
