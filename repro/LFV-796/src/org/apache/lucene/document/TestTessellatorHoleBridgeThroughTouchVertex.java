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
import java.util.List;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A valid polygon with two holes. Hole A touches the outer ring at the outer vertex V = (2,3),
 * where the outer edge (2,5)-(2,3) and hole A's edge (2,3)-(2,1) lie on one line; hole B touches
 * nothing, and its left-most vertex (3,3) is level with V. The Tessellator javadoc allows holes
 * that touch at one vertex, so a point strictly inside hole A must not match the polygon, whatever
 * vertex the outer ring starts at.
 *
 * <p>For one start vertex of the outer ring (on main: (2,3); on 9.12.2 and 10.3.2: (2,5)) the
 * polygon is indexed with triangles that cover hole A: INTERSECTS matches the point in the hole and
 * DISJOINT does not.
 */
public class TestTessellatorHoleBridgeThroughTouchVertex extends LuceneTestCase {

  /** outer ring, clockwise */
  private static final int[][] OUTER = {{4, 4}, {5, 0}, {0, 0}, {2, 3}, {2, 5}};

  /** hole A (counter-clockwise), touches the outer ring at V = (2,3) */
  private static final int[][] HOLE_A = {{2, 1}, {2, 3}, {1, 1}};

  /** hole B (counter-clockwise), strictly inside; its left-most vertex (3,3) is level with V */
  private static final int[][] HOLE_B = {{3, 3}, {4, 2}, {4, 3}};

  /** a point strictly inside hole A */
  private static final float[] IN_HOLE_A = {1.75f, 1.5f};

  /** the ring rotated to start at vertex {@code start}, closed, as {xs, ys} */
  private static float[][] ring(int[][] v, int start) {
    float[] x = new float[v.length + 1], y = new float[v.length + 1];
    for (int i = 0; i <= v.length; i++) {
      x[i] = v[(start + i) % v.length][0];
      y[i] = v[(start + i) % v.length][1];
    }
    return new float[][] {x, y};
  }

  private static double[] d(float[] f) {
    double[] r = new double[f.length];
    for (int i = 0; i < f.length; i++) r[i] = f[i];
    return r;
  }

  private static XYPolygon xy(int start) {
    float[][] o = ring(OUTER, start), ha = ring(HOLE_A, 0), hb = ring(HOLE_B, 0);
    return new XYPolygon(o[0], o[1], new XYPolygon(ha[0], ha[1]), new XYPolygon(hb[0], hb[1]));
  }

  /** lat = y, lon = x (degrees) */
  private static Polygon latLon(int start) {
    float[][] o = ring(OUTER, start), ha = ring(HOLE_A, 0), hb = ring(HOLE_B, 0);
    return new Polygon(
        d(o[1]), d(o[0]), new Polygon(d(ha[1]), d(ha[0])), new Polygon(d(hb[1]), d(hb[0])));
  }

  private int count(Field[] fields, Query q) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : fields) doc.add(f);
        w.addDocument(doc);
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        return new IndexSearcher(r).count(q);
      }
    }
  }

  private void check(boolean latLon) throws Exception {
    float[] p = IN_HOLE_A;
    List<String> wrong = new ArrayList<>();
    for (int start = 0; start < OUTER.length; start++) {
      String at = "outer ring starting at (" + OUTER[start][0] + "," + OUTER[start][1] + "): ";
      Field[] f;
      try {
        f =
            latLon
                ? LatLonShape.createIndexableFields("s", latLon(start))
                : XYShape.createIndexableFields("s", xy(start));
      } catch (IllegalArgumentException e) {
        // a valid polygon (see the class javadoc) must be accepted
        wrong.add(at + "rejected: " + e.getMessage());
        continue;
      }
      Query intersects, disjoint;
      if (latLon) {
        double[] q = {p[1], p[0]};
        intersects = LatLonShape.newPointQuery("s", QueryRelation.INTERSECTS, q);
        disjoint = LatLonShape.newPointQuery("s", QueryRelation.DISJOINT, q);
      } else {
        intersects = XYShape.newPointQuery("s", QueryRelation.INTERSECTS, p);
        disjoint = XYShape.newPointQuery("s", QueryRelation.DISJOINT, p);
      }
      int i = count(f, intersects), dj = count(f, disjoint);
      if (i != 0 || dj != 1) {
        wrong.add(at + "INTERSECTS=" + i + " DISJOINT=" + dj);
      }
    }
    assertTrue(
        "point (" + p[0] + "," + p[1] + ") strictly inside hole A: " + wrong, wrong.isEmpty());
  }

  public void testXYShape() throws Exception {
    check(false);
  }

  public void testLatLonShape() throws Exception {
    check(true);
  }
}
