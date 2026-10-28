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
 * A valid quadrilateral with two small triangular holes that are strictly inside it and touch
 * nothing (no shared vertices, no vertex on another ring). The Tessellator javadoc requires only
 * "no self intersections", "holes must be inside the polygon" and "polygon must have an area", so
 * the polygon must be indexed whatever vertex its outer ring starts at.
 *
 * <p>The outer vertex (1,1) is reflex and its edge from (0,0) points at hole B's left-most vertex
 * (2,2), so hole B's bridge (2,2)-(1,1) continues that outer edge. On main the polygon is rejected
 * with "Unable to Tessellate shape" when the outer ring starts at (5,1) or (0,0).
 */
public class TestTessellatorDisjointHolesCollinearBridge extends LuceneTestCase {

  /** outer ring, clockwise */
  private static final int[][] OUTER = {{4, 5}, {5, 1}, {0, 0}, {1, 1}};

  /** hole A (counter-clockwise) */
  private static final int[][] HOLE_A = {{3, 2}, {3, 1}, {4, 1}};

  /** hole B (counter-clockwise); its left-most vertex (2,2) lies on the line through (0,0), (1,1) */
  private static final int[][] HOLE_B = {{2, 2}, {4, 3}, {3, 3}};

  private static final float[] IN_HOLE_A = {3.25f, 1.25f};
  private static final float[] IN_HOLE_B = {3f, 2.75f};
  private static final float[] IN_POLYGON = {2f, 1f};

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

  private Query point(boolean latLon, float[] p) {
    return latLon
        ? LatLonShape.newPointQuery("s", QueryRelation.INTERSECTS, new double[] {p[1], p[0]})
        : XYShape.newPointQuery("s", QueryRelation.INTERSECTS, p);
  }

  private void check(boolean latLon) throws Exception {
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
        wrong.add(at + "rejected: " + e.getMessage());
        continue;
      }
      int inA = count(f, point(latLon, IN_HOLE_A));
      int inB = count(f, point(latLon, IN_HOLE_B));
      int body = count(f, point(latLon, IN_POLYGON));
      if (inA != 0 || inB != 0 || body != 1) {
        wrong.add(at + "INTERSECTS hole A=" + inA + " hole B=" + inB + " body=" + body);
      }
    }
    assertTrue("valid polygon with disjoint holes: " + wrong, wrong.isEmpty());
  }

  public void testXYShape() throws Exception {
    check(false);
  }

  public void testLatLonShape() throws Exception {
    check(true);
  }
}
