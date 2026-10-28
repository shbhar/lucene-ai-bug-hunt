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

import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.Tessellator;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A valid polygon with three holes that all meet at the vertex P=(7,6) (each pair of holes touches
 * at that one vertex only; no hole touches the outer ring). For the hole whose left-most vertex is P,
 * Tessellator picks the first copy of P in ring order as the connection, and the polygon is rejected
 * with "Unable to Tessellate shape" by XYShape/LatLonShape.createIndexableFields.
 *
 * <pre>
 *  outer ring (clockwise): (0,0) (7,12) (12,2)
 *  hole 0: (8,8) (6,7) (7,6)         triangle with vertex P
 *  hole 1: (9,3) (10,4) (7,6)        triangle whose left-most vertex is P
 *  hole 2: (6,6) (6,4) (7,4) (7,6)   square, top-right corner P
 * </pre>
 */
public class TestTessellatorThreeHolesSharedVertex extends LuceneTestCase {

  static final float[] SX = {0, 7, 12, 0}, SY = {0, 12, 2, 0};
  // holes, counter-clockwise (Polygon NOTES), in this order (the result depends on the hole order)
  static final float[][] HX = {{8, 6, 7, 8}, {9, 10, 7, 9}, {6, 6, 7, 7, 6}};
  static final float[][] HY = {{8, 7, 6, 8}, {3, 4, 6, 3}, {6, 4, 4, 6, 6}};

  static XYPolygon xy(int... holes) {
    XYPolygon[] hs = new XYPolygon[holes.length];
    for (int i = 0; i < holes.length; i++) hs[i] = new XYPolygon(HX[holes[i]], HY[holes[i]]);
    return new XYPolygon(SX, SY, hs);
  }

  static double[] d(float[] f) {
    double[] r = new double[f.length];
    for (int i = 0; i < f.length; i++) r[i] = f[i];
    return r;
  }

  public void testControlsTwoHoles() {
    Tessellator.tessellate(xy(0, 1), false);
    Tessellator.tessellate(xy(0, 2), false);
    Tessellator.tessellate(xy(1, 2), false);
  }

  public void testControlSameHolesOtherOrder() {
    // the same three rings with the square hole first tessellate
    Tessellator.tessellate(xy(2, 0, 1), false);
  }

  public void testXYShapeThreeHolesSharingOneVertex() {
    XYShape.createIndexableFields("shape", xy(0, 1, 2)); // must not throw
  }

  public void testLatLonShapeThreeHolesSharingOneVertex() {
    Polygon[] hs = new Polygon[3];
    for (int i = 0; i < 3; i++) hs[i] = new Polygon(d(HY[i]), d(HX[i]));
    LatLonShape.createIndexableFields("shape", new Polygon(d(SY), d(SX), hs)); // must not throw
  }
}
