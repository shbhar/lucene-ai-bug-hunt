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

import java.util.List;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.Tessellator;
import org.apache.lucene.geo.XYPoint;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A valid polygon with two holes: hole A touches the outer ring at exactly one vertex V (allowed:
 * "Holes may only touch at one vertex", Tessellator javadoc) and hole B touches nothing; the
 * left-most vertex of hole B lies on the horizontal line through V. The tessellation used by
 * XYShape/LatLonShape.createIndexableFields covers hole A (and overlaps itself), so a point inside
 * hole A matches an INTERSECTS query; with checkSelfIntersections=true the polygon is rejected.
 *
 * <pre>
 *  outer ring (clockwise): (0,0) (2,5) (0,10) (10,10) (10,0)   square, left side dented in at V=(2,5)
 *  hole A (ccw):           (2,5) (5,6) (4,8)                    shares only V with the outer ring
 *  hole B (ccw):           (6,5) (8,4) (8,6)                    strictly inside, left-most vertex (6,5)
 * </pre>
 */
public class TestTessellatorSharedVertexLaterHoleBridge extends LuceneTestCase {

  static final float[] SX = {0, 2, 0, 10, 10, 0}, SY = {0, 5, 10, 10, 0, 0};
  static final float[] AX = {2, 5, 4, 2}, AY = {5, 6, 8, 5};
  static final float[] BX = {6, 8, 8, 6}, BY = {5, 4, 6, 5};

  static XYPolygon xyPolygon(boolean withA, boolean withB) {
    XYPolygon a = new XYPolygon(AX, AY), b = new XYPolygon(BX, BY);
    if (withA && withB) return new XYPolygon(SX, SY, a, b);
    if (withA) return new XYPolygon(SX, SY, a);
    if (withB) return new XYPolygon(SX, SY, b);
    return new XYPolygon(SX, SY);
  }

  static double[] d(float[] f) {
    double[] r = new double[f.length];
    for (int i = 0; i < f.length; i++) r[i] = f[i];
    return r;
  }

  /** twice the area covered by the triangles (sum of |orientation|) */
  static double twiceArea(List<Tessellator.Triangle> ts) {
    double s = 0;
    for (Tessellator.Triangle t : ts) {
      double ax = t.getX(0), ay = t.getY(0), bx = t.getX(1), by = t.getY(1), cx = t.getX(2), cy = t.getY(2);
      s += Math.abs((bx - ax) * (cy - ay) - (cx - ax) * (by - ay));
    }
    return s;
  }

  // twice the area of the polygon: outer 180, hole A 7, hole B 4
  static final double POLY_2A = 180 - 7 - 4;

  public void testTessellationCoversExactlyThePolygon() {
    // controls: each hole alone tessellates exactly
    assertEquals(180 - 7, twiceArea(Tessellator.tessellate(xyPolygon(true, false), false)), 0);
    assertEquals(180 - 4, twiceArea(Tessellator.tessellate(xyPolygon(false, true), false)), 0);
    // both holes: the triangles must cover the polygon exactly once
    assertEquals(
        "twice the area of the triangles", POLY_2A, twiceArea(Tessellator.tessellate(xyPolygon(true, true), false)), 0);
  }

  public void testValidPolygonAcceptedWithSelfIntersectionCheck() {
    // the polygon is valid (simple rings, holes inside, hole A touches the outer ring at one vertex)
    Tessellator.tessellate(xyPolygon(true, false), true); // control
    Tessellator.tessellate(xyPolygon(false, true), true); // control
    Tessellator.tessellate(xyPolygon(true, true), true); // must not throw
  }

  public void testXYShapePointInHoleDoesNotIntersect() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : XYShape.createIndexableFields("shape", xyPolygon(true, true))) doc.add(f);
        w.addDocument(doc);
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        s.setQueryCache(null);
        // control: a point of the polygon matches
        assertEquals(1, s.count(XYShape.newPointQuery("shape", QueryRelation.INTERSECTS, new float[] {5f, 2f})));
        // (3.75, 6.5) is strictly inside hole A, so it is not part of the polygon
        assertEquals(
            "point inside hole A", 0, s.count(XYShape.newPointQuery("shape", QueryRelation.INTERSECTS, new float[] {3.75f, 6.5f})));
        assertEquals(1, s.count(XYShape.newGeometryQuery("shape", QueryRelation.DISJOINT, new XYPoint(3.75f, 6.5f))));
      }
    }
  }

  public void testLatLonShapePointInHoleDoesNotIntersect() throws Exception {
    Polygon poly =
        new Polygon(d(SY), d(SX), new Polygon(d(AY), d(AX)), new Polygon(d(BY), d(BX)));
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : LatLonShape.createIndexableFields("shape", poly)) doc.add(f);
        w.addDocument(doc);
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        s.setQueryCache(null);
        assertEquals(1, s.count(LatLonShape.newPointQuery("shape", QueryRelation.INTERSECTS, new double[] {2d, 5d})));
        assertEquals(
            "point inside hole A",
            0,
            s.count(LatLonShape.newPointQuery("shape", QueryRelation.INTERSECTS, new double[] {6.5d, 3.75d})));
      }
    }
  }
}
