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
 * Valid polygons whose three holes all meet at one vertex must be indexable whatever the order in
 * which the holes are listed. Indexes via XYShape/LatLonShape; Tessellator only checks the area.
 */
public class TestShapeHolesMeetingAtOneVertex extends LuceneTestCase {

  private static final int[][] ORDERS = {
    {0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}
  };

  // Small XY polygon: clockwise triangle of area 65; three counter-clockwise holes that touch
  // each other only at P = (7,6) and do not touch the outer ring. Polygon area 65 - 6 = 59.
  private static final float[] OUTER_X = {0, 7, 12, 0}, OUTER_Y = {0, 12, 2, 0};
  private static final float[][][] HOLES_XY = {
    {{8, 6, 7, 8}, {8, 7, 6, 8}}, // A: triangle, area 1.5
    {{9, 10, 7, 9}, {3, 4, 6, 3}}, // B: triangle, area 2.5, its left-most vertex is P
    {{6, 6, 7, 7, 6}, {6, 4, 4, 6, 6}} // C: 1 x 2 rectangle, area 2, top-right corner P
  };
  private static final String[] NAMES = {"A", "B", "C"};

  /** XYShape.createIndexableFields over the 6 hole orders of the small polygon. */
  public void testXYShapeEveryHoleOrder() {
    List<String> rejected = new ArrayList<>();
    for (int[] order : ORDERS) {
      String name = NAMES[order[0]] + NAMES[order[1]] + NAMES[order[2]];
      XYPolygon[] holes = new XYPolygon[3];
      for (int i = 0; i < 3; i++) {
        holes[i] = new XYPolygon(HOLES_XY[order[i]][0], HOLES_XY[order[i]][1]);
      }
      XYPolygon polygon = new XYPolygon(OUTER_X, OUTER_Y, holes);
      try {
        int fields = XYShape.createIndexableFields("shape", polygon).length;
        double area = 0;
        for (Tessellator.Triangle t : Tessellator.tessellate(polygon, false)) {
          area +=
              Math.abs(
                      (t.getX(1) - t.getX(0)) * (t.getY(2) - t.getY(0))
                          - (t.getX(2) - t.getX(0)) * (t.getY(1) - t.getY(0)))
                  / 2;
        }
        System.out.println("XY  holes " + name + ": " + fields + " triangles, area " + area);
        assertEquals("area of the triangles for hole order " + name, 59.0, area, 1e-9);
      } catch (IllegalArgumentException e) {
        System.out.println("XY  holes " + name + ": REJECTED: " + e.getMessage());
        rejected.add(name);
      }
    }
    assertTrue("valid polygon rejected for hole orders " + rejected, rejected.isEmpty());
  }

  // Upstream TestTessellator#testComplexPolygon41 (LUCENE-9400): three holes meet at one vertex.
  private static final double[][] SHELL_LON_LAT = {
    {-1.569137181294115, 54.4855283059375}, {-1.5692505240440333, 54.48535373128068},
    {-1.5684753656387294, 54.48534438253056}, {-1.568606793880459, 54.485674703738624},
    {-1.5694141387939453, 54.48611720532629}, {-1.569137181294115, 54.4855283059375}
  };
  private static final double[][][] HOLES_LON_LAT = {
    {
      {-1.569137181294115, 54.4855283059375}, {-1.5690783030431206, 54.48545352137167},
      {-1.5689449291711688, 54.48547663706703}, {-1.569137181294115, 54.4855283059375}
    },
    {
      {-1.5689449291711688, 54.48547663706703}, {-1.5689437289004642, 54.48535482680399},
      {-1.5687730514221028, 54.48538045082698}, {-1.5689449291711688, 54.48547663706703}
    },
    {
      {-1.5689449291711688, 54.48547663706703}, {-1.5689879483854345, 54.485580118416785},
      {-1.5687756358893499, 54.485612860811244}, {-1.568765285875931, 54.485496217554285},
      {-1.5689449291711688, 54.48547663706703}
    }
  };

  private static Polygon ring(double[][] lonLat, Polygon... holes) {
    double[] lats = new double[lonLat.length], lons = new double[lonLat.length];
    for (int i = 0; i < lonLat.length; i++) {
      lons[i] = lonLat[i][0];
      lats[i] = lonLat[i][1];
    }
    return new Polygon(lats, lons, holes);
  }

  /** LatLonShape.createIndexableFields over the 6 hole orders of upstream's LUCENE-9400 polygon. */
  public void testLatLonShapeLucene9400PolygonEveryHoleOrder() {
    List<String> rejected = new ArrayList<>();
    for (int[] order : ORDERS) {
      String name = "(" + order[0] + "," + order[1] + "," + order[2] + ")";
      Polygon[] holes = new Polygon[3];
      for (int i = 0; i < 3; i++) {
        holes[i] = ring(HOLES_LON_LAT[order[i]]);
      }
      try {
        int fields = LatLonShape.createIndexableFields("shape", ring(SHELL_LON_LAT, holes)).length;
        System.out.println("LUCENE-9400 holes " + name + ": " + fields + " triangles");
      } catch (IllegalArgumentException e) {
        System.out.println("LUCENE-9400 holes " + name + ": REJECTED: " + e.getMessage());
        rejected.add(name);
      }
    }
    assertTrue("valid polygon rejected for hole orders " + rejected, rejected.isEmpty());
  }
}
