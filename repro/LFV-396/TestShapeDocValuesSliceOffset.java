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

import java.util.Arrays;
import org.apache.lucene.geo.Component2D;
import org.apache.lucene.geo.LatLonGeometry;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.Rectangle;
import org.apache.lucene.geo.XYGeometry;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.geo.XYRectangle;
import org.apache.lucene.index.PointValues.Relation;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A shape doc value built from a {@link BytesRef} slice (offset != 0) must relate to a query
 * exactly like the same bytes at offset 0. Two encoded squares are packed back to back in one
 * array, far square B first, then square A; A's doc value is built from the slice that points at A.
 * Public API only.
 */
public class TestShapeDocValuesSliceOffset extends LuceneTestCase {

  private static final Relation[] EXPECTED_FOR_A = {
    Relation.CELL_CROSSES_QUERY, // box 5..15 overlaps A
    Relation.CELL_INSIDE_QUERY, // box -20..20 contains A
    Relation.CELL_OUTSIDE_QUERY // box 45..55 overlaps only B
  };

  /**
   * copies {@code first} and {@code second} back to back; returns the slice that is {@code second}
   */
  private static BytesRef packAndSliceSecond(BytesRef first, BytesRef second) {
    byte[] buffer = new byte[first.length + second.length];
    System.arraycopy(first.bytes, first.offset, buffer, 0, first.length);
    System.arraycopy(second.bytes, second.offset, buffer, first.length, second.length);
    return new BytesRef(buffer, first.length, second.length);
  }

  private static void assertRelations(String what, Relation[] got) {
    if (Arrays.equals(EXPECTED_FOR_A, got) == false) {
      fail(
          what
              + ": expected "
              + Arrays.toString(EXPECTED_FOR_A)
              + " (like A at offset 0) but was "
              + Arrays.toString(got));
    }
  }

  private static Polygon latLonSquare(double min, double max) {
    return new Polygon(
        new double[] {min, min, max, max, min}, new double[] {min, max, max, min, min});
  }

  private static XYPolygon xySquare(float min, float max) {
    return new XYPolygon(
        new float[] {min, max, max, min, min}, new float[] {min, min, max, max, min});
  }

  public void testLatLonSliceRelatesLikeItsOwnBytes() throws Exception {
    BytesRef b =
        BytesRef.deepCopyOf(
            LatLonShape.createDocValueField("f", latLonSquare(40, 50)).binaryValue());
    BytesRef a =
        BytesRef.deepCopyOf(
            LatLonShape.createDocValueField("f", latLonSquare(0, 10)).binaryValue());
    BytesRef aSlice = packAndSliceSecond(b, a);
    assertTrue("same bytes", a.bytesEquals(aSlice));

    LatLonShapeDocValues alone = LatLonShape.createLatLonShapeDocValues(a);
    LatLonShapeDocValues sliced = LatLonShape.createLatLonShapeDocValues(aSlice);
    // the header is read at the offset: same bounding box and centroid
    assertEquals(alone.getBoundingBox(), sliced.getBoundingBox());
    assertEquals(alone.getCentroid(), sliced.getCentroid());

    Rectangle[] boxes = {
      new Rectangle(5, 15, 5, 15), new Rectangle(-20, 20, -20, 20), new Rectangle(45, 55, 45, 55)
    };
    Relation[] got = new Relation[boxes.length];
    for (int i = 0; i < boxes.length; i++) {
      Component2D box = LatLonGeometry.create(boxes[i]);
      assertEquals("control: A at offset 0", EXPECTED_FOR_A[i], alone.relate(box));
      got[i] = sliced.relate(box);
    }
    assertRelations(
        "LatLon square A at offset " + aSlice.offset + ", B in bytes 0-" + (b.length - 1), got);
  }

  public void testXYSliceRelatesLikeItsOwnBytes() throws Exception {
    BytesRef b =
        BytesRef.deepCopyOf(XYShape.createDocValueField("f", xySquare(40, 50)).binaryValue());
    BytesRef a =
        BytesRef.deepCopyOf(XYShape.createDocValueField("f", xySquare(0, 10)).binaryValue());
    BytesRef aSlice = packAndSliceSecond(b, a);
    assertTrue("same bytes", a.bytesEquals(aSlice));

    XYShapeDocValues alone = XYShape.createXYShapeDocValues(a);
    XYShapeDocValues sliced = XYShape.createXYShapeDocValues(aSlice);
    assertEquals(alone.getBoundingBox(), sliced.getBoundingBox());
    assertEquals(alone.getCentroid(), sliced.getCentroid());

    XYRectangle[] boxes = {
      new XYRectangle(5, 15, 5, 15),
      new XYRectangle(-20, 20, -20, 20),
      new XYRectangle(45, 55, 45, 55)
    };
    Relation[] got = new Relation[boxes.length];
    for (int i = 0; i < boxes.length; i++) {
      Component2D box = XYGeometry.create(boxes[i]);
      assertEquals("control: A at offset 0", EXPECTED_FOR_A[i], alone.relate(box));
      got[i] = sliced.relate(box);
    }
    assertRelations(
        "XY square A at offset " + aSlice.offset + ", B in bytes 0-" + (b.length - 1), got);
  }
}
