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
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A valid polygon with two holes. Hole A touches the outer ring at exactly one vertex V = (2,5),
 * which the Tessellator javadoc allows ("Holes may only touch at one vertex"). Hole B touches
 * nothing; its left-most vertex (6,5) is level with V.
 *
 * <pre>
 *   outer ring: (0,0) (2,5) (0,10) (10,10) (10,0)   a 10x10 square, left side dented in to V
 *   hole A:     (2,5) (5,6) (4,8)                    shares only V with the outer ring
 *   hole B:     (6,5) (8,4) (8,6)                    touches nothing
 * </pre>
 *
 * <p>Twice the area of the polygon is 180 - 7 - 4 = 169. The point (3.75, 6.5) is strictly inside
 * hole A, so it is not part of the polygon. Only public API is used.
 */
public class TestTessellatorSharedVertexLaterHoleBridge extends LuceneTestCase {

  // x coordinates (longitudes) and y coordinates (latitudes) of the closed rings
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

  static Polygon latLonPolygon() {
    return new Polygon(d(SY), d(SX), new Polygon(d(AY), d(AX)), new Polygon(d(BY), d(BX)));
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
      double ax = t.getX(0), ay = t.getY(0), bx = t.getX(1), by = t.getY(1);
      double cx = t.getX(2), cy = t.getY(2);
      s += Math.abs((bx - ax) * (cy - ay) - (cx - ax) * (by - ay));
    }
    return s;
  }

  public void testTessellationCoversExactlyThePolygon() {
    // controls: each hole alone tessellates exactly
    assertEquals(180 - 7, twiceArea(Tessellator.tessellate(xyPolygon(true, false), false)), 0);
    assertEquals(180 - 4, twiceArea(Tessellator.tessellate(xyPolygon(false, true), false)), 0);
    // both holes: the triangles must cover the polygon exactly once
    assertEquals(
        "twice the area of the triangles",
        180 - 7 - 4,
        twiceArea(Tessellator.tessellate(xyPolygon(true, true), false)),
        0);
  }

  public void testValidPolygonAcceptedWithSelfIntersectionCheck() {
    Tessellator.tessellate(xyPolygon(true, false), true); // control
    Tessellator.tessellate(xyPolygon(false, true), true); // control
    Tessellator.tessellate(xyPolygon(true, true), true); // must not throw
  }

  public void testXYShapePointInHoleDoesNotIntersect() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : XYShape.createIndexableFields("shape", xyPolygon(true, true))) doc.add(f);
        w.addDocument(doc);
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        // control: a point of the polygon matches
        assertEquals(
            1,
            s.count(XYShape.newPointQuery("shape", QueryRelation.INTERSECTS, new float[] {5, 2})));
        float[] inHoleA = {3.75f, 6.5f};
        int intersects = s.count(XYShape.newPointQuery("shape", QueryRelation.INTERSECTS, inHoleA));
        int disjoint = s.count(XYShape.newPointQuery("shape", QueryRelation.DISJOINT, inHoleA));
        assertEquals(
            "point inside hole A: INTERSECTS, DISJOINT", "0, 1", intersects + ", " + disjoint);
      }
    }
  }

  public void testLatLonShapePointInHoleDoesNotIntersect() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : LatLonShape.createIndexableFields("shape", latLonPolygon())) doc.add(f);
        w.addDocument(doc);
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        // control: a point of the polygon matches (lat 2, lon 5)
        assertEquals(
            1,
            s.count(
                LatLonShape.newPointQuery("shape", QueryRelation.INTERSECTS, new double[] {2, 5})));
        double[] inHoleA = {6.5, 3.75}; // lat, lon
        int intersects =
            s.count(LatLonShape.newPointQuery("shape", QueryRelation.INTERSECTS, inHoleA));
        int disjoint = s.count(LatLonShape.newPointQuery("shape", QueryRelation.DISJOINT, inHoleA));
        assertEquals(
            "point inside hole A: INTERSECTS, DISJOINT", "0, 1", intersects + ", " + disjoint);
      }
    }
  }

  public void testXYShapeDocValuesBoxInHoleDoesNotIntersect() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(XYShape.createDocValueField("shape", xyPolygon(true, true)));
        w.addDocument(doc);
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        // control: a small box inside the polygon proper matches
        assertEquals(
            1,
            s.count(
                XYShape.newSlowDocValuesBoxQuery(
                    "shape", QueryRelation.INTERSECTS, 4.9f, 5.1f, 1.9f, 2.1f)));
        // a small box strictly inside hole A
        assertEquals(
            "doc values INTERSECTS, box inside hole A",
            0,
            s.count(
                XYShape.newSlowDocValuesBoxQuery(
                    "shape", QueryRelation.INTERSECTS, 3.7f, 3.8f, 6.4f, 6.6f)));
      }
    }
  }
}
