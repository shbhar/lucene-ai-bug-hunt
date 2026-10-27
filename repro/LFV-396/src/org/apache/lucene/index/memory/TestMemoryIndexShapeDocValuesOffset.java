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
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A doc-values shape query on a MemoryIndex must match a shape doc value field built from an
 * encoded value, whether or not that BytesRef starts at offset 0. Uses only public API.
 */
public class TestMemoryIndexShapeDocValuesOffset extends LuceneTestCase {

  private static float score(BytesRef encoded, QueryRelation relation) {
    MemoryIndex mi = new MemoryIndex();
    mi.addField(LatLonShape.createDocValueField("shape", encoded), null);
    // a box that overlaps the 10 x 10 degree square
    Query q = LatLonShape.newSlowDocValuesBoxQuery("shape", relation, 5, 15, 5, 15);
    return mi.search(q);
  }

  public void testShapeDocValuesQueryWithOffset() {
    Polygon square = new Polygon(new double[] {0, 0, 10, 10, 0}, new double[] {0, 10, 10, 0, 0});
    BytesRef value = LatLonShape.createDocValueField("shape", square).binaryValue();
    byte[] buffer = new byte[3 + value.length];
    System.arraycopy(value.bytes, value.offset, buffer, 3, value.length);

    BytesRef atZero = BytesRef.deepCopyOf(value);
    BytesRef atThree = new BytesRef(buffer, 3, value.length);
    assertTrue("offset 0 intersects", score(atZero, QueryRelation.INTERSECTS) > 0);
    assertEquals("offset 0 is not disjoint", 0f, score(atZero, QueryRelation.DISJOINT), 0f);
    assertTrue(
        "the same bytes at offset 3 must intersect too", score(atThree, QueryRelation.INTERSECTS) > 0);
    assertEquals(
        "the same bytes at offset 3 must not be disjoint",
        0f,
        score(atThree, QueryRelation.DISJOINT),
        0f);
  }
}
