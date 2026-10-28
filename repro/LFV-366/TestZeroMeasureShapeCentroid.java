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
package org.apache.lucene.verify.shapedv;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.LatLonShape;
import org.apache.lucene.document.LatLonShapeDocValuesField;
import org.apache.lucene.document.XYShape;
import org.apache.lucene.document.XYShapeDocValuesField;
import org.apache.lucene.geo.GeoEncodingUtils;
import org.apache.lucene.geo.Line;
import org.apache.lucene.geo.Point;
import org.apache.lucene.geo.Rectangle;
import org.apache.lucene.geo.XYLine;
import org.apache.lucene.geo.XYPoint;
import org.apache.lucene.geo.XYRectangle;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Independent verifier repro (public API only; this test lives outside org.apache.lucene.document,
 * so no package-private access is possible).
 *
 * <p>Claim under test: for a shape whose length (line) is zero after coordinate encoding, {@code
 * ShapeDocValuesField#getCentroid()} returns (0, 0) instead of a location of the geometry.
 *
 * <p>Oracle: a centroid is an average of the geometry's own coordinates, so it must lie inside the
 * geometry's own bounding box as returned by {@code getBoundingBox()} of the same field. For a line
 * whose vertices all encode to one location, that location is the only sensible answer, and it is
 * what the point factory returns for the same location.
 */
public class TestZeroMeasureShapeCentroid extends LuceneTestCase {

  private static final double EPS = 1e-6;

  private static void assertInside(Point c, Rectangle bbox) {
    assertTrue(
        "centroid " + c + " outside its own bounding box " + bbox,
        c.getLat() >= bbox.minLat - EPS
            && c.getLat() <= bbox.maxLat + EPS
            && c.getLon() >= bbox.minLon - EPS
            && c.getLon() <= bbox.maxLon + EPS);
  }

  private static void assertInside(XYPoint c, XYRectangle bbox) {
    assertTrue(
        "centroid " + c + " outside its own bounding box " + bbox,
        c.getX() >= bbox.minX - EPS
            && c.getX() <= bbox.maxX + EPS
            && c.getY() >= bbox.minY - EPS
            && c.getY() <= bbox.maxY + EPS);
  }

  /** Control: an ordinary two-point line has a centroid at its midpoint (passes on main). */
  public void testControlNormalLine() {
    Line line = new Line(new double[] {10, 12}, new double[] {20, 24});
    LatLonShapeDocValuesField f = LatLonShape.createDocValueField("f", line);
    assertEquals(11, f.getCentroid().getLat(), 1e-6);
    assertEquals(22, f.getCentroid().getLon(), 1e-6);
    assertInside(f.getCentroid(), f.getBoundingBox());
  }

  /**
   * Control: a line with one zero-length segment and one non-zero segment is fine, because the
   * total length is non-zero (passes on main).
   */
  public void testControlMixedZeroAndNonZeroSegments() {
    Line line = new Line(new double[] {10, 10, 12}, new double[] {20, 20, 24});
    LatLonShapeDocValuesField f = LatLonShape.createDocValueField("f", line);
    assertEquals(11, f.getCentroid().getLat(), 1e-6);
    assertEquals(22, f.getCentroid().getLon(), 1e-6);
  }

  /** Control: the point factory returns the point as its centroid (passes on main). */
  public void testControlPointFactory() {
    LatLonShapeDocValuesField f = LatLonShape.createDocValueField("f", 10d, 20d);
    assertEquals(10, f.getCentroid().getLat(), 1e-6);
    assertEquals(20, f.getCentroid().getLon(), 1e-6);
  }

  /** A two-point line whose points are identical (Line accepts this). */
  public void testLineWithTwoIdenticalPoints() {
    Line line = new Line(new double[] {10, 10}, new double[] {20, 20});
    LatLonShapeDocValuesField f = LatLonShape.createDocValueField("f", line);
    Point c = f.getCentroid();
    Rectangle bbox = f.getBoundingBox();
    assertInside(c, bbox);
    assertEquals(10, c.getLat(), 1e-6);
    assertEquals(20, c.getLon(), 1e-6);
  }

  /** Three identical points: exercises the several-component path (more than one segment). */
  public void testLineWithThreeIdenticalPoints() {
    Line line = new Line(new double[] {10, 10, 10}, new double[] {20, 20, 20});
    LatLonShapeDocValuesField f = LatLonShape.createDocValueField("f", line);
    assertEquals(2, f.numberOfTerms());
    assertInside(f.getCentroid(), f.getBoundingBox());
  }

  /** Two distinct points closer together than one encoding step: they encode to one location. */
  public void testLineShorterThanOneEncodingStep() {
    double lat0 = 10, lon0 = 20;
    double lat1 = 10 + 1e-9, lon1 = 20 + 1e-9;
    // precondition: both points encode to the same quantized location
    assertEquals(GeoEncodingUtils.encodeLatitude(lat0), GeoEncodingUtils.encodeLatitude(lat1));
    assertEquals(GeoEncodingUtils.encodeLongitude(lon0), GeoEncodingUtils.encodeLongitude(lon1));
    Line line = new Line(new double[] {lat0, lat1}, new double[] {lon0, lon1});
    LatLonShapeDocValuesField f = LatLonShape.createDocValueField("f", line);
    assertInside(f.getCentroid(), f.getBoundingBox());
  }

  /** Cartesian line with identical points (XYLine accepts this). */
  public void testXYLineWithTwoIdenticalPoints() {
    XYLine line = new XYLine(new float[] {3, 3}, new float[] {4, 4});
    XYShapeDocValuesField f = XYShape.createDocValueField("f", line);
    XYPoint c = f.getCentroid();
    assertInside(c, f.getBoundingBox());
    assertEquals(3f, c.getX(), 1e-6f);
    assertEquals(4f, c.getY(), 1e-6f);
  }

  /**
   * The two public factories for the same Line disagree: building the doc value from the line's
   * indexable fields yields the point as centroid, building it from the Line yields something else.
   */
  public void testFactoriesAgreeForSameLine() {
    Line line = new Line(new double[] {10, 10}, new double[] {20, 20});
    LatLonShapeDocValuesField fromLine = LatLonShape.createDocValueField("f", line);
    LatLonShapeDocValuesField fromFields =
        LatLonShape.createDocValueField("f", LatLonShape.createIndexableFields("f", line));
    // the indexable-fields route sees a degenerate triangle and types it as a point
    assertEquals(10, fromFields.getCentroid().getLat(), 1e-6);
    assertEquals(20, fromFields.getCentroid().getLon(), 1e-6);
    assertEquals(fromFields.getCentroid().getLat(), fromLine.getCentroid().getLat(), 1e-6);
    assertEquals(fromFields.getCentroid().getLon(), fromLine.getCentroid().getLon(), 1e-6);
  }

  /**
   * A polygon thinner than one latitude encoding step: its tessellated triangle is collinear after
   * encoding, so its area in the doc value is zero.
   */
  public void testSliverPolygonThinnerThanOneEncodingStep() {
    org.apache.lucene.geo.Polygon p =
        new org.apache.lucene.geo.Polygon(
            new double[] {10, 10, 10 + 1e-9, 10}, new double[] {20, 30, 25, 20});
    LatLonShapeDocValuesField f;
    try {
      f = LatLonShape.createDocValueField("f", p);
    } catch (IllegalArgumentException e) {
      assumeTrue("tessellator rejected the sliver polygon: " + e.getMessage(), false);
      return;
    }
    assertInside(f.getCentroid(), f.getBoundingBox());
  }

  /** The wrong centroid is what gets written to the index: read it back from BinaryDocValues. */
  public void testIndexedValueCarriesWrongCentroid() throws Exception {
    Line line = new Line(new double[] {10, 10}, new double[] {20, 20});
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        doc.add(LatLonShape.createDocValueField("shape", line));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReader leaf = r.leaves().get(0).reader();
        BinaryDocValues bdv = leaf.getBinaryDocValues("shape");
        assertTrue(bdv.advanceExact(0));
        BytesRef bytes = BytesRef.deepCopyOf(bdv.binaryValue());
        LatLonShapeDocValuesField back = LatLonShape.createDocValueField("shape", bytes);
        Point c = back.getCentroid();
        assertInside(c, back.getBoundingBox());
      }
    }
  }
}
