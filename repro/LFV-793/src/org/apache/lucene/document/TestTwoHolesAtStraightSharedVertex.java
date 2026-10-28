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
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.Tessellator;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A valid polygon (OGC / JTS valid) whose shell and two holes meet at the vertex P = (2,6): the
 * shell edge (2,7)-(2,6) and the hole edge (2,6)-(2,1) lie on one straight line through P. The
 * Tessellator covers the second hole, so LatLonShape and XYShape match points inside it.
 *
 * <p>POLYGON((2 6, 2 7, 4 0, 0 0, 2 6), (2 6, 3 1, 3 2, 2 6), (2 6, 1 1, 2 1, 2 6)): shell
 * clockwise, holes counter-clockwise (the Polygon javadoc's winding). Shell (2,6) (2,7) (4,0)
 * (0,0): twice the area is 26. Hole A (2,6) (3,1) (3,2): 1. Hole B (2,6) (1,1) (2,1): 5. Twice the polygon area is 20. The point (1.8, 2) is strictly inside the
 * shell and strictly inside hole B; the point (0.9, 0.5) is inside the polygon.
 *
 * <p>Uses public API only.
 */
public class TestTwoHolesAtStraightSharedVertex extends LuceneTestCase {

  private static XYPolygon xy() {
    XYPolygon holeA = new XYPolygon(new float[] {2, 3, 3, 2}, new float[] {6, 1, 2, 6});
    XYPolygon holeB = new XYPolygon(new float[] {2, 1, 2, 2}, new float[] {6, 1, 1, 6});
    return new XYPolygon(new float[] {2, 2, 4, 0, 2}, new float[] {6, 7, 0, 0, 6}, holeA, holeB);
  }

  /** the same polygon in degrees (lat = y, lon = x) */
  private static Polygon latLon() {
    Polygon holeA = new Polygon(new double[] {6, 1, 2, 6}, new double[] {2, 3, 3, 2});
    Polygon holeB = new Polygon(new double[] {6, 1, 1, 6}, new double[] {2, 1, 2, 2});
    return new Polygon(new double[] {6, 7, 0, 0, 6}, new double[] {2, 2, 4, 0, 2}, holeA, holeB);
  }

  public void testTessellationAreaEqualsPolygonArea() {
    for (boolean checkSelfIntersections : new boolean[] {false, true}) {
      List<Tessellator.Triangle> ts = Tessellator.tessellate(xy(), checkSelfIntersections);
      double twiceArea = 0;
      for (Tessellator.Triangle t : ts) {
        twiceArea +=
            Math.abs(
                (t.getX(1) - t.getX(0)) * (t.getY(2) - t.getY(0))
                    - (t.getX(2) - t.getX(0)) * (t.getY(1) - t.getY(0)));
      }
      assertEquals(
          "twice the tessellation area, checkSelfIntersections=" + checkSelfIntersections,
          20d,
          twiceArea,
          0d);
    }
  }

  public void testXYShapePointInsideHoleDoesNotMatch() throws Exception {
    try (Directory dir = newDirectory()) {
      try (RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
        Document doc = new Document();
        for (Field f : XYShape.createIndexableFields("shape", xy())) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        assertEquals(
            "control point (0.9,0.5) inside the polygon",
            1,
            s.count(
                XYShape.newPointQuery(
                    "shape", ShapeField.QueryRelation.INTERSECTS, new float[] {0.9f, 0.5f})));
        assertEquals(
            "point (1.8,2) strictly inside hole B",
            0,
            s.count(
                XYShape.newPointQuery(
                    "shape", ShapeField.QueryRelation.INTERSECTS, new float[] {1.8f, 2f})));
      }
    }
  }

  public void testLatLonShapePointInsideHoleDoesNotMatch() throws Exception {
    try (Directory dir = newDirectory()) {
      try (RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
        Document doc = new Document();
        for (Field f : LatLonShape.createIndexableFields("shape", latLon())) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        assertEquals(
            "control point (lat 0.5, lon 0.9) inside the polygon",
            1,
            s.count(
                LatLonShape.newPointQuery(
                    "shape", ShapeField.QueryRelation.INTERSECTS, new double[] {0.5, 0.9})));
        assertEquals(
            "point (lat 2, lon 1.8) strictly inside hole B",
            0,
            s.count(
                LatLonShape.newPointQuery(
                    "shape", ShapeField.QueryRelation.INTERSECTS, new double[] {2, 1.8})));
      }
    }
  }

  /** a box strictly inside hole B is disjoint from the shape */
  public void testXYShapeBoxInsideHoleIsDisjoint() throws Exception {
    try (Directory dir = newDirectory()) {
      try (RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
        Document doc = new Document();
        for (Field f : XYShape.createIndexableFields("shape", xy())) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        // [1.75, 1.85] x [1.9, 2.1] lies strictly inside hole B
        assertEquals(
            "DISJOINT with a box inside hole B",
            1,
            s.count(
                XYShape.newBoxQuery(
                    "shape", ShapeField.QueryRelation.DISJOINT, 1.75f, 1.85f, 1.9f, 2.1f)));
        assertEquals(
            "INTERSECTS with a box inside hole B",
            0,
            s.count(
                XYShape.newBoxQuery(
                    "shape", ShapeField.QueryRelation.INTERSECTS, 1.75f, 1.85f, 1.9f, 2.1f)));
      }
    }
  }
}
