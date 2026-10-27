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

import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Component-level checks for a query polygon with holes: a segment or triangle whose vertices all
 * lie in holes still intersects the polygon when it crosses polygon area between them. The methods
 * are meant to be added to TestPolygon2D.
 */
public class TestPolygon2DHoleEdges extends LuceneTestCase {

  /** The square [0,10] x [0,10] (x = lon, y = lat) with holes [1,3] x [4,6] and [7,9] x [4,6]. */
  private static Component2D twoHoles() {
    Polygon holeA = new Polygon(new double[] {4, 4, 6, 6, 4}, new double[] {1, 3, 3, 1, 1});
    Polygon holeB = new Polygon(new double[] {4, 4, 6, 6, 4}, new double[] {7, 9, 9, 7, 7});
    return Polygon2D.create(
        new Polygon(new double[] {0, 10, 10, 0, 0}, new double[] {0, 0, 10, 10, 0}, holeA, holeB));
  }

  public void testIntersectsAcrossTwoHoles() {
    Component2D polygon = twoHoles();
    assertTrue(polygon.contains(5, 5)); // the land between the holes
    assertFalse(polygon.contains(2, 5)); // in hole A
    assertFalse(polygon.contains(8, 5)); // in hole B
    // from hole A to hole B through (5,5)
    assertTrue("line from hole A to hole B", polygon.intersectsLine(2, 5, 8, 5));
    // one vertex in hole A, two in hole B; the triangle crosses the land strip, e.g. at (5,4.8)
    assertTrue("triangle over both holes", polygon.intersectsTriangle(2, 4.5, 8, 4.5, 8, 5.5));
  }

  public void testNoIntersectionInsideOneHole() {
    Component2D polygon = twoHoles();
    assertFalse(polygon.intersectsLine(1.5, 5, 2.5, 5));
    assertFalse(polygon.intersectsTriangle(1.5, 4.5, 2.5, 4.5, 2.5, 5.5));
    // left of the outer ring
    assertFalse(polygon.intersectsLine(-1, 5, -0.5, 5));
  }

  public void testIntersectsAcrossNonConvexHole() {
    // the square [0,10] x [0,10] with one U-shaped hole: bottom bar [2,8] x [2,4] and arms
    // [2,4] x [4,8] and [6,8] x [4,8]; the notch [4,6] x [4,8] between the arms is land
    Polygon u =
        new Polygon(
            new double[] {2, 2, 8, 8, 4, 4, 8, 8, 2}, new double[] {2, 8, 8, 6, 6, 4, 4, 2, 2});
    Component2D polygon =
        Polygon2D.create(
            new Polygon(new double[] {0, 10, 10, 0, 0}, new double[] {0, 0, 10, 10, 0}, u));
    assertTrue(polygon.contains(5, 6)); // the notch
    assertFalse(polygon.contains(3, 6)); // left arm of the hole
    assertFalse(polygon.contains(7, 6)); // right arm of the hole
    assertFalse(polygon.intersectsLine(2.5, 6, 3.5, 6)); // inside the left arm
    // from arm to arm through the notch
    assertTrue("line through the notch", polygon.intersectsLine(3, 6, 7, 6));
  }
}
