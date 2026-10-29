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

public class TestTessellatorChainedTouchingHoles extends LuceneTestCase {

  public void testHolesTouchingAtSingleVertices() {
    XYPolygon polygon =
        new XYPolygon(
            new float[] {0, 0, 4, 4, 12, 12, 0},
            new float[] {0, 12, 12, 4, 4, 0, 0},
            new XYPolygon(new float[] {4, 1, 2, 4}, new float[] {4, 2, 1, 4}),
            new XYPolygon(new float[] {3, 4, 3, 3}, new float[] {4, 4, 5, 4}),
            new XYPolygon(new float[] {2, 1, 3, 2}, new float[] {6, 5, 4, 6}));
    try {
      XYShape.createIndexableFields("shape", polygon);
    } catch (IllegalArgumentException e) {
      throw new AssertionError("expected the valid polygon to be tessellated, got " + e, e);
    }
  }
}
