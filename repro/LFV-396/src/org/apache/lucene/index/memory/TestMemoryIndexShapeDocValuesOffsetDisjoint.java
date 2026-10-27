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

/** The DISJOINT side of the same defect: an offset value wrongly matches DISJOINT. Public API only. */
public class TestMemoryIndexShapeDocValuesOffsetDisjoint extends LuceneTestCase {
  public void testDisjointWithOffset() {
    Polygon square = new Polygon(new double[] {0, 0, 10, 10, 0}, new double[] {0, 10, 10, 0, 0});
    BytesRef value = LatLonShape.createDocValueField("shape", square).binaryValue();
    byte[] buffer = new byte[3 + value.length];
    System.arraycopy(value.bytes, value.offset, buffer, 3, value.length);
    MemoryIndex mi = new MemoryIndex();
    mi.addField(LatLonShape.createDocValueField("shape", new BytesRef(buffer, 3, value.length)), null);
    float disjoint =
        mi.search(LatLonShape.newSlowDocValuesBoxQuery("shape", QueryRelation.DISJOINT, 5, 15, 5, 15));
    assertEquals("a square overlapping the box must not match DISJOINT", 0f, disjoint, 0f);
  }
}
