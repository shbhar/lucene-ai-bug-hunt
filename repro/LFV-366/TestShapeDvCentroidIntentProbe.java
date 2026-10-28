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

import org.apache.lucene.geo.Line;
import org.apache.lucene.geo.Point;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.Rectangle;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Probes for the intended-behaviour review: the end-to-end path (index, read back, decode), how
 * the indexed form treats the same collapsed line, whether the polygon factories agree, and that
 * a partially degenerate line is handled.
 */
public class TestShapeDvCentroidIntentProbe extends LuceneTestCase {

  private static boolean inside(Point c, Rectangle box) {
    return c.getLat() >= box.minLat
        && c.getLat() <= box.maxLat
        && c.getLon() >= box.minLon
        && c.getLon() <= box.maxLon;
  }

  /** two distinct points closer than one encoding step: a valid line with distinct vertices */
  private static Line subStepLine() {
    return new Line(new double[] {40, 40.000000001}, new double[] {-70, -70});
  }

  /** realistic path: index the doc value, read it back with the public decoder, ask for centroid */
  public void testRoundTripThroughIndex() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      Document doc = new Document();
      doc.add(LatLonShape.createDocValueField("shape", subStepLine()));
      w.addDocument(doc);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        LeafReader leaf = r.leaves().get(0).reader();
        BinaryDocValues bdv = leaf.getBinaryDocValues("shape");
        assertEquals(0, bdv.nextDoc());
        BytesRef bytes = bdv.binaryValue();
        LatLonShapeDocValues dv = LatLonShape.createLatLonShapeDocValues(bytes);
        System.out.println(
            "PROBE roundtrip centroid=" + dv.getCentroid() + " bbox=" + dv.getBoundingBox()
                + " type=" + dv.getHighestDimension());
        assertTrue(
            "round-tripped centroid " + dv.getCentroid() + " outside bbox " + dv.getBoundingBox(),
            inside(dv.getCentroid(), dv.getBoundingBox()));
      }
    }
  }

  /** the indexed (points) form of the same line resolves the collapsed segment to a POINT */
  public void testIndexedFormResolvesCollapsedLineToPoint() {
    Field[] fields = LatLonShape.createIndexableFields("shape", subStepLine());
    assertEquals(1, fields.length);
    ShapeField.DecodedTriangle t = new ShapeField.DecodedTriangle();
    BytesRef br = fields[0].binaryValue();
    byte[] scratch = new byte[7 * Integer.BYTES];
    System.arraycopy(br.bytes, br.offset, scratch, 0, scratch.length);
    ShapeField.decodeTriangle(scratch, t);
    System.out.println("PROBE indexed-form type=" + t.type);
    assertEquals(ShapeField.DecodedTriangle.TYPE.POINT, t.type);
  }

  /** for the sliver polygon both public factories give the same value (no factory disagreement) */
  public void testSliverPolygonFactoriesAgree() {
    Polygon sliver =
        new Polygon(new double[] {40, 40, 40.000000001, 40}, new double[] {-70, -60, -65, -70});
    Point a = LatLonShape.createDocValueField("shape", sliver).getCentroid();
    Point b =
        LatLonShape.createDocValueField("shape", LatLonShape.createIndexableFields("shape", sliver))
            .getCentroid();
    System.out.println("PROBE sliver polygon centroid fromPolygon=" + a + " fromFields=" + b);
    assertEquals(a, b);
  }

  /** a line with one zero-length segment and one real segment is fine: needs ALL weight zero */
  public void testPartiallyDegenerateLineIsFine() {
    Line line = new Line(new double[] {40, 40, 41}, new double[] {-70, -70, -70});
    LatLonShapeDocValuesField dv = LatLonShape.createDocValueField("shape", line);
    System.out.println(
        "PROBE partial centroid=" + dv.getCentroid() + " bbox=" + dv.getBoundingBox());
    assertTrue(inside(dv.getCentroid(), dv.getBoundingBox()));
  }
  /** the same sliver with the other winding order (Polygon javadoc note 1 asks for clockwise) */
  public void testSliverPolygonOtherWinding() {
    Polygon sliver =
        new Polygon(new double[] {40, 40.000000001, 40, 40}, new double[] {-70, -65, -60, -70});
    LatLonShapeDocValuesField dv = LatLonShape.createDocValueField("shape", sliver);
    System.out.println(
        "PROBE sliver other winding order=" + sliver.getWindingOrder() + " centroid="
            + dv.getCentroid() + " bbox=" + dv.getBoundingBox());
    assertTrue(
        "centroid " + dv.getCentroid() + " outside bbox " + dv.getBoundingBox(),
        inside(dv.getCentroid(), dv.getBoundingBox()));
  }
}
