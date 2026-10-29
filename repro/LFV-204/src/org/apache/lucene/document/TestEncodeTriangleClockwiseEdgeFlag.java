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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestEncodeTriangleClockwiseEdgeFlag extends LuceneTestCase {

  public void testClockwiseTriangleKeepsEdgeFlag() {
    byte[] bytes = new byte[7 * Integer.BYTES];
    // clockwise (0,0) (0,10) (10,0): expected only (0,10)-(10,0) flagged, actual also (0,0)-(10,0)
    ShapeField.encodeTriangle(bytes, 0, 0, false, 10, 0, true, 0, 10, false);
    ShapeField.DecodedTriangle t = new ShapeField.DecodedTriangle();
    ShapeField.decodeTriangle(bytes, t);
    List<String> flagged = new ArrayList<>();
    if (t.ab) flagged.add(edge(t.aX, t.aY, t.bX, t.bY));
    if (t.bc) flagged.add(edge(t.bX, t.bY, t.cX, t.cY));
    if (t.ca) flagged.add(edge(t.cX, t.cY, t.aX, t.aY));
    assertEquals("flagged edges of " + t, List.of(edge(0, 10, 10, 0)), flagged);
  }

  private static String edge(int x1, int y1, int x2, int y2) {
    boolean ordered = x1 < x2 || (x1 == x2 && y1 <= y2);
    return ordered
        ? "(" + x1 + "," + y1 + ")-(" + x2 + "," + y2 + ")"
        : "(" + x2 + "," + y2 + ")-(" + x1 + "," + y1 + ")";
  }
}
