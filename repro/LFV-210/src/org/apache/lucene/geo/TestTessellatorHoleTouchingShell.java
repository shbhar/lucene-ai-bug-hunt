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
package org.apache.lucene.geo;

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LatLonShape;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.document.XYShape;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A hole whose left-most vertex touches the outer ring is bridged to the wrong outer vertex: the
 * tessellation then covers the hole (points inside the hole match INTERSECTS queries) or the
 * polygon is rejected.
 *
 * <p>Shell (2,0) (1,1) (0,2) (3,3); the shell vertex (1,1) lies on the straight line from (2,0) to
 * (0,2). Hole (1,1) (1,2) (2,2) shares exactly that one vertex with the shell, which the Tessellator
 * javadoc allows ("Holes may only touch at one vertex"). The point (1.25, 1.75) is strictly inside
 * the hole, so it is not part of the polygon; (2.2, 1.0) is inside the polygon.
 */
public class TestTessellatorHoleTouchingShell extends LuceneTestCase {

  private static final float[] SHELL_X = {2, 1, 0, 3, 2};
  private static final float[] SHELL_Y = {0, 1, 2, 3, 0};
  private static final float[] HOLE_X = {1, 1, 2, 1};
  private static final float[] HOLE_Y = {1, 2, 2, 1};

  private static double twiceArea(double[] x, double[] y) {
    double a = 0;
    for (int i = 0; i + 1 < x.length; i++) {
      a += x[i] * y[i + 1] - x[i + 1] * y[i];
    }
    return Math.abs(a);
  }

  private static double[] toDouble(float[] f) {
    double[] d = new double[f.length];
    for (int i = 0; i < f.length; i++) d[i] = f[i];
    return d;
  }

  /** Tessellator level: the triangles must cover the polygon minus the hole (exact for these ints). */
  public void testTriangleAreaEqualsPolygonArea() {
    XYPolygon polygon = new XYPolygon(SHELL_X, SHELL_Y, new XYPolygon(HOLE_X, HOLE_Y));
    List<Tessellator.Triangle> triangles = Tessellator.tessellate(polygon, true);
    double sum = 0;
    for (Tessellator.Triangle t : triangles) {
      sum +=
          Math.abs(
              (t.getX(1) - t.getX(0)) * (t.getY(2) - t.getY(0))
                  - (t.getX(2) - t.getX(0)) * (t.getY(1) - t.getY(0)));
    }
    double expected =
        twiceArea(toDouble(SHELL_X), toDouble(SHELL_Y))
            - twiceArea(toDouble(HOLE_X), toDouble(HOLE_Y));
    // 2 * area: shell 8, hole 1
    assertEquals(7.0, expected, 0.0);
    assertEquals("triangles: " + triangles, expected, sum, 0.0);
  }

  private static int count(Field[] fields, Query query) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : fields) doc.add(f);
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        return new IndexSearcher(reader).count(query);
      }
    }
  }

  public void testXYShapePointInHoleDoesNotMatch() throws Exception {
    XYPolygon polygon = new XYPolygon(SHELL_X, SHELL_Y, new XYPolygon(HOLE_X, HOLE_Y));
    Field[] fields = XYShape.createIndexableFields("shape", polygon);
    // control: a point of the polygon matches
    assertEquals(
        1,
        count(fields, XYShape.newPointQuery("shape", QueryRelation.INTERSECTS, new float[] {2.2f, 1f})));
    // a point strictly inside the hole must not match
    assertEquals(
        0,
        count(
            fields,
            XYShape.newPointQuery("shape", QueryRelation.INTERSECTS, new float[] {1.25f, 1.75f})));
  }

  public void testLatLonShapePointInHoleDoesNotMatch() throws Exception {
    // same rings in degrees: x = longitude, y = latitude
    Polygon hole = new Polygon(toDouble(HOLE_Y), toDouble(HOLE_X));
    Polygon polygon = new Polygon(toDouble(SHELL_Y), toDouble(SHELL_X), hole);
    Field[] fields = LatLonShape.createIndexableFields("shape", polygon);
    assertEquals(
        1,
        count(fields, LatLonShape.newPointQuery("shape", QueryRelation.INTERSECTS, new double[] {1.0, 2.2})));
    assertEquals(
        0,
        count(
            fields,
            LatLonShape.newPointQuery("shape", QueryRelation.INTERSECTS, new double[] {1.75, 1.25})));
  }

  /**
   * Same root cause, other symptom: shell (2,0) (0,2) (3,3) and hole (1,1) (2,1) (1,2); the hole
   * vertex (1,1) touches the shell edge (2,0)-(0,2) at one point. The valid polygon is rejected.
   */
  public void testValidPolygonIsNotRejected() {
    XYPolygon hole = new XYPolygon(new float[] {1, 2, 1, 1}, new float[] {1, 1, 2, 1});
    XYPolygon polygon = new XYPolygon(new float[] {2, 0, 3, 2}, new float[] {0, 2, 3, 0}, hole);
    List<Tessellator.Triangle> triangles = Tessellator.tessellate(polygon, false);
    assertFalse(triangles.isEmpty());
  }
}
