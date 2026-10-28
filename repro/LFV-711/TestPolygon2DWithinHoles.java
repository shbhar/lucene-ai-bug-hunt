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

import org.apache.lucene.geo.Component2D.WithinRelation;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Polygon2D.withinTriangle with a query polygon that has holes (methods meant for TestPolygon2D). A
 * triangle edge flagged true belongs to the indexed shape; an edge flagged false is a tessellation
 * edge.
 */
public class TestPolygon2DWithinHoles extends LuceneTestCase {

  private static final float[] SQUARE_X = {0, 0, 10, 10, 0};
  private static final float[] SQUARE_Y = {0, 10, 10, 0, 0};

  /** the square [0,10] x [0,10] minus the holes [1,3] x [4,6] and [7,9] x [4,6] */
  private static Component2D twoHoles() {
    XYPolygon holeA = new XYPolygon(new float[] {1, 3, 3, 1, 1}, new float[] {4, 4, 6, 6, 4});
    XYPolygon holeB = new XYPolygon(new float[] {7, 9, 9, 7, 7}, new float[] {4, 4, 6, 6, 4});
    return Polygon2D.create(new XYPolygon(SQUARE_X, SQUARE_Y, holeA, holeB));
  }

  /**
   * the square [0,10] x [0,10] minus one U-shaped hole with arms [1,3] x [3,6] and [7,9] x [3,6]
   */
  private static Component2D uShapedHole() {
    XYPolygon hole =
        new XYPolygon(
            new float[] {1, 9, 9, 7, 7, 3, 3, 1, 1}, new float[] {3, 3, 6, 6, 4, 4, 6, 6, 3});
    return Polygon2D.create(new XYPolygon(SQUARE_X, SQUARE_Y, hole));
  }

  public void testShapeEdgeBetweenTwoHoles() {
    Component2D polygon = twoHoles();
    assertTrue(polygon.contains(5, 4.5)); // on the shape edge (2,4.5)-(8,4.5) below
    assertFalse(polygon.contains(2, 4.5)); // in hole A
    assertFalse(polygon.contains(8, 4.5)); // in hole B
    // the third vertex is outside the polygon, so two tessellation edges cross the outer ring
    assertEquals(
        WithinRelation.NOTWITHIN,
        polygon.withinTriangle(-1, -1, false, 2, 4.5, true, 8, 4.5, false));
    // all three vertices in the holes
    assertEquals(
        WithinRelation.NOTWITHIN,
        polygon.withinTriangle(2, 4.5, true, 8, 4.5, false, 8, 5.5, false));
  }

  public void testShapeEdgeAcrossUShapedHole() {
    Component2D polygon = uShapedHole();
    assertTrue(polygon.contains(5, 4.5)); // the land between the two arms
    assertEquals(
        WithinRelation.NOTWITHIN,
        polygon.withinTriangle(-1, -1, false, 2, 4.5, true, 8, 4.5, false));
  }

  public void testControls() {
    Component2D polygon = twoHoles();
    // the same triangle, but the edge between the holes is a tessellation edge: a candidate
    assertEquals(
        WithinRelation.CANDIDATE,
        polygon.withinTriangle(-1, -1, false, 2, 4.5, false, 8, 4.5, false));
    // a shape edge that stays inside hole A: a candidate
    assertEquals(
        WithinRelation.CANDIDATE,
        polygon.withinTriangle(-1, -1, false, 1.5, 4.5, true, 2.5, 4.5, false));
    // a shape edge with an end on the land: not within (as before)
    assertEquals(
        WithinRelation.NOTWITHIN,
        polygon.withinTriangle(-1, -1, false, 2, 4.5, true, 5, 4.5, false));
  }
}
