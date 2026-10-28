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

import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.Tessellator;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The shell-vertex variant: the outer ring (0,0) (7,12) (12,0) (5,0) (2,0) lists both touch points
 * as vertices, so each hole, (2,0) (5,1) (3,3) and (5,0) (8,1) (6,3), shares one vertex with the
 * outer ring. The piece (2,0)-(5,0) of the bottom edge y = 0 is part of the polygon's boundary.
 */
public class TestHolesTouchingSameOuterEdgeShellVertices extends LuceneTestCase {

  private static XYPolygon xyPolygon() {
    XYPolygon holeA = new XYPolygon(new float[] {2, 5, 3, 2}, new float[] {0, 1, 3, 0});
    XYPolygon holeB = new XYPolygon(new float[] {5, 8, 6, 5}, new float[] {0, 1, 3, 0});
    return new XYPolygon(
        new float[] {0, 7, 12, 5, 2, 0}, new float[] {0, 12, 0, 0, 0, 0}, holeA, holeB);
  }

  /** The same polygon in degrees, lat = y and lon = x. */
  private static Polygon latLonPolygon() {
    Polygon holeA = new Polygon(new double[] {0, 1, 3, 0}, new double[] {2, 5, 3, 2});
    Polygon holeB = new Polygon(new double[] {0, 1, 3, 0}, new double[] {5, 8, 6, 5});
    return new Polygon(
        new double[] {0, 12, 0, 0, 0, 0}, new double[] {0, 7, 12, 5, 2, 0}, holeA, holeB);
  }

  /** Every triangle edge that lies on the outer edge y = 0 belongs to the polygon. */
  public void testTriangleEdgesOnOuterEdgeAreFromPolygon() {
    for (Tessellator.Triangle t : Tessellator.tessellate(xyPolygon(), random().nextBoolean())) {
      for (int k = 0; k < 3; k++) {
        int n = (k + 1) % 3;
        if (t.getY(k) == 0 && t.getY(n) == 0) {
          assertTrue(
              "edge (" + t.getX(k) + ",0)-(" + t.getX(n) + ",0) is not flagged as a polygon edge",
              t.isEdgefromPolygon(k));
        }
      }
    }
  }

  public void testXYShapeContains() throws Exception {
    Field[] fields = XYShape.createIndexableFields("s", xyPolygon());
    // inside the polygon: contained
    assertEquals(
        1, count(fields, XYShape.newBoxQuery("s", QueryRelation.CONTAINS, 3, 4, 0.05f, 0.3f)));
    // sticks out below y = 0 between the two touch points: not contained
    assertEquals(
        0, count(fields, XYShape.newBoxQuery("s", QueryRelation.CONTAINS, 3, 4, -0.5f, 0.3f)));
  }

  public void testLatLonShapeContains() throws Exception {
    Field[] fields = LatLonShape.createIndexableFields("s", latLonPolygon());
    // inside the polygon: contained
    assertEquals(
        1, count(fields, LatLonShape.newBoxQuery("s", QueryRelation.CONTAINS, 0.05, 0.3, 3, 4)));
    // sticks out below lat = 0 between the two touch points: not contained
    assertEquals(
        0, count(fields, LatLonShape.newBoxQuery("s", QueryRelation.CONTAINS, -0.5, 0.3, 3, 4)));
  }

  private int count(Field[] fields, Query query) throws Exception {
    try (Directory dir = newDirectory();
        RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
      Document doc = new Document();
      for (Field f : fields) {
        doc.add(f);
      }
      w.addDocument(doc);
      try (IndexReader r = w.getReader()) {
        return newSearcher(r).count(query);
      }
    }
  }
}
