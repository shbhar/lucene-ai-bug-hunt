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
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.Tessellator;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A triangle with three triangular/rectangular holes that all meet at the single point P = (7, 6).
 * Every pair of holes touches only at P, no hole touches the outer ring, all holes lie strictly
 * inside the outer ring. The Tessellator class javadoc lists "Holes may only touch at one vertex"
 * among the valid-polygon requirements, so this polygon is valid input and must tessellate, and
 * whether it tessellates must not depend on the order in which the holes are given.
 *
 * <p>Uses only public API: XYPolygon / Polygon constructors, Tessellator.tessellate, and
 * XYShape / LatLonShape.createIndexableFields.
 */
public class TestThreeHolesMeetAtOneVertexV1 extends LuceneTestCase {

  // outer ring, clockwise: (0,0) (7,12) (12,2); area 65
  private static final float[] OX = {0, 7, 12, 0};
  private static final float[] OY = {0, 12, 2, 0};

  // holes, counter-clockwise, all containing P = (7,6)
  // A: triangle (8,8) (6,7) (7,6), area 1.5, sector at P between directions 63.4 and 135 degrees
  private static final float[][] A = {{8, 6, 7, 8}, {8, 7, 6, 8}};
  // B: triangle (9,3) (10,4) (7,6), area 2.5, left-most vertex is P, sector -56.3 .. -33.7 degrees
  private static final float[][] B = {{9, 10, 7, 9}, {3, 4, 6, 3}};
  // C: square [6,7]x[4,6] (6,6) (6,4) (7,4) (7,6), area 2, sector 180 .. 270 degrees
  private static final float[][] C = {{6, 6, 7, 7, 6}, {6, 4, 4, 6, 6}};

  private static final float[][][] HOLES = {A, B, C};
  private static final String[] NAMES = {"A", "B", "C"};
  private static final double EXPECTED_AREA = 65 - 1.5 - 2.5 - 2; // 59

  private static XYPolygon xy(int... order) {
    XYPolygon[] holes = new XYPolygon[order.length];
    for (int i = 0; i < order.length; i++) {
      float[][] h = HOLES[order[i]];
      holes[i] = new XYPolygon(h[0].clone(), h[1].clone());
    }
    return new XYPolygon(OX.clone(), OY.clone(), holes);
  }

  private static double[] toD(float[] f) {
    double[] d = new double[f.length];
    for (int i = 0; i < f.length; i++) {
      d[i] = f[i];
    }
    return d;
  }

  /** lat = y, lon = x (small degree values, well inside the valid range). */
  private static Polygon latLon(int... order) {
    Polygon[] holes = new Polygon[order.length];
    for (int i = 0; i < order.length; i++) {
      float[][] h = HOLES[order[i]];
      holes[i] = new Polygon(toD(h[1]), toD(h[0]));
    }
    return new Polygon(toD(OY), toD(OX), holes);
  }

  private static String name(int[] order) {
    StringBuilder sb = new StringBuilder();
    for (int o : order) {
      sb.append(NAMES[o]);
    }
    return sb.toString();
  }

  private static double area(List<Tessellator.Triangle> tris) {
    double s = 0;
    for (Tessellator.Triangle t : tris) {
      double ax = t.getX(0), ay = t.getY(0), bx = t.getX(1), by = t.getY(1);
      double cx = t.getX(2), cy = t.getY(2);
      s += Math.abs((bx - ax) * (cy - ay) - (cx - ax) * (by - ay)) / 2;
    }
    return s;
  }

  private static final int[][] PERMS = {
    {0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}
  };

  /** The order A, B, C through the default XYShape indexing entry point. */
  public void testXYShapeOrderABC() {
    Field[] fields = XYShape.createIndexableFields("f", xy(0, 1, 2));
    assertTrue(fields.length > 0);
  }

  /** The same polygon as lat/lon through the default LatLonShape indexing entry point. */
  public void testLatLonShapeOrderABC() {
    Field[] fields = LatLonShape.createIndexableFields("f", latLon(0, 1, 2));
    assertTrue(fields.length > 0);
  }

  /**
   * Every hole order must tessellate (with and without the self-intersection check), and every
   * accepted tessellation must have the exact area 59.
   */
  public void testAllHoleOrdersXY() {
    List<String> failures = new ArrayList<>();
    List<String> accepted = new ArrayList<>();
    for (boolean check : new boolean[] {false, true}) {
      for (int[] p : PERMS) {
        String n = name(p) + (check ? "/check" : "/nocheck");
        try {
          List<Tessellator.Triangle> t = Tessellator.tessellate(xy(p), check);
          double a = area(t);
          if (Math.abs(a - EXPECTED_AREA) > 1e-6) {
            failures.add(n + ": area " + a + " != " + EXPECTED_AREA);
          } else {
            accepted.add(n);
          }
        } catch (IllegalArgumentException e) {
          failures.add(n + ": " + e.getMessage());
        }
      }
    }
    assertTrue(
        "hole orders rejected or wrong: " + failures + " ; accepted: " + accepted,
        failures.isEmpty());
  }

  /** Same for the lat/lon variant (area check skipped because of encoding; only acceptance). */
  public void testAllHoleOrdersLatLon() {
    List<String> failures = new ArrayList<>();
    List<String> accepted = new ArrayList<>();
    for (int[] p : PERMS) {
      try {
        Tessellator.tessellate(latLon(p), false);
        accepted.add(name(p));
      } catch (IllegalArgumentException e) {
        failures.add(name(p) + ": " + e.getMessage());
      }
    }
    assertTrue("hole orders rejected: " + failures + " ; accepted: " + accepted, failures.isEmpty());
  }

  /** Control: every two-hole and one-hole subset, in both orders, tessellates with the right area. */
  public void testControlSubsets() {
    int[][] subsets = {{0}, {1}, {2}, {0, 1}, {1, 0}, {0, 2}, {2, 0}, {1, 2}, {2, 1}};
    for (int[] s : subsets) {
      double expected = 65;
      for (int i : s) {
        expected -= i == 0 ? 1.5 : i == 1 ? 2.5 : 2;
      }
      List<Tessellator.Triangle> t = Tessellator.tessellate(xy(s), true);
      assertEquals(name(s), expected, area(t), 1e-6);
    }
  }
}
