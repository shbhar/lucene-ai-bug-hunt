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
package org.apache.lucene.index.memory;

import org.apache.lucene.document.LatLonShape;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A MemoryIndex shape doc-values field built from a {@link BytesRef} slice (offset != 0) must match
 * box queries exactly like the same bytes at offset 0. Public API only.
 */
public class TestMemoryIndexShapeDocValuesSlice extends LuceneTestCase {

  private static Polygon square(double min, double max) {
    return new Polygon(
        new double[] {min, min, max, max, min}, new double[] {min, max, max, min, min});
  }

  /** "match" or "no match" for INTERSECTS and DISJOINT with the box 5..15, which overlaps A */
  private static String matches(BytesRef encoded) {
    MemoryIndex index = new MemoryIndex();
    index.addField(LatLonShape.createDocValueField("shape", encoded), null);
    StringBuilder result = new StringBuilder();
    for (QueryRelation relation :
        new QueryRelation[] {QueryRelation.INTERSECTS, QueryRelation.DISJOINT}) {
      float score =
          index.search(LatLonShape.newSlowDocValuesBoxQuery("shape", relation, 5, 15, 5, 15));
      result.append(relation).append(score > 0 ? "=match " : "=no match ");
    }
    return result.toString().trim();
  }

  public void testSliceFieldMatchesLikeItsOwnBytes() {
    BytesRef b =
        BytesRef.deepCopyOf(LatLonShape.createDocValueField("f", square(40, 50)).binaryValue());
    BytesRef a =
        BytesRef.deepCopyOf(LatLonShape.createDocValueField("f", square(0, 10)).binaryValue());
    byte[] buffer = new byte[b.length + a.length];
    System.arraycopy(b.bytes, b.offset, buffer, 0, b.length);
    System.arraycopy(a.bytes, a.offset, buffer, b.length, a.length);
    BytesRef aSlice = new BytesRef(buffer, b.length, a.length);

    String expected = "INTERSECTS=match DISJOINT=no match";
    assertEquals("control: A at offset 0", expected, matches(a));
    String got = matches(aSlice);
    if (expected.equals(got) == false) {
      fail("square A at offset " + aSlice.offset + ": expected " + expected + " but was " + got);
    }
  }
}
