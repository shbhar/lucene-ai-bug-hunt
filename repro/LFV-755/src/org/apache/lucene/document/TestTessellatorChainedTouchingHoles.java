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
 * A valid L-shaped polygon with three triangular holes: holes 0 and 1 both touch the outer ring at
 * its reflex vertex V=(4,4), and hole 2 touches hole 1 at the vertex (3,4). Every touch is at one
 * vertex. XYShape/LatLonShape.createIndexableFields reject it with "Unable to Tessellate shape".
 *
 * <pre>
 *  outer ring (clockwise): (0,0) (0,12) (4,12) (4,4) (12,4) (12,0)
 *  hole 0 (ccw): (4,4) (1,2) (2,1)
 *  hole 1 (ccw): (3,4) (4,4) (3,5)
 *  hole 2 (ccw): (2,6) (1,5) (3,4)
 * </pre>
 */
public class TestTessellatorChainedTouchingHoles extends LuceneTestCase {

  static final float[] SX = {0, 0, 4, 4, 12, 12, 0}, SY = {0, 12, 12, 4, 4, 0, 0};
  static final float[][] HX = {{4, 1, 2, 4}, {3, 4, 3, 3}, {2, 1, 3, 2}};
  static final float[][] HY = {{4, 2, 1, 4}, {4, 4, 5, 4}, {6, 5, 4, 6}};

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

  public void testXYShapeChainedTouchingHoles() {
    XYShape.createIndexableFields("shape", xy(0, 1, 2)); // must not throw
  }

  public void testLatLonShapeChainedTouchingHoles() {
    Polygon[] hs = new Polygon[3];
    for (int i = 0; i < 3; i++) hs[i] = new Polygon(d(HY[i]), d(HX[i]));
    LatLonShape.createIndexableFields("shape", new Polygon(d(SY), d(SX), hs)); // must not throw
  }
}
