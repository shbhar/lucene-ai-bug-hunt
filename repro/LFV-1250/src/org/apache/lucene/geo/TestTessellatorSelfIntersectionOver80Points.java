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

import org.apache.lucene.document.XYShape;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTessellatorSelfIntersectionOver80Points extends LuceneTestCase {

  public void testSelfCrossingRingWith81PointsIsRejected() {
    // Expected: IAE "Polygon self-intersection" (as for the same ring with 7 or 80 points); actual: no exception.
    float[] xs = new float[81];
    float[] ys = new float[81];
    for (int i = 0; i < 75; i++) {
      xs[i] = 5 - 2f * i / 75;
      ys[i] = 2;
    }
    float[] restX = {3, 6, 9, 7, 1, 5};
    float[] restY = {2, 0, 9, 5, 1, 2};
    System.arraycopy(restX, 0, xs, 75, 6);
    System.arraycopy(restY, 0, ys, 75, 6);
    XYPolygon polygon = new XYPolygon(xs, ys);
    expectThrows(
        IllegalArgumentException.class, () -> XYShape.createIndexableFields("f", polygon, true));
  }
}
