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

/**
 * Prints Polygon2D.withinTriangle for edge cases near the query's holes (component level). In each
 * triangle only the edge b-c belongs to the indexed shape (bc = true); a-b and c-a are tessellation
 * edges. Run it on stock and with a fix and compare the lines.
 */
public class WithinHoleBoundaryProbe {

  static Component2D poly(float[] x, float[] y, float[]... holes) {
    XYPolygon[] h = new XYPolygon[holes.length / 2];
    for (int i = 0; i < h.length; i++) {
      h[i] = new XYPolygon(holes[2 * i], holes[2 * i + 1]);
    }
    return Polygon2D.create(new XYPolygon(x, y, h));
  }

  static void row(String query, Component2D p, String what, double ax, double ay, double bx, double by,
      double cx, double cy) {
    System.out.printf(
        "%-12s %-62s b-c=(%s,%s)-(%s,%s)  %s%n",
        query, what, bx, by, cx, cy, p.withinTriangle(ax, ay, false, bx, by, true, cx, cy, false));
  }

  public static void main(String[] args) {
    System.out.println("# " + (args.length > 0 ? args[0] : "") + "  (a vertex; flagged edge b-c; result)");
    float[] sx = {0, 0, 10, 10, 0}, sy = {0, 10, 10, 0, 0};
    // two holes A = [1,3]x[4,6], B = [7,9]x[4,6]
    Component2D two =
        poly(sx, sy, new float[] {1, 3, 3, 1, 1}, new float[] {4, 4, 6, 6, 4},
            new float[] {7, 9, 9, 7, 7}, new float[] {4, 4, 6, 6, 4});
    row("two-holes", two, "hole A -> hole B across land (the bug)", -1, -1, 2, 4.5, 8, 4.5);
    row("two-holes", two, "inside hole A", -1, -1, 1.5, 4.5, 2.5, 4.5);
    row("two-holes", two, "inside hole A, ends on hole A's right edge", -1, -1, 2, 5, 3, 5);
    row("two-holes", two, "inside hole A, ends at hole A's corner (3,4)", -1, -1, 2, 5, 3, 4);
    row("two-holes", two, "along hole A's bottom edge (1,4)-(3,4)", -1, -1, 1, 4, 3, 4);
    row("two-holes", two, "along part of hole A's bottom edge (1.5,4)-(2.5,4)", -1, -1, 1.5, 4, 2.5, 4);
    row("two-holes", two, "hole A -> land (end on land)", -1, -1, 2, 4.5, 5, 4.5);
    row("two-holes", two, "hole A -> outside, across the outer ring", -1, -1, 2, 5, -0.5, 5);
    row("two-holes", two, "land only", -1, -1, 4, 2, 6, 2);
    row("two-holes", two, "outside only", -1, 12, -1, 11, 11, 11);
    // one convex hole A
    Component2D one = poly(sx, sy, new float[] {1, 3, 3, 1, 1}, new float[] {4, 4, 6, 6, 4});
    row("one-hole", one, "inside the only (convex) hole", -1, -1, 1.5, 4.5, 2.5, 5.5);
    // two holes that touch at the vertex (4,4): H1 = [2,4]x[2,4], H2 = [4,6]x[4,6]
    Component2D touch =
        poly(sx, sy, new float[] {2, 4, 4, 2, 2}, new float[] {2, 2, 4, 4, 2},
            new float[] {4, 6, 6, 4, 4}, new float[] {4, 4, 6, 6, 4});
    row("touching", touch, "H1 -> H2 through the shared corner (4,4) only", -1, 11, 3, 3, 5, 5);
    row("touching", touch, "H1 -> H2 across land, not through the corner", -1, 11, 3, 3.5, 5, 5.5);
    row("touching", touch, "inside H1, ends at the shared corner (4,4)", -1, 11, 3, 3, 4, 4);
    row("touching", touch, "inside H1", -1, 11, 2.5, 2.5, 3.5, 3.5);
    System.out.println(
        "# contains(4,4)=" + touch.contains(4, 4) + " contains(5,4.5)=" + two.contains(5, 4.5));
  }
}
