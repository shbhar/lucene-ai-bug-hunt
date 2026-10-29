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

import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestXYShapeLargeTriangle extends LuceneTestCase {

  public void testTriangleScaledBy2Pow66CanBeIndexed() {
    float s = Math.scalb(1f, 66);
    XYPolygon triangle = new XYPolygon(new float[] {0, s, 2 * s, 0}, new float[] {0, s, s, 0});
    try {
      XYShape.createIndexableFields("shape", triangle);
    } catch (IllegalArgumentException e) {
      fail("expected: the scaled triangle indexes as at scale 1; actual: " + e.getMessage());
    }
  }
}
