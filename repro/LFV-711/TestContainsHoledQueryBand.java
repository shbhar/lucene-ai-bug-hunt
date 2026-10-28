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

import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.Component2D;
import org.apache.lucene.geo.LatLonGeometry;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.XYGeometry;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent reproducer: CONTAINS with a query polygon that has two holes.
 *
 * <p>Query Q: the square [0,20]^2 (clockwise) with two counter-clockwise holes H1 = [2,6]^2 and
 * H2 = [14,18]^2. The point (10,10) is in Q.
 *
 * <p>Indexed shapes, all based on the square [-5,25]^2 (clockwise):
 *
 * <ul>
 *   <li>"band": with a thin diagonal hole (3,4),(4,3),(17,16),(16,17). Its two ends lie in H1 and
 *       H2 and its middle removes (10,10), so it does NOT contain Q.
 *   <li>"solid": no hole, contains Q.
 *   <li>"inHole": hole [3,5]^2, which lies wholly inside H1, so it still contains Q.
 *   <li>"vertexInLand": hole [9,11]^2 whose corners lie in Q's land, does NOT contain Q.
 * </ul>
 *
 * Expected CONTAINS hits: {inHole, solid}.
 */
public class TestContainsHoledQueryBand extends LuceneTestCase {

  private static final String F = "shape";

  // ---- geometry in (x, y); for LatLon: lon = x, lat = y ----
  private static final double[][] Q_OUTER = {
    {0, 0, 20, 20, 0}, // x
    {0, 20, 20, 0, 0} // y   clockwise
  };
  private static final double[][] Q_H1 = {
    {2, 6, 6, 2, 2}, {2, 2, 6, 6, 2} // counter-clockwise
  };
  private static final double[][] Q_H2 = {
    {14, 18, 18, 14, 14}, {14, 14, 18, 18, 14}
  };
  private static final double[][] D_OUTER = {
    {-5, -5, 25, 25, -5}, {-5, 25, 25, -5, -5} // clockwise
  };
  private static final double[][] D_BAND_HOLE = {
    {3, 4, 17, 16, 3}, {4, 3, 16, 17, 4} // counter-clockwise, ends inside H1 and H2
  };
  private static final double[][] D_INHOLE_HOLE = {
    {3, 5, 5, 3, 3}, {3, 3, 5, 5, 3}
  };
  private static final double[][] D_LAND_HOLE = {
    {9, 11, 11, 9, 9}, {9, 9, 11, 11, 9}
  };

  private static float[] f(double[] a) {
    float[] r = new float[a.length];
    for (int i = 0; i < a.length; i++) {
      r[i] = (float) a[i];
    }
    return r;
  }

  private static XYPolygon xy(double[][] ring, XYPolygon... holes) {
    return new XYPolygon(f(ring[0]), f(ring[1]), holes);
  }

  private static Polygon ll(double[][] ring, Polygon... holes) {
    // Polygon(lats, lons): lat = y, lon = x
    return new Polygon(ring[1].clone(), ring[0].clone(), holes);
  }

  private static Set<String> hits(IndexSearcher s, Query q) throws Exception {
    Set<String> out = new TreeSet<>();
    for (ScoreDoc sd : s.search(q, 100).scoreDocs) {
      out.add(s.storedFields().document(sd.doc).get("id"));
    }
    return out;
  }

  private static Document doc(String id, Field[] fields) {
    Document d = new Document();
    d.add(new StoredField("id", id));
    for (Field fl : fields) {
      d.add(fl);
    }
    return d;
  }

  public void testXYShapeContainsHoledQuery() throws Exception {
    XYPolygon query = xy(Q_OUTER, xy(Q_H1), xy(Q_H2));
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        w.addDocument(doc("band", XYShape.createIndexableFields(F, xy(D_OUTER, xy(D_BAND_HOLE)), true)));
        w.addDocument(doc("solid", XYShape.createIndexableFields(F, xy(D_OUTER), true)));
        w.addDocument(
            doc("inHole", XYShape.createIndexableFields(F, xy(D_OUTER, xy(D_INHOLE_HOLE)), true)));
        w.addDocument(
            doc("vertexInLand", XYShape.createIndexableFields(F, xy(D_OUTER, xy(D_LAND_HOLE)), true)));
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        // Sanity: which documents cover the query point (10,10), which lies in Q.
        assertEquals(
            "(10,10) is in Q; band and vertexInLand do not cover it",
            Set.of("inHole", "solid"),
            hits(s, XYShape.newPointQuery(F, QueryRelation.INTERSECTS, new float[] {10f, 10f})));
        // The query polygon really contains (10,10)
        assertTrue(XYGeometry.create(query).contains(10, 10));
        assertEquals(
            "CONTAINS: only shapes that contain the whole query polygon",
            new TreeSet<>(Set.of("inHole", "solid")),
            hits(s, XYShape.newPolygonQuery(F, QueryRelation.CONTAINS, query)));
      }
    }
  }

  public void testLatLonShapeContainsHoledQuery() throws Exception {
    Polygon query = ll(Q_OUTER, ll(Q_H1), ll(Q_H2));
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        w.addDocument(
            doc("band", LatLonShape.createIndexableFields(F, ll(D_OUTER, ll(D_BAND_HOLE)), true)));
        w.addDocument(doc("solid", LatLonShape.createIndexableFields(F, ll(D_OUTER), true)));
        w.addDocument(
            doc(
                "inHole",
                LatLonShape.createIndexableFields(F, ll(D_OUTER, ll(D_INHOLE_HOLE)), true)));
        w.addDocument(
            doc(
                "vertexInLand",
                LatLonShape.createIndexableFields(F, ll(D_OUTER, ll(D_LAND_HOLE)), true)));
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        assertEquals(
            "(lat 10, lon 10) is in Q; band and vertexInLand do not cover it",
            Set.of("inHole", "solid"),
            hits(s, LatLonShape.newPointQuery(F, QueryRelation.INTERSECTS, new double[] {10, 10})));
        assertTrue(LatLonGeometry.create(query).contains(10, 10));
        assertEquals(
            "CONTAINS: only shapes that contain the whole query polygon",
            new TreeSet<>(Set.of("inHole", "solid")),
            hits(s, LatLonShape.newPolygonQuery(F, QueryRelation.CONTAINS, query)));
      }
    }
  }

  /**
   * Component level (Component2D is public but @lucene.internal). Triangle a=(-5,-5), b=(4,3),
   * c=(17,16) with bc an edge of the original shape (the band hole's lower edge, which runs through
   * Q's land from H1 to H2). WithinRelation javadoc: NOTWITHIN when "The query shape intersects an
   * edge that does belong to the original shape".
   */
  public void testComponentWithinTriangleShapeEdgeThroughLand() {
    Component2D q = XYGeometry.create(xy(Q_OUTER, xy(Q_H1), xy(Q_H2)));
    // the shape edge (4,3)-(17,16) passes through (10,9), which is in Q
    assertTrue(q.contains(10, 9));
    assertFalse(q.contains(4, 3));
    assertFalse(q.contains(17, 16));
    Component2D.WithinRelation rel =
        q.withinTriangle(-5, 17, -5, 16, -5, -5, false, 4, 3, true, 17, 16, false);
    assertEquals(Component2D.WithinRelation.NOTWITHIN, rel);
  }
}
