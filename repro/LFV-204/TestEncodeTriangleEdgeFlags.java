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

import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * ShapeField.encodeTriangle re-orients a clockwise triangle by swapping its 2nd and 3rd vertex, but
 * then gives the new first edge the flag of the old 2nd edge instead of the old 3rd edge. The
 * decoded "edge belongs to the original shape" flags are then wrong, and CONTAINS queries, which
 * rely on those flags, return wrong results.
 *
 * <p>For XYShape the encoded coordinates are sortable float bits, so a triangle that is
 * counter-clockwise in float space can be clockwise in encoded space: small convex polygons with
 * integer coordinates are enough.
 */
public class TestEncodeTriangleEdgeFlags extends LuceneTestCase {

  /** Encoding level: decoded edge flags must be the flags of the same geometric edges. */
  public void testClockwiseTriangleKeepsEdgeFlags() {
    // clockwise triangle p0 -> p1 -> p2; only the edge p1-p2 belongs to the shape
    int p0x = 0, p0y = 0, p1x = 0, p1y = 10, p2x = 10, p2y = 0;
    byte[] bytes = new byte[7 * ShapeField.BYTES];
    // argument order is (aY, aX, ab, bY, bX, bc, cY, cX, ca)
    ShapeField.encodeTriangle(bytes, p0y, p0x, false, p1y, p1x, true, p2y, p2x, false);
    ShapeField.DecodedTriangle d = new ShapeField.DecodedTriangle();
    ShapeField.decodeTriangle(bytes, d);
    assertEquals(ShapeField.DecodedTriangle.TYPE.TRIANGLE, d.type);
    int[][] v = {{d.aX, d.aY}, {d.bX, d.bY}, {d.cX, d.cY}};
    boolean[] flag = {d.ab, d.bc, d.ca};
    int flagged = 0;
    for (int e = 0; e < 3; e++) {
      int[] u = v[e], w = v[(e + 1) % 3];
      boolean isP1P2 =
          (u[0] == p1x && u[1] == p1y && w[0] == p2x && w[1] == p2y)
              || (u[0] == p2x && u[1] == p2y && w[0] == p1x && w[1] == p1y);
      assertEquals("decoded " + d + ", edge " + e, isP1P2, flag[e]);
      if (flag[e]) flagged++;
    }
    assertEquals("decoded " + d, 1, flagged);
  }

  private static int containsCount(
      XYPolygon polygon, float minX, float maxX, float minY, float maxY) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : XYShape.createIndexableFields("shape", polygon)) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        // the document is there and intersects the box
        assertEquals(
            1,
            searcher.count(
                XYShape.newBoxQuery("shape", QueryRelation.INTERSECTS, minX, maxX, minY, maxY)));
        return searcher.count(
            XYShape.newBoxQuery("shape", QueryRelation.CONTAINS, minX, maxX, minY, maxY));
      }
    }
  }

  /**
   * Convex quadrilateral (6,0) (-1,7) (-8,8) (0,1). The box is centred on the boundary edge
   * (-1,7)-(-8,8), so half of it lies outside the polygon: the polygon does not contain it.
   */
  public void testContainsFalsePositive() throws Exception {
    XYPolygon polygon = new XYPolygon(new float[] {6, -1, -8, 0, 6}, new float[] {0, 7, 8, 1, 0});
    // corner (-4.75, 7.75) is above the edge (y = 7.5357 at x = -4.75), i.e. outside the polygon
    assertEquals(0, containsCount(polygon, -4.75f, -4.25f, 7.25f, 7.75f));
  }

  /**
   * Convex quadrilateral (3,3) (6,0) (8,5) (1,8). The box lies strictly inside the polygon (it is
   * centred on the tessellation diagonal (6,0)-(1,8), at least 0.5 away from every polygon edge):
   * the polygon contains it.
   */
  public void testContainsFalseNegative() throws Exception {
    XYPolygon polygon = new XYPolygon(new float[] {3, 6, 8, 1, 3}, new float[] {3, 0, 5, 8, 3});
    assertEquals(1, containsCount(polygon, 3.25f, 3.75f, 3.75f, 4.25f));
  }

  /** plain crossing-number test (no Lucene code), used only far from the polygon boundary */
  private static boolean insideRing(double[] lats, double[] lons, double lat, double lon) {
    boolean in = false;
    for (int i = 0, j = lats.length - 1; i < lats.length; j = i++) {
      if ((lats[i] > lat) != (lats[j] > lat)
          && lon < (lons[j] - lons[i]) * (lat - lats[i]) / (lats[j] - lats[i]) + lons[i]) {
        in = !in;
      }
    }
    return in;
  }

  /**
   * LatLonShape: an 8-vertex polygon whose north-east edge is densified with nearly collinear
   * vertices (a common shape for boundaries that follow a long straight line). Quantization makes
   * one of the thin triangles along that edge clockwise in encoded space, so it goes through the
   * faulty branch. The query box straddles the densified edge: its north-east corner is about 0.007
   * degrees outside the polygon.
   */
  public void testLatLonContainsFalsePositive() throws Exception {
    double[] lats = {
      40.02153729379782,
      40.03034715760576,
      40.0391570421736,
      40.07439675121492,
      40.08320660860139,
      40.09201651378586,
      40.64511347064427,
      38.46316244809368,
      40.02153729379782
    };
    double[] lons = {
      -9.332087396969534, -9.383153105764327, -9.434218835319008, -9.638481924307744,
      -9.689547626681062, -9.74061337685239, -10.861355963069201, -9.477263958618547,
      -9.332087396969534
    };
    double minLat = 40.08220950141549, maxLat = 40.09301352314651;
    double minLon = -9.725884599611163, maxLon = -9.704276639968157;
    // the box is not inside the polygon: its north-east corner is outside, its south-west inside
    assertFalse(insideRing(lats, lons, maxLat, maxLon));
    assertTrue(insideRing(lats, lons, minLat, minLon));
    org.apache.lucene.geo.Polygon polygon = new org.apache.lucene.geo.Polygon(lats, lons);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : LatLonShape.createIndexableFields("shape", polygon)) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        assertEquals(
            1,
            searcher.count(
                LatLonShape.newBoxQuery(
                    "shape", QueryRelation.INTERSECTS, minLat, maxLat, minLon, maxLon)));
        assertEquals(
            0,
            searcher.count(
                LatLonShape.newBoxQuery(
                    "shape", QueryRelation.CONTAINS, minLat, maxLat, minLon, maxLon)));
      }
    }
  }
}
