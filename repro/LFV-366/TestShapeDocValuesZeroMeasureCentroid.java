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

import org.apache.lucene.geo.GeoEncodingUtils;
import org.apache.lucene.geo.Line;
import org.apache.lucene.geo.Point;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.Rectangle;
import org.apache.lucene.geo.XYLine;
import org.apache.lucene.geo.XYPoint;
import org.apache.lucene.geo.XYRectangle;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A shape doc value's centroid is an average of the shape's own points, so it must lie inside the
 * shape's own bounding box. When the shape's total length or area is zero after encoding, {@code
 * getCentroid()} returns (0, 0) instead. Public API only.
 */
public class TestShapeDocValuesZeroMeasureCentroid extends LuceneTestCase {

  private static void assertInside(Point c, Rectangle box) {
    assertTrue(
        "centroid " + c + " must be inside the bounding box " + box,
        c.getLat() >= box.minLat
            && c.getLat() <= box.maxLat
            && c.getLon() >= box.minLon
            && c.getLon() <= box.maxLon);
  }

  /** control: an ordinary line gets its midpoint (passes) */
  public void testControlNormalLine() {
    Line line = new Line(new double[] {40, 41}, new double[] {-70, -69});
    LatLonShapeDocValuesField dv = LatLonShape.createDocValueField("shape", line);
    assertInside(dv.getCentroid(), dv.getBoundingBox());
    assertEquals(40.5, dv.getCentroid().getLat(), 1e-6);
    assertEquals(-69.5, dv.getCentroid().getLon(), 1e-6);
  }

  /** control: one zero-length segment next to a real one is fine, the total length is not 0 */
  public void testControlOneZeroLengthSegment() {
    Line line = new Line(new double[] {40, 40, 41}, new double[] {-70, -70, -69});
    LatLonShapeDocValuesField dv = LatLonShape.createDocValueField("shape", line);
    assertEquals(40.5, dv.getCentroid().getLat(), 1e-6);
    assertEquals(-69.5, dv.getCentroid().getLon(), 1e-6);
  }

  /** a line whose two points are equal (Line accepts repeated points) */
  public void testLineWithRepeatedPoint() {
    Line line = new Line(new double[] {40, 40}, new double[] {-70, -70});
    LatLonShapeDocValuesField dv = LatLonShape.createDocValueField("shape", line);
    assertInside(dv.getCentroid(), dv.getBoundingBox());
  }

  /** two distinct points closer than one encoding step (about 4.7 mm of latitude) */
  public void testLineShorterThanOneEncodingStep() {
    assertEquals(
        GeoEncodingUtils.encodeLatitude(40), GeoEncodingUtils.encodeLatitude(40.000000001));
    Line line = new Line(new double[] {40, 40.000000001}, new double[] {-70, -70});
    LatLonShapeDocValuesField dv = LatLonShape.createDocValueField("shape", line);
    assertInside(dv.getCentroid(), dv.getBoundingBox());
  }

  /** the other public factory returns the real point for the same line */
  public void testFactoriesAgree() {
    Line line = new Line(new double[] {40, 40}, new double[] {-70, -70});
    Point fromFields =
        LatLonShape.createDocValueField("shape", LatLonShape.createIndexableFields("shape", line))
            .getCentroid();
    Point fromLine = LatLonShape.createDocValueField("shape", line).getCentroid();
    assertEquals(fromFields, fromLine);
  }

  /** a polygon 10 degrees long but thinner than one latitude encoding step: zero area */
  public void testSliverPolygon() {
    Polygon sliver =
        new Polygon(new double[] {40, 40, 40.000000001, 40}, new double[] {-70, -60, -65, -70});
    LatLonShapeDocValuesField dv = LatLonShape.createDocValueField("shape", sliver);
    assertInside(dv.getCentroid(), dv.getBoundingBox());
  }

  /** the cartesian variant: an XYLine whose two points are equal */
  public void testXYLineWithRepeatedPoint() {
    XYLine line = new XYLine(new float[] {5, 5}, new float[] {7, 7});
    XYShapeDocValuesField dv = XYShape.createDocValueField("shape", line);
    XYPoint c = dv.getCentroid();
    XYRectangle box = dv.getBoundingBox();
    assertTrue(
        "centroid " + c + " must be inside the bounding box " + box,
        c.getX() >= box.minX
            && c.getX() <= box.maxX
            && c.getY() >= box.minY
            && c.getY() <= box.maxY);
  }

  /** the wrong centroid is what the index stores: read it back from BinaryDocValues */
  public void testStoredValueKeepsWrongCentroid() throws Exception {
    Line line = new Line(new double[] {40, 40}, new double[] {-70, -70});
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        doc.add(LatLonShape.createDocValueField("shape", line));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        BinaryDocValues values = r.leaves().get(0).reader().getBinaryDocValues("shape");
        assertTrue(values.advanceExact(0));
        BytesRef stored = BytesRef.deepCopyOf(values.binaryValue());
        LatLonShapeDocValuesField back = LatLonShape.createDocValueField("shape", stored);
        assertInside(back.getCentroid(), back.getBoundingBox());
      }
    }
  }
}
