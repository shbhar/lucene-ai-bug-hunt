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

import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestXYTessellatorMonitor extends LuceneTestCase {

  public void testMonitorOnXYPolygonOutsideLatLonRange() {
    XYPolygon triangle = new XYPolygon(new float[] {0, 500, 0, 0}, new float[] {0, 0, 50, 0});
    Tessellator.Monitor noOp =
        new Tessellator.Monitor() {
          @Override
          public void currentState(
              String status, List<Point> points, List<Tessellator.Triangle> t) {}

          @Override
          public void startSplit(String status, List<Point> left, List<Point> right) {}

          @Override
          public void endSplit(String status) {}
        };
    // same result as without a monitor: 1 triangle (today: "invalid longitude 500.0")
    assertEquals(1, Tessellator.tessellate(triangle, false, noOp).size());
  }
}
