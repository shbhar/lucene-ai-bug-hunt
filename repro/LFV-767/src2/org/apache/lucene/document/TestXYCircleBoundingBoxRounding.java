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
import org.apache.lucene.geo.XYCircle;
import org.apache.lucene.geo.XYLine;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

/**
 * An XYShape distance query must find a line or polygon that passes through the circle, also when
 * the circle's centre has a large magnitude: the same configuration moved to the origin (an exact
 * translation) matches. Uses only public API.
 */
public class TestXYCircleBoundingBoxRounding extends LuceneTestCase {

  private static int count(Field[] fields, XYCircle circle, QueryRelation relation) throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      Document doc = new Document();
      for (Field f : fields) {
        doc.add(f);
      }
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = newSearcher(r);
        return s.count(XYShape.newDistanceQuery("shape", relation, circle));
      }
    }
  }

  /** squared distance from (px, py) to the segment (ax, ay)-(bx, by), in double */
  private static double dist2(double px, double py, double ax, double ay, double bx, double by) {
    double dx = bx - ax, dy = by - ay;
    double t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
    double cx = ax + t * dx - px, cy = ay + t * dy - py;
    return cx * cx + cy * cy;
  }

  private static void checkLine(float ox, float oy) throws Exception {
    // the segment (ox+1, oy) - (ox+2, oy+3) passes 1.2649 from the centre (ox, oy+1), radius 1.3
    float[] x = {ox + 1, ox + 2};
    float[] y = {oy, oy + 3};
    XYCircle circle = new XYCircle(ox, oy + 1, 1.3f);
    double d = Math.sqrt(dist2(ox, oy + 1, x[0], y[0], x[1], y[1]));
    assertTrue("segment passes inside the circle: " + d, d < 1.3f - 0.03);
    Field[] fields = XYShape.createIndexableFields("shape", new XYLine(x, y));
    assertEquals("INTERSECTS, origin (" + ox + ", " + oy + ")", 1, count(fields, circle, QueryRelation.INTERSECTS));
    assertEquals("DISJOINT, origin (" + ox + ", " + oy + ")", 0, count(fields, circle, QueryRelation.DISJOINT));
  }

  public void testLineNearOrigin() throws Exception {
    checkLine(0f, 0f); // control: passes
  }

  public void testLineTranslatedBy2pow23() throws Exception {
    checkLine(8388608f, 8388608f); // the same shapes moved by (2^23, 2^23): exact translation
  }

  /**
   * Web-Mercator-sized coordinates (metres): a road segment crossing a 10.9 m circle 0.31 m inside
   * its edge is not found.
   */
  public void testLineWebMercatorScale() throws Exception {
    float cx = 20_000_000f, cy = 5_000_000f;
    float[] x = {cx + 10, cx + 12};
    float[] y = {cy - 5, cy + 12};
    XYCircle circle = new XYCircle(cx, cy, 10.9f);
    double d = Math.sqrt(dist2(cx, cy, x[0], y[0], x[1], y[1]));
    assertTrue("segment passes inside the circle: " + d, d < 10.9f - 0.3);
    Field[] fields = XYShape.createIndexableFields("shape", new XYLine(x, y));
    assertEquals("INTERSECTS", 1, count(fields, circle, QueryRelation.INTERSECTS));
  }

  /** the same with a polygon: one edge of the triangle cuts the circle, all vertices are outside */
  public void testPolygonWebMercatorScale() throws Exception {
    float cx = 20_000_000f, cy = 5_000_000f;
    // clockwise triangle (Polygon notes); its left edge is the segment of the previous test
    XYPolygon triangle =
        new XYPolygon(
            new float[] {cx + 10, cx + 12, cx + 30, cx + 10},
            new float[] {cy - 5, cy + 12, cy - 5, cy - 5});
    XYCircle circle = new XYCircle(cx, cy, 10.9f);
    Field[] fields = XYShape.createIndexableFields("shape", triangle);
    assertEquals("INTERSECTS", 1, count(fields, circle, QueryRelation.INTERSECTS));
    assertEquals("DISJOINT", 0, count(fields, circle, QueryRelation.DISJOINT));
  }

  private static void checkContains(float ox, float oy) throws Exception {
    // clockwise triangle (0,0) (0,4) (5,2); the circle around (2,2) with radius 1.3 reaches y = 3.3
    // at x = 2, above the edge (0,4)-(5,2), which is at y = 3.2 there: the triangle does not contain it
    XYPolygon triangle =
        new XYPolygon(
            new float[] {ox, ox, ox + 5, ox}, new float[] {oy, oy + 4, oy + 2, oy});
    XYCircle circle = new XYCircle(ox + 2, oy + 2, 1.3f);
    Field[] fields = XYShape.createIndexableFields("shape", triangle);
    assertEquals("CONTAINS, origin (" + ox + ", " + oy + ")", 0, count(fields, circle, QueryRelation.CONTAINS));
  }

  public void testContainsNearOrigin() throws Exception {
    checkContains(0f, 0f); // control: passes
  }

  public void testContainsTranslatedBy2pow23() throws Exception {
    checkContains(8388608f, 8388608f);
  }
}
