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
import org.apache.lucene.geo.Tessellator;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Verifier variant: the same three holes (meeting only at P=(7,6)) in all 6 input orders, plus a
 * translated and a scaled copy (exact in float), through the public XYShape entry point. Every
 * order describes the same polygon, so every order must be accepted. Each accepted tessellation
 * must also cover exactly the polygon area (outer area minus hole areas).
 */
public class TestThreeHolesPermutations extends LuceneTestCase {

  static final float[] SX = {0, 7, 12, 0}, SY = {0, 12, 2, 0};
  static final float[][] HX = {{8, 6, 7, 8}, {9, 10, 7, 9}, {6, 6, 7, 7, 6}};
  static final float[][] HY = {{8, 7, 6, 8}, {3, 4, 6, 3}, {6, 4, 4, 6, 6}};
  static final int[][] PERMS = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};

  static float[] tr(float[] a, float scale, float off) {
    float[] r = new float[a.length];
    for (int i = 0; i < a.length; i++) r[i] = a[i] * scale + off;
    return r;
  }

  static double ringArea(float[] x, float[] y) {
    double s = 0;
    for (int i = 0; i < x.length - 1; i++) s += (double) x[i] * y[i + 1] - (double) x[i + 1] * y[i];
    return Math.abs(s) / 2;
  }

  static XYPolygon poly(int[] order, float scale, float off) {
    XYPolygon[] hs = new XYPolygon[order.length];
    for (int i = 0; i < order.length; i++) {
      hs[i] = new XYPolygon(tr(HX[order[i]], scale, off), tr(HY[order[i]], scale, off));
    }
    return new XYPolygon(tr(SX, scale, off), tr(SY, scale, off), hs);
  }

  private List<String> run(float scale, float off) {
    List<String> failed = new ArrayList<>();
    double expected = ringArea(tr(SX, scale, off), tr(SY, scale, off));
    for (int[] h : HX.length == 3 ? new int[][] {{0}, {1}, {2}} : new int[0][]) {
      expected -= ringArea(tr(HX[h[0]], scale, off), tr(HY[h[0]], scale, off));
    }
    for (int[] p : PERMS) {
      String name = "holes(" + p[0] + "," + p[1] + "," + p[2] + ") scale=" + scale + " off=" + off;
      try {
        XYShape.createIndexableFields("shape", poly(p, scale, off));
        List<Tessellator.Triangle> t = Tessellator.tessellate(poly(p, scale, off), false);
        double a = 0;
        for (Tessellator.Triangle tri : t) {
          a +=
              Math.abs(
                      (tri.getX(1) - tri.getX(0)) * (tri.getY(2) - tri.getY(0))
                          - (tri.getX(2) - tri.getX(0)) * (tri.getY(1) - tri.getY(0)))
                  / 2;
        }
        System.out.println("V3 OK   " + name + " triangles=" + t.size() + " area=" + a + " expected=" + expected);
        if (Math.abs(a - expected) > 1e-9 * Math.max(1, expected)) {
          failed.add(name + " area " + a + " != " + expected);
        }
      } catch (IllegalArgumentException e) {
        System.out.println("V3 FAIL " + name + " -> " + e.getMessage());
        failed.add(name + " -> " + e.getMessage());
      }
    }
    return failed;
  }

  public void testAllHoleOrders() {
    List<String> f = run(1f, 0f);
    assertTrue("hole orders rejected or wrong area: " + f, f.isEmpty());
  }

  public void testAllHoleOrdersTranslated() {
    List<String> f = run(1f, 100f);
    assertTrue("hole orders rejected or wrong area: " + f, f.isEmpty());
  }

  public void testAllHoleOrdersScaled() {
    List<String> f = run(0.5f, 0f);
    assertTrue("hole orders rejected or wrong area: " + f, f.isEmpty());
  }
}
