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

public class TestTessellatorDisjointHolesCollinearBridge extends LuceneTestCase {

  public void testPolygonWithDisjointHolesIsIndexed() {
    // two counter-clockwise holes, strictly inside the outer ring and touching nothing
    XYPolygon holeA = new XYPolygon(new float[] {3, 3, 4, 3}, new float[] {2, 1, 1, 2});
    XYPolygon holeB = new XYPolygon(new float[] {2, 4, 3, 2}, new float[] {2, 3, 3, 2});
    // clockwise outer ring (5,1) (0,0) (1,1) (4,5); the same ring listed from (4,5) is accepted
    XYPolygon polygon =
        new XYPolygon(new float[] {5, 0, 1, 4, 5}, new float[] {1, 0, 1, 5, 1}, holeA, holeB);
    // on main this throws IllegalArgumentException: Unable to Tessellate shape
    Field[] fields = XYShape.createIndexableFields("shape", polygon);
    assertTrue(fields.length > 0);
  }
}
