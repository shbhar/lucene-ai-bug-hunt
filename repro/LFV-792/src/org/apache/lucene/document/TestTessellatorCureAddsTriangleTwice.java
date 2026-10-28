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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.lucene.geo.GeoEncodingUtils;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.XYEncodingUtils;
import org.apache.lucene.geo.XYPoint;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Tessellator#cureLocalIntersections adds the triangle it cuts off to the tessellation twice (two
 * identical tessellation.add calls), so every polygon whose tessellation reaches that step is
 * indexed with a duplicated triangle, and ShapeDocValues weighs that triangle twice in its
 * centroid.
 *
 * <p>Uses public API only (XYShape, LatLonShape, ShapeField.decodeTriangle, ShapeDocValuesField).
 */
public class TestTessellatorCureAddsTriangleTwice extends LuceneTestCase {

  /**
   * A valid polygon (OGC / JTS valid): POLYGON((4 2, 3 0, 1 2, 0 0, 0 4, 4 2), (2 1, 3 2, 2 2, 2 1)),
   * a five-vertex shell with a triangular hole whose vertex (2,1) touches the shell edge (3,0)-(1,2)
   * at one point. Its tessellation reaches the cure step
   * on the default path (checkSelfIntersections=false).
   */
  private static XYPolygon validXY() {
    // shell clockwise, hole counter-clockwise (the Polygon javadoc's winding)
    XYPolygon hole = new XYPolygon(new float[] {2, 3, 2, 2}, new float[] {1, 2, 2, 1});
    return new XYPolygon(new float[] {4, 3, 1, 0, 0, 4}, new float[] {2, 0, 2, 0, 4, 2}, hole);
  }

  /** the same polygon in degrees */
  private static Polygon validLatLon() {
    Polygon hole = new Polygon(new double[] {1, 2, 2, 1}, new double[] {2, 3, 2, 2});
    return new Polygon(new double[] {2, 0, 2, 0, 4, 2}, new double[] {4, 3, 1, 0, 0, 4}, hole);
  }

  /**
   * A self-intersecting ring POLYGON((6 0, 6 4, 5 1, 6 5, 6 1, 2 6, 2 0, 6 0)) (outside the
   * Tessellator javadoc, but accepted by the default path checkSelfIntersections=false, which is
   * where the cure step is meant to help).
   */
  private static XYPolygon selfIntersectingXY() {
    return new XYPolygon(new float[] {6, 6, 5, 6, 6, 2, 2, 6}, new float[] {0, 4, 1, 5, 1, 6, 0, 0});
  }

  private static List<BytesRef> triangleBytes(IndexableField[] fields) {
    List<BytesRef> out = new ArrayList<>();
    for (IndexableField f : fields) {
      out.add(BytesRef.deepCopyOf(f.binaryValue()));
    }
    return out;
  }

  private static String duplicates(List<BytesRef> tris, boolean geo) {
    Set<BytesRef> seen = new HashSet<>();
    StringBuilder dups = new StringBuilder();
    for (BytesRef b : tris) {
      if (seen.add(b) == false) {
        ShapeField.DecodedTriangle t = new ShapeField.DecodedTriangle();
        ShapeField.decodeTriangle(b.bytes, t);
        dups.append(describe(t, geo)).append(' ');
      }
    }
    return dups.toString();
  }

  private static double dx(int v, boolean geo) {
    return geo ? GeoEncodingUtils.decodeLongitude(v) : XYEncodingUtils.decode(v);
  }

  private static double dy(int v, boolean geo) {
    return geo ? GeoEncodingUtils.decodeLatitude(v) : XYEncodingUtils.decode(v);
  }

  private static String describe(ShapeField.DecodedTriangle t, boolean geo) {
    return "["
        + dx(t.aX, geo) + "," + dy(t.aY, geo) + " " + dx(t.bX, geo) + "," + dy(t.bY, geo) + " "
        + dx(t.cX, geo) + "," + dy(t.cY, geo) + "]";
  }

  /** a tessellation is a set of triangles with disjoint interiors: no triangle twice */
  public void testValidXYPolygonIndexesNoTriangleTwice() {
    List<BytesRef> tris = triangleBytes(XYShape.createIndexableFields("shape", validXY()));
    String dups = duplicates(tris, false);
    assertEquals("triangles indexed more than once: " + dups, "", dups);
  }

  public void testValidLatLonPolygonIndexesNoTriangleTwice() {
    List<BytesRef> tris = triangleBytes(LatLonShape.createIndexableFields("shape", validLatLon()));
    String dups = duplicates(tris, true);
    assertEquals("triangles indexed more than once: " + dups, "", dups);
  }

  /**
   * ShapeDocValues weighs every triangle by its area; the doc value's centroid must be the
   * area-weighted centroid of the region the same shape indexes (its distinct triangles).
   */
  public void testDocValuesCentroidWeighsEachTriangleOnce() {
    XYPolygon p = validXY();
    Set<BytesRef> distinct = new HashSet<>(triangleBytes(XYShape.createIndexableFields("shape", p)));
    double area = 0, mx = 0, my = 0;
    for (BytesRef b : distinct) {
      ShapeField.DecodedTriangle t = new ShapeField.DecodedTriangle();
      ShapeField.decodeTriangle(b.bytes, t);
      double ax = dx(t.aX, false), ay = dy(t.aY, false);
      double bx = dx(t.bX, false), by = dy(t.bY, false);
      double cx = dx(t.cX, false), cy = dy(t.cY, false);
      double a = Math.abs(0.5 * ((bx - ax) * (cy - ay) - (cx - ax) * (by - ay)));
      area += a;
      mx += a * (ax + bx + cx) / 3;
      my += a * (ay + by + cy) / 3;
    }
    XYShapeDocValuesField dv = XYShape.createDocValueField("shape", p);
    XYPoint c = (XYPoint) dv.getCentroid();
    String msg =
        "doc-values centroid ("
            + c.getX() + "," + c.getY() + ") vs centroid of the indexed triangles ("
            + (mx / area) + "," + (my / area) + "); numberOfTerms=" + dv.numberOfTerms()
            + " distinct triangles=" + distinct.size();
    assertEquals(msg, mx / area, c.getX(), 1e-5);
    assertEquals(msg, my / area, c.getY(), 1e-5);
    assertEquals(msg, distinct.size(), dv.numberOfTerms());
  }

  /** robustness input (self-intersecting ring on the default path): still no triangle twice */
  public void testSelfIntersectingRingIndexesNoTriangleTwice() {
    List<BytesRef> tris = triangleBytes(XYShape.createIndexableFields("shape", selfIntersectingXY()));
    String dups = duplicates(tris, false);
    assertEquals("triangles indexed more than once: " + dups, "", dups);
  }
}
