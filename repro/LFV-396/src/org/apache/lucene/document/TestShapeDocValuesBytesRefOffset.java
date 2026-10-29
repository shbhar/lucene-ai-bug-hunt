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
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestShapeDocValuesBytesRefOffset extends LuceneTestCase {

  private static BytesRef square(double min, double max) {
    Polygon p =
        new Polygon(new double[] {min, min, max, max, min}, new double[] {min, max, max, min, min});
    return BytesRef.deepCopyOf(LatLonShape.createDocValueField("f", p).binaryValue());
  }

  public void testRelateWithNonZeroOffset() throws Exception {
    BytesRef a = square(0, 10);
    BytesRef b = square(40, 50);
    byte[] buf = new byte[b.length + a.length];
    System.arraycopy(b.bytes, 0, buf, 0, b.length);
    System.arraycopy(a.bytes, 0, buf, b.length, a.length);
    Component2D box = LatLonGeometry.create(new Rectangle(5, 15, 5, 15));
    // expected: the slice of a relates like a at offset 0 (CROSSES); actual: like b (OUTSIDE)
    assertEquals(
        LatLonShape.createLatLonShapeDocValues(a).relate(box),
        LatLonShape.createLatLonShapeDocValues(new BytesRef(buf, b.length, a.length)).relate(box));
  }
}
