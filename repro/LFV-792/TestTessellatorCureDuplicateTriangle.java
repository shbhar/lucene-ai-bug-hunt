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

import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.XYEncodingUtils;
import org.apache.lucene.geo.XYPoint;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * The ring POLYGON((6 3, 0 6, 4 0, 3 4, 5 1, 3 2, 6 3)) crosses itself, which the Polygon javadoc
 * excludes, but the default path (checkSelfIntersections = false) accepts it and the Tessellator's
 * cure step handles it by cutting off the triangle (4,0) (3,4) (3,2). That triangle must be stored
 * once, like every other triangle of the tessellation.
 */
public class TestTessellatorCureDuplicateTriangle extends LuceneTestCase {

  private static XYPolygon xyRing() {
    return new XYPolygon(new float[] {6, 0, 4, 3, 5, 3, 6}, new float[] {3, 6, 0, 4, 1, 2, 3});
  }

  /** The same ring in degrees, lat = y and lon = x. */
  private static Polygon latLonRing() {
    return new Polygon(new double[] {3, 6, 0, 4, 1, 2, 3}, new double[] {6, 0, 4, 3, 5, 3, 6});
  }

  private static Set<BytesRef> distinct(IndexableField[] fields) {
    Set<BytesRef> distinct = new HashSet<>();
    for (IndexableField f : fields) {
      distinct.add(BytesRef.deepCopyOf(f.binaryValue()));
    }
    return distinct;
  }

  public void testXYShapeIndexesEachTriangleOnce() {
    Field[] fields = XYShape.createIndexableFields("shape", xyRing());
    assertEquals("distinct triangles vs indexed triangles", distinct(fields).size(), fields.length);
  }

  public void testLatLonShapeIndexesEachTriangleOnce() {
    Field[] fields = LatLonShape.createIndexableFields("shape", latLonRing());
    assertEquals("distinct triangles vs indexed triangles", distinct(fields).size(), fields.length);
  }

  /** The doc value must describe the triangles the same shape indexes, each one once. */
  public void testShapeDocValuesCountEachTriangleOnce() {
    Set<BytesRef> triangles = distinct(XYShape.createIndexableFields("shape", xyRing()));
    double area = 0, x = 0, y = 0;
    for (BytesRef b : triangles) {
      ShapeField.DecodedTriangle t = new ShapeField.DecodedTriangle();
      ShapeField.decodeTriangle(b.bytes, t);
      double ax = XYEncodingUtils.decode(t.aX), ay = XYEncodingUtils.decode(t.aY);
      double bx = XYEncodingUtils.decode(t.bX), by = XYEncodingUtils.decode(t.bY);
      double cx = XYEncodingUtils.decode(t.cX), cy = XYEncodingUtils.decode(t.cY);
      double a = Math.abs(0.5 * ((bx - ax) * (cy - ay) - (cx - ax) * (by - ay)));
      area += a;
      x += a * (ax + bx + cx) / 3;
      y += a * (ay + by + cy) / 3;
    }
    XYShapeDocValuesField dv = XYShape.createDocValueField("shape", xyRing());
    XYPoint c = dv.getCentroid();
    String msg =
        "numberOfTerms="
            + dv.numberOfTerms()
            + " for "
            + triangles.size()
            + " distinct triangles; centroid ("
            + c.getX()
            + ", "
            + c.getY()
            + ") vs centroid of the distinct triangles ("
            + (x / area)
            + ", "
            + (y / area)
            + ")";
    assertEquals(msg, triangles.size(), dv.numberOfTerms());
    assertEquals(msg, x / area, c.getX(), 1e-5);
    assertEquals(msg, y / area, c.getY(), 1e-5);
  }
}
