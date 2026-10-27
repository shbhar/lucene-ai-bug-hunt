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
import org.apache.lucene.geo.Component2D;
import org.apache.lucene.geo.Line;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.XYGeometry;
import org.apache.lucene.geo.XYLine;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent checks (different geometry from the finder's repro). Only public API: XYGeometry.create
 * / Component2D default methods, XYShape / LatLonShape / LatLonPoint queries.
 */
public class TestHoleIntersects extends LuceneTestCase {

  // outer ring [0,10]^2 clockwise; one U-shaped (non-convex) hole, counter-clockwise.
  // The hole is the U: bottom bar [2,8]x[2,4], left arm [2,4]x[4,8], right arm [6,8]x[4,8].
  // The notch (4,6)x(4,8) is NOT hole, i.e. it is land of the query polygon.
  private static XYPolygon uHoleQuery() {
    XYPolygon u =
        new XYPolygon(
            new float[] {2, 8, 8, 6, 6, 4, 4, 2, 2}, new float[] {2, 2, 8, 8, 4, 4, 8, 8, 2});
    return new XYPolygon(new float[] {0, 0, 10, 10, 0}, new float[] {0, 10, 10, 0, 0}, u);
  }

  // outer [0,30]x[0,30] clockwise, three small square holes (counter-clockwise)
  private static XYPolygon threeHolesQuery() {
    XYPolygon h1 = new XYPolygon(new float[] {4, 6, 6, 4, 4}, new float[] {4, 4, 6, 6, 4});
    XYPolygon h2 = new XYPolygon(new float[] {24, 26, 26, 24, 24}, new float[] {4, 4, 6, 6, 4});
    XYPolygon h3 = new XYPolygon(new float[] {14, 16, 16, 14, 14}, new float[] {24, 24, 26, 26, 24});
    return new XYPolygon(
        new float[] {0, 0, 30, 30, 0}, new float[] {0, 30, 30, 0, 0}, h1, h2, h3);
  }

  public void testComponentNonConvexHoleLine() {
    Component2D c = XYGeometry.create(uHoleQuery());
    assertTrue("notch point (5,6) is land of the query polygon", c.contains(5, 6));
    assertFalse("(3,6) is in the hole", c.contains(3, 6));
    assertFalse("(7,6) is in the hole", c.contains(7, 6));
    // control: a segment wholly inside the left arm of the hole does not intersect
    assertFalse(c.intersectsLine(2.5, 6, 3.5, 6));
    // the segment (3,6)-(7,6) runs through the notch point (5,6)
    assertTrue("segment through the notch must intersect", c.intersectsLine(3, 6, 7, 6));
  }

  public void testComponentThreeHolesTriangle() {
    Component2D c = XYGeometry.create(threeHolesQuery());
    assertTrue("centroid (15,11.67) is land", c.contains(15, 11.67));
    // triangle with one vertex in each hole covers a big piece of land
    assertTrue(
        "triangle spanning three holes must intersect", c.intersectsTriangle(5, 5, 25, 5, 15, 25));
  }

  public void testXYShapeNonConvexHoleEndToEnd() throws IOException {
    XYPolygon q = uHoleQuery();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document d = new Document();
        for (Field f : XYShape.createIndexableFields("f", new XYLine(new float[] {3, 7}, new float[] {6, 6}))) {
          d.add(f);
        }
        w.addDocument(d);
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        assertEquals(
            "INTERSECTS must match the line through the notch",
            1,
            s.count(XYShape.newPolygonQuery("f", QueryRelation.INTERSECTS, q)));
        assertEquals(
            "DISJOINT must not match it",
            0,
            s.count(XYShape.newPolygonQuery("f", QueryRelation.DISJOINT, q)));
      }
    }
  }

  public void testLatLonThreeHolesPolygonDocAndPointControl() throws IOException {
    // lat = y, lon = x of threeHolesQuery()
    Polygon h1 = new Polygon(new double[] {4, 4, 6, 6, 4}, new double[] {4, 6, 6, 4, 4});
    Polygon h2 = new Polygon(new double[] {4, 4, 6, 6, 4}, new double[] {24, 26, 26, 24, 24});
    Polygon h3 = new Polygon(new double[] {24, 24, 26, 26, 24}, new double[] {14, 16, 16, 14, 14});
    Polygon q =
        new Polygon(new double[] {0, 30, 30, 0, 0}, new double[] {0, 0, 30, 30, 0}, h1, h2, h3);
    // indexed polygon: triangle (lon,lat) (5,5),(25,5),(15,25) given clockwise
    Polygon tri = new Polygon(new double[] {5, 25, 5, 5}, new double[] {5, 15, 25, 5});
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document d = new Document();
        for (Field f : LatLonShape.createIndexableFields("f", tri)) {
          d.add(f);
        }
        // control: point document at the land centroid, also indexed as LatLonPoint
        w.addDocument(d);
        Document p = new Document();
        for (Field f : LatLonShape.createIndexableFields("f", 11.67, 15)) {
          p.add(f);
        }
        p.add(new LatLonPoint("p", 11.67, 15));
        w.addDocument(p);
        // multi-part line doc whose only segment goes hole1 -> hole2
        Document l = new Document();
        for (Field f : LatLonShape.createIndexableFields("f", new Line(new double[] {5, 5}, new double[] {5, 25}))) {
          l.add(f);
        }
        w.addDocument(l);
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        assertEquals("point control via LatLonPoint", 1, s.count(LatLonPoint.newPolygonQuery("p", q)));
        assertEquals(
            "INTERSECTS must match triangle, point and line",
            3,
            s.count(LatLonShape.newPolygonQuery("f", QueryRelation.INTERSECTS, q)));
        assertEquals(
            "DISJOINT must match nothing",
            0,
            s.count(LatLonShape.newPolygonQuery("f", QueryRelation.DISJOINT, q)));
      }
    }
  }
}
