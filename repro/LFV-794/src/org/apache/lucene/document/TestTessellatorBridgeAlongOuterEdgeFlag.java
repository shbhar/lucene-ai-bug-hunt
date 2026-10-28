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
 * A valid polygon (0,0) (7,12) (12,0) (5,0) (2,0) with two triangular holes; each hole touches the
 * outer ring at exactly one vertex, (2,0) and (5,0) (vertices of both rings, as the Tessellator
 * javadoc allows: "Holes may only touch at one vertex"), and the holes do not touch each other. The
 * box x in [3,4], y in [-0.5,0.3] sticks out below the outer edge y = 0, so the polygon does not
 * contain it and a CONTAINS query for it must not match. The box x in [3,4], y in [0.05,0.3] lies
 * inside the polygon (control). The second polygon is the same without the outer vertices (2,0) and
 * (5,0) (the holes then touch the outer edge (12,0)-(0,0) in its interior).
 *
 * <p>On main the outer-edge piece (2,0)-(5,0) is indexed as an internal edge (its "from polygon"
 * flag is false), so CONTAINS matches the box that sticks out through it.
 */
public class TestTessellatorBridgeAlongOuterEdgeFlag extends LuceneTestCase {

  /** outer ring, clockwise, with vertices at the two touch points */
  private static final int[][] OUTER = {{0, 0}, {7, 12}, {12, 0}, {5, 0}, {2, 0}};

  /** the same outer ring without the vertices (2,0) and (5,0) */
  private static final int[][] OUTER_NO_TOUCH_VERTICES = {{0, 0}, {7, 12}, {12, 0}};

  /** holes, counter-clockwise; each touches the outer edge (12,0)-(0,0) at one vertex */
  private static final int[][][] HOLES = {{{2, 0}, {5, 1}, {3, 3}}, {{5, 0}, {8, 1}, {6, 3}}};

  /** {minX, maxX, minY, maxY}: crosses y = 0 between the two touch points */
  private static final float[] OUTSIDE_BOX = {3f, 4f, -0.5f, 0.3f};

  /** {minX, maxX, minY, maxY}: inside the polygon */
  private static final float[] INSIDE_BOX = {3f, 4f, 0.05f, 0.3f};

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

  private static XYPolygon xy(int[][] outer, int start, int first) {
    float[][] o = ring(outer, start), h0 = ring(HOLES[first], 0), h1 = ring(HOLES[1 - first], 0);
    return new XYPolygon(o[0], o[1], new XYPolygon(h0[0], h0[1]), new XYPolygon(h1[0], h1[1]));
  }

  /** lat = y, lon = x (degrees) */
  private static Polygon latLon(int[][] outer, int start, int first) {
    float[][] o = ring(outer, start), h0 = ring(HOLES[first], 0), h1 = ring(HOLES[1 - first], 0);
    return new Polygon(
        d(o[1]), d(o[0]), new Polygon(d(h0[1]), d(h0[0])), new Polygon(d(h1[1]), d(h1[0])));
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

  private Query contains(boolean latLon, float[] b) {
    return latLon
        ? LatLonShape.newBoxQuery("s", QueryRelation.CONTAINS, b[2], b[3], b[0], b[1])
        : XYShape.newBoxQuery("s", QueryRelation.CONTAINS, b[0], b[1], b[2], b[3]);
  }

  private void check(int[][] outer, boolean latLon) throws Exception {
    List<String> wrong = new ArrayList<>();
    for (int start = 0; start < outer.length; start++) {
      for (int first = 0; first < 2; first++) {
        String at =
            "outer ring starting at ("
                + outer[start][0]
                + ","
                + outer[start][1]
                + "), hole order "
                + (first == 0 ? "AB" : "BA")
                + ": ";
        Field[] f =
            latLon
                ? LatLonShape.createIndexableFields("s", latLon(outer, start, first))
                : XYShape.createIndexableFields("s", xy(outer, start, first));
        int out = count(f, contains(latLon, OUTSIDE_BOX));
        int in = count(f, contains(latLon, INSIDE_BOX));
        if (out != 0 || in != 1) {
          wrong.add(at + "CONTAINS box sticking out=" + out + " CONTAINS box inside=" + in);
        }
      }
    }
    assertTrue(wrong.toString(), wrong.isEmpty());
  }

  public void testXYShape() throws Exception {
    check(OUTER, false);
  }

  public void testLatLonShape() throws Exception {
    check(OUTER, true);
  }

  public void testXYShapeHolesTouchingEdgeInterior() throws Exception {
    check(OUTER_NO_TOUCH_VERTICES, false);
  }

  public void testLatLonShapeHolesTouchingEdgeInterior() throws Exception {
    check(OUTER_NO_TOUCH_VERTICES, true);
  }
}
