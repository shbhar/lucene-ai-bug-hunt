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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.geo.GeoEncodingUtils;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.XYEncodingUtils;
import org.apache.lucene.geo.XYPoint;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.PointValues;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Independent check: when the tessellator's cure stage cuts off a triangle, does the shape index
 * that triangle once (as a triangular mesh should) or twice?
 *
 * <p>Public API only: XYShape / LatLonShape createIndexableFields and createDocValueField,
 * ShapeField.decodeTriangle, the doc-values field's numberOfTerms() and getCentroid(), and
 * PointValues.size() of an indexed document.
 */
public class TestCureDuplicateTriangle extends LuceneTestCase {

  // Valid polygon: the hole touches the shell at exactly one point, (3,2), which lies on the
  // shell edge (1,3)-(5,1). Shell clockwise, hole counter-clockwise (Polygon javadoc order).
  private static final float[] SHELL_X = {0, 4, 5, 1, 0};
  private static final float[] SHELL_Y = {3, 5, 1, 3, 3};
  private static final float[] HOLE_X = {3, 4, 3, 3};
  private static final float[] HOLE_Y = {2, 2, 3, 2};
  // Control: same shell, hole strictly inside (no touching point).
  private static final float[] INNER_HOLE_X = {3, 4, 3, 3};
  private static final float[] INNER_HOLE_Y = {3, 3, 4, 3};
  // Self-crossing ring (rejected by checkSelfIntersections=true, accepted by the default path).
  private static final float[] RING_X = {1, 6, 6, 4, 0, 5, 1};
  private static final float[] RING_Y = {3, 5, 1, 4, 2, 3, 3};

  private static XYPolygon touchingHolePolygon() {
    return new XYPolygon(SHELL_X, SHELL_Y, new XYPolygon(HOLE_X, HOLE_Y));
  }

  /** decoded triangle: encoded vertex ints (identity) plus decoded doubles (geometry) */
  private record Tri(String key, double[] x, double[] y) {
    double area() {
      return Math.abs((x[1] - x[0]) * (y[2] - y[0]) - (x[2] - x[0]) * (y[1] - y[0])) / 2;
    }
  }

  private static List<Tri> decode(Field[] fields, boolean xy) {
    List<Tri> out = new ArrayList<>();
    for (Field f : fields) {
      BytesRef br = f.binaryValue();
      byte[] bytes = Arrays.copyOfRange(br.bytes, br.offset, br.offset + br.length);
      ShapeField.DecodedTriangle t = new ShapeField.DecodedTriangle();
      ShapeField.decodeTriangle(bytes, t);
      int[][] v = {{t.aX, t.aY}, {t.bX, t.bY}, {t.cX, t.cY}};
      String[] k = new String[3];
      double[] x = new double[3];
      double[] y = new double[3];
      for (int i = 0; i < 3; i++) {
        k[i] = v[i][0] + ":" + v[i][1];
        if (xy) {
          x[i] = XYEncodingUtils.decode(v[i][0]);
          y[i] = XYEncodingUtils.decode(v[i][1]);
        } else {
          x[i] = GeoEncodingUtils.decodeLongitude(v[i][0]);
          y[i] = GeoEncodingUtils.decodeLatitude(v[i][1]);
        }
      }
      Arrays.sort(k);
      out.add(new Tri(String.join(" ", k), x, y));
    }
    return out;
  }

