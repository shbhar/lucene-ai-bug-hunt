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
package org.apache.lucene.geo;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.XYShape;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * XYPolygon computes its winding order with float arithmetic. For coordinates whose products
 * exceed Float.MAX_VALUE (|x|, |y| above about 1.8e19, which XYPolygon and XYEncodingUtils accept)
 * the sum becomes NaN and the winding order is wrong; the Tessellator then gets the ring in the
 * wrong order and rejects the valid polygon.
 */
public class TestXYPolygonLargeCoordinates extends LuceneTestCase {

  private static XYPolygon triangle(float scale) {
    // (0,0) (1,1) (2,1), times scale: a plain non-degenerate triangle
    return new XYPolygon(
        new float[] {0, scale, 2 * scale, 0}, new float[] {0, scale, scale, 0});
  }

  /** scaling by a power of two is exact in float and must not change the orientation */
  public void testWindingOrderIsScaleInvariant() {
    float big = Math.scalb(1f, 66); // about 7.4e19
    assertEquals(triangle(1f).getWindingOrder(), triangle(big).getWindingOrder());
  }

  public void testLargeTriangleCanBeIndexed() {
    float big = Math.scalb(1f, 66);
    Field[] fields = XYShape.createIndexableFields("shape", triangle(big));
    assertEquals(1, fields.length);
  }
}
