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
 * A shape doc value given as a BytesRef slice (offset != 0) must relate to a query exactly like
 * the same bytes at offset 0. Uses only public API.
 */
public class TestShapeDocValuesBytesRefOffset extends LuceneTestCase {

  /** the same bytes, copied into a larger array at {@code offset} */
  private static BytesRef slice(BytesRef value, int offset) {
    byte[] buffer = new byte[offset + value.length + 8];
    System.arraycopy(value.bytes, value.offset, buffer, offset, value.length);
    return new BytesRef(buffer, offset, value.length);
  }

  /** 10 x 10 degree square, tessellated into two triangles */
  private static Polygon square() {
    return new Polygon(new double[] {0, 0, 10, 10, 0}, new double[] {0, 10, 10, 0, 0});
  }

  public void testLatLonRelateWithOffset() throws Exception {
    BytesRef value = LatLonShape.createDocValueField("shape", square()).binaryValue();
    LatLonShapeDocValues atZero = LatLonShape.createLatLonShapeDocValues(slice(value, 0));
    LatLonShapeDocValues atThree = LatLonShape.createLatLonShapeDocValues(slice(value, 3));

    // the header is read at the right place: same bounding box and centroid
    assertEquals(atZero.getBoundingBox(), atThree.getBoundingBox());
    assertEquals(atZero.getCentroid(), atThree.getCentroid());

    // a box that overlaps the square, but does not contain its bounding box
    Component2D box = LatLonGeometry.create(new Rectangle(5, 15, 5, 15));
    assertEquals(Relation.CELL_CROSSES_QUERY, atZero.relate(box));
    assertEquals(
        "same bytes at offset 3 must relate like at offset 0",
        atZero.relate(box),
        atThree.relate(box));
  }

  public void testXYRelateWithOffset() throws Exception {
    XYPolygon square =
        new XYPolygon(new float[] {0, 10, 10, 0, 0}, new float[] {0, 0, 10, 10, 0});
    BytesRef value = XYShape.createDocValueField("shape", square).binaryValue();
    XYShapeDocValues atZero = XYShape.createXYShapeDocValues(slice(value, 0));
    XYShapeDocValues atThree = XYShape.createXYShapeDocValues(slice(value, 3));
    assertEquals(atZero.getBoundingBox(), atThree.getBoundingBox());

    Component2D box = XYGeometry.create(new XYRectangle(5, 15, 5, 15));
    assertEquals(Relation.CELL_CROSSES_QUERY, atZero.relate(box));
    assertEquals(
        "same bytes at offset 3 must relate like at offset 0",
        atZero.relate(box),
        atThree.relate(box));
  }
}
