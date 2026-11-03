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

import org.apache.lucene.geo.LatLonGeometry;
import org.apache.lucene.geo.Line;
import org.apache.lucene.geo.Rectangle;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestShapeDocValuesBBoxAfterRelate extends LuceneTestCase {

  public void testBoundingBoxUnchangedByRelate() throws Exception {
    Line line = new Line(new double[] {0, 10, 20}, new double[] {0, 10, 20});
    LatLonShapeDocValues dv =
        LatLonShape.createLatLonShapeDocValues(
            LatLonShape.createDocValueField("f", line).binaryValue());
    int minX = dv.getEncodedMinX();
    dv.relate(LatLonGeometry.create(new Rectangle(19, 21, 19, 21)));
    // expected: the shape's bounding box minX is unchanged by relate(); actual: a tree node's minX
    assertEquals(minX, dv.getEncodedMinX());
  }
}
