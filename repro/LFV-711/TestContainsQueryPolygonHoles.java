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

import java.io.IOException;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * CONTAINS with a query polygon that has holes. Rings are written as {x[], y[]}; for LatLonShape x
 * is the longitude and y the latitude. Outer rings are clockwise, holes counter-clockwise.
 *
 * <p>Query: the square [0,10] x [0,10] minus either the two holes [1,3] x [4,6] and [7,9] x [4,6],
 * or one U-shaped hole with the arms [1,3] x [3,6] and [7,9] x [3,6]. In both, the point (5,5)
 * between the holes belongs to the query.
 *
 * <p>Indexed: the square [-1,11] x [-1,11] with
 *
 * <ul>
 *   <li>doc 0: a hole [2,8] x [4.5,5.5]. Its corners lie in the query's holes and it removes (5,5),
 *       so doc 0 does NOT contain the query;
 *   <li>doc 1: no hole, contains the query;
 *   <li>doc 2: a hole [1.5,2.5] x [4.5,5.5] that lies inside a query hole, contains the query.
 * </ul>
 */
public class TestContainsQueryPolygonHoles extends LuceneTestCase {

  private static final float[][] SQUARE = {{0, 0, 10, 10, 0}, {0, 10, 10, 0, 0}};
  private static final float[][] HOLE_A = {{1, 3, 3, 1, 1}, {4, 4, 6, 6, 4}};
  private static final float[][] HOLE_B = {{7, 9, 9, 7, 7}, {4, 4, 6, 6, 4}};
  private static final float[][] HOLE_U = {
    {1, 9, 9, 7, 7, 3, 3, 1, 1}, {3, 3, 6, 6, 4, 4, 6, 6, 3}
  };
  private static final float[][] SUBSET = {{4, 4, 6, 6, 4}, {4, 6, 6, 4, 4}}; // [4,6] x [4,6]
  private static final float[][] DOC = {{-1, -1, 11, 11, -1}, {-1, 11, 11, -1, -1}};
  private static final float[][] DOC0_HOLE = {{2, 8, 8, 2, 2}, {4.5f, 4.5f, 5.5f, 5.5f, 4.5f}};
  private static final float[][] DOC2_HOLE = {
    {1.5f, 2.5f, 2.5f, 1.5f, 1.5f}, {4.5f, 4.5f, 5.5f, 5.5f, 4.5f}
  };

  public void testXYShapeTwoQueryHoles() throws IOException {
    check(false, SQUARE, HOLE_A, HOLE_B);
  }

  public void testXYShapeOneUShapedQueryHole() throws IOException {
    check(false, SQUARE, HOLE_U);
  }

  public void testLatLonShapeTwoQueryHoles() throws IOException {
    check(true, SQUARE, HOLE_A, HOLE_B);
  }

  public void testLatLonShapeOneUShapedQueryHole() throws IOException {
    check(true, SQUARE, HOLE_U);
  }

  private void check(boolean latLon, float[][] outer, float[][]... holes) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        add(w, latLon, DOC, DOC0_HOLE);
        add(w, latLon, DOC);
        add(w, latLon, DOC, DOC2_HOLE);
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Query point =
            latLon
                ? LatLonShape.newPointQuery("f", QueryRelation.INTERSECTS, new double[] {5, 5})
                : XYShape.newPointQuery("f", QueryRelation.INTERSECTS, new float[] {5, 5});
        assertEquals("docs that cover (5,5)", "[1, 2]", hits(s, point));
        assertEquals("CONTAINS [4,6] x [4,6]", "[1, 2]", hits(s, contains(latLon, SUBSET)));
        assertEquals(
            "CONTAINS the holed query, which includes [4,6] x [4,6]",
            "[1, 2]",
            hits(s, contains(latLon, outer, holes)));
      }
    }
  }

  private static Query contains(boolean latLon, float[][] outer, float[][]... holes) {
    return latLon
        ? LatLonShape.newPolygonQuery("f", QueryRelation.CONTAINS, latLon(outer, holes))
        : XYShape.newPolygonQuery("f", QueryRelation.CONTAINS, xy(outer, holes));
  }

  private static void add(IndexWriter w, boolean latLon, float[][] outer, float[][]... holes)
      throws IOException {
    Field[] fields =
        latLon
            ? LatLonShape.createIndexableFields("f", latLon(outer, holes), true)
            : XYShape.createIndexableFields("f", xy(outer, holes), true);
    Document d = new Document();
    for (Field f : fields) {
      d.add(f);
    }
    w.addDocument(d);
  }

  private static XYPolygon xy(float[][] outer, float[][]... holes) {
    XYPolygon[] h = new XYPolygon[holes.length];
    for (int i = 0; i < holes.length; i++) {
      h[i] = new XYPolygon(holes[i][0], holes[i][1]);
    }
    return new XYPolygon(outer[0], outer[1], h);
  }

  private static Polygon latLon(float[][] outer, float[][]... holes) {
    Polygon[] h = new Polygon[holes.length];
    for (int i = 0; i < holes.length; i++) {
      h[i] = new Polygon(toDouble(holes[i][1]), toDouble(holes[i][0]));
    }
    return new Polygon(toDouble(outer[1]), toDouble(outer[0]), h);
  }

  private static double[] toDouble(float[] a) {
    double[] d = new double[a.length];
    for (int i = 0; i < a.length; i++) {
      d[i] = a[i];
    }
    return d;
  }

  private static String hits(IndexSearcher s, Query q) throws IOException {
    StringBuilder b = new StringBuilder("[");
    for (ScoreDoc sd : s.search(q, 10, new Sort(SortField.FIELD_DOC)).scoreDocs) {
      if (b.length() > 1) {
        b.append(", ");
      }
      b.append(sd.doc);
    }
    return b.append("]").toString();
  }
}