  /** returns a description of every triangle that occurs more than once, or "" */
  private static String duplicates(List<Tri> tris) {
    Map<String, Integer> count = new LinkedHashMap<>();
    Map<String, Tri> first = new LinkedHashMap<>();
    for (Tri t : tris) {
      count.merge(t.key(), 1, Integer::sum);
      first.putIfAbsent(t.key(), t);
    }
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Integer> e : count.entrySet()) {
      if (e.getValue() > 1) {
        Tri t = first.get(e.getKey());
        sb.append(e.getValue())
            .append("x [(")
            .append(t.x()[0])
            .append(",")
            .append(t.y()[0])
            .append(") (")
            .append(t.x()[1])
            .append(",")
            .append(t.y()[1])
            .append(") (")
            .append(t.x()[2])
            .append(",")
            .append(t.y()[2])
            .append(") area=")
            .append(t.area())
            .append("] ");
      }
    }
    return sb.toString();
  }

  private static List<Tri> distinct(List<Tri> tris) {
    Map<String, Tri> m = new LinkedHashMap<>();
    for (Tri t : tris) {
      m.putIfAbsent(t.key(), t);
    }
    return new ArrayList<>(m.values());
  }

  /** area-weighted centroid of a set of triangles */
  private static double[] centroid(List<Tri> tris) {
    double sx = 0, sy = 0, sa = 0;
    for (Tri t : tris) {
      double a = t.area();
      sx += a * (t.x()[0] + t.x()[1] + t.x()[2]) / 3;
      sy += a * (t.y()[0] + t.y()[1] + t.y()[2]) / 3;
      sa += a;
    }
    return new double[] {sx / sa, sy / sa};
  }

  /** shoelace area of a polygon with holes (independent of the tessellation) */
  private static double polygonArea(float[] sx, float[] sy, float[] hx, float[] hy) {
    return Math.abs(ringArea(sx, sy)) - Math.abs(ringArea(hx, hy));
  }

  private static double ringArea(float[] x, float[] y) {
    double s = 0;
    for (int i = 0; i < x.length - 1; i++) {
      s += (double) x[i] * y[i + 1] - (double) x[i + 1] * y[i];
    }
    return s / 2;
  }

  private static double sumArea(List<Tri> tris) {
    double a = 0;
    for (Tri t : tris) {
      a += t.area();
    }
    return a;
  }

  /** valid polygon, default path: every tessellated triangle should be indexed once */
  public void testValidTouchingHoleXYIndexesEachTriangleOnce() {
    Field[] fields = XYShape.createIndexableFields("f", touchingHolePolygon());
    List<Tri> tris = decode(fields, true);
    assertEquals(
        "XYShape.createIndexableFields(valid polygon): triangles indexed more than once: "
            + duplicates(tris)
            + " fields="
            + tris.size()
            + " distinct="
            + distinct(tris).size()
            + " sumArea(all)="
            + sumArea(tris)
            + " sumArea(distinct)="
            + sumArea(distinct(tris))
            + " polygonArea="
            + polygonArea(SHELL_X, SHELL_Y, HOLE_X, HOLE_Y),
        "",
        duplicates(tris));
  }

  /** same valid polygon through the strict path (it passes the self-intersection check) */
  public void testValidTouchingHoleXYStrictPathIndexesEachTriangleOnce() {
    Field[] fields = XYShape.createIndexableFields("f", touchingHolePolygon(), true);
    List<Tri> tris = decode(fields, true);
    assertEquals(
        "XYShape.createIndexableFields(valid polygon, checkSelfIntersections=true): duplicates: "
            + duplicates(tris),
        "",
        duplicates(tris));
  }

  /** LatLonShape: same valid polygon, lat = y, lon = x */
  public void testValidTouchingHoleLatLonIndexesEachTriangleOnce() {
    Polygon hole = new Polygon(toD(HOLE_Y), toD(HOLE_X));
    Polygon polygon = new Polygon(toD(SHELL_Y), toD(SHELL_X), hole);
    Field[] fields = LatLonShape.createIndexableFields("f", polygon);
    List<Tri> tris = decode(fields, false);
    assertEquals(
        "LatLonShape.createIndexableFields(valid polygon): duplicates: "
            + duplicates(tris)
            + " fields="
            + tris.size()
            + " distinct="
            + distinct(tris).size(),
        "",
        duplicates(tris));
  }

  /** the duplicate reaches the index: one extra point value per duplicated triangle */
  public void testValidTouchingHoleXYIndexPointCount() throws Exception {
    Field[] fields = XYShape.createIndexableFields("f", touchingHolePolygon());
    int distinct = distinct(decode(fields, true)).size();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      Document doc = new Document();
      for (Field f : fields) {
        doc.add(f);
      }
      w.addDocument(doc);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        PointValues pv = r.leaves().get(0).reader().getPointValues("f");
        assertEquals(
            "indexed point values for one valid polygon vs distinct tessellation triangles",
            distinct,
            pv.size());
      }
    }
  }

  /**
   * doc values: numberOfTerms and getCentroid should count each triangle once. The reference is
   * the same field's own distinct triangles (the true polygon centroid is printed for context).
   */
  public void testValidTouchingHoleXYDocValuesCountEachTriangleOnce() {
    XYPolygon polygon = touchingHolePolygon();
    List<Tri> distinct = distinct(decode(XYShape.createIndexableFields("f", polygon), true));
    XYShapeDocValuesField dv = XYShape.createDocValueField("f", polygon);
    XYPoint c = dv.getCentroid();
    double[] want = centroid(distinct);
    String msg =
        "numberOfTerms="
            + dv.numberOfTerms()
            + " distinctTriangles="
            + distinct.size()
            + " docValuesCentroid=("
            + c.getX()
            + ","
            + c.getY()
            + ") centroidOfDistinctTriangles=("
            + want[0]
            + ","
            + want[1]
            + ")";
    assertEquals(msg, distinct.size(), dv.numberOfTerms());
    assertEquals(msg, want[0], c.getX(), 1e-4);
    assertEquals(msg, want[1], c.getY(), 1e-4);
  }

  /** self-crossing ring: Lucene's own strict check rejects it; the default path indexes it */
  public void testSelfCrossingRingDefaultPathIndexesEachTriangleOnce() {
    XYPolygon ring = new XYPolygon(RING_X, RING_Y);
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class, () -> XYShape.createIndexableFields("f", ring, true));
    assertTrue(e.getMessage(), e.getMessage().contains("self-intersection"));
    List<Tri> tris = decode(XYShape.createIndexableFields("f", ring), true);
    assertEquals(
        "XYShape.createIndexableFields(self-crossing ring, default path): duplicates: "
            + duplicates(tris)
            + " fields="
            + tris.size()
            + " distinct="
            + distinct(tris).size(),
        "",
        duplicates(tris));
  }

  /** control: hole strictly inside: no duplicates, exact area, centroid matches the polygon's */
  public void testControlInteriorHoleIsExact() {
    XYPolygon polygon =
        new XYPolygon(SHELL_X, SHELL_Y, new XYPolygon(INNER_HOLE_X, INNER_HOLE_Y));
    List<Tri> tris = decode(XYShape.createIndexableFields("f", polygon), true);
    assertEquals("control duplicates: " + duplicates(tris), "", duplicates(tris));
    double area = polygonArea(SHELL_X, SHELL_Y, INNER_HOLE_X, INNER_HOLE_Y);
    assertEquals("control area", area, sumArea(tris), 1e-9);
    XYShapeDocValuesField dv = XYShape.createDocValueField("f", polygon);
    double[] want = polygonCentroid(SHELL_X, SHELL_Y, INNER_HOLE_X, INNER_HOLE_Y);
    assertEquals("control numberOfTerms", tris.size(), dv.numberOfTerms());
    assertEquals("control centroid x", want[0], dv.getCentroid().getX(), 1e-4);
    assertEquals("control centroid y", want[1], dv.getCentroid().getY(), 1e-4);
  }

  /** shoelace centroid of shell minus hole */
  private static double[] polygonCentroid(float[] sx, float[] sy, float[] hx, float[] hy) {
    double[] s = ringMoments(sx, sy);
    double[] h = ringMoments(hx, hy);
    // normalise both rings to positive area before subtracting
    double ss = Math.signum(s[0]);
    double hs = Math.signum(h[0]);
    double a = ss * s[0] - hs * h[0];
    double mx = ss * s[1] - hs * h[1];
    double my = ss * s[2] - hs * h[2];
    return new double[] {mx / a, my / a};
  }

  private static double[] ringMoments(float[] x, float[] y) {
    double a = 0, mx = 0, my = 0;
    for (int i = 0; i < x.length - 1; i++) {
      double cr = (double) x[i] * y[i + 1] - (double) x[i + 1] * y[i];
      a += cr / 2;
      mx += (x[i] + x[i + 1]) * cr / 6;
      my += (y[i] + y[i + 1]) * cr / 6;
    }
    return new double[] {a, mx, my};
  }

  private static double[] toD(float[] f) {
    double[] d = new double[f.length];
    for (int i = 0; i < f.length; i++) {
      d[i] = f[i];
    }
    return d;
  }
}
