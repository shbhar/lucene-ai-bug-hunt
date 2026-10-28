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

import java.util.Arrays;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * An XYShape point query placed exactly on a vertex of an indexed polygon must find the polygon, as
 * the same query does on the polygon's other vertices and on the same polygon moved by an exact
 * translation. Uses only public API.
 */
public class TestXYShapePointAtPolygonVertex extends LuceneTestCase {

  /** hits of a point query at each vertex (the ring is closed: the last point repeats the first) */
  private static int[] vertexHits(XYPolygon polygon, QueryRelation relation) throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      Document doc = new Document();
      for (Field f : XYShape.createIndexableFields("shape", polygon)) {
        doc.add(f);
      }
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = newSearcher(r);
        int n = polygon.numPoints() - 1;
        int[] hits = new int[n];
        for (int i = 0; i < n; i++) {
          float[] p = {polygon.getPolyX(i), polygon.getPolyY(i)};
          hits[i] = s.count(XYShape.newPointQuery("shape", relation, p));
        }
        return hits;
      }
    }
  }

  private static XYPolygon translate(XYPolygon p, float dx, float dy) {
    float[] x = p.getPolyX(), y = p.getPolyY();
    for (int i = 0; i < x.length; i++) {
      x[i] += dx; // exact for these small integers
      y[i] += dy;
    }
    return new XYPolygon(x, y);
  }

  /** a small triangle near the origin: (0,1) (1,2) (3,3) */
  public void testTriangleNearOrigin() throws Exception {
    XYPolygon triangle = new XYPolygon(new float[] {0, 1, 3, 0}, new float[] {1, 2, 3, 1});
    // control: the same triangle moved by (8, 8) matches at all three vertices
    assertEquals(
        "translated triangle, INTERSECTS",
        "[1, 1, 1]",
        Arrays.toString(vertexHits(translate(triangle, 8, 8), QueryRelation.INTERSECTS)));
    assertEquals(
        "point query at the vertices (0,1) (1,2) (3,3), INTERSECTS",
        "[1, 1, 1]",
        Arrays.toString(vertexHits(triangle, QueryRelation.INTERSECTS)));
  }

  /** the same query relation, the other way round: a vertex is not DISJOINT from its polygon */
  public void testTriangleNearOriginDisjoint() throws Exception {
    XYPolygon triangle = new XYPolygon(new float[] {0, 1, 3, 0}, new float[] {1, 2, 3, 1});
    assertEquals(
        "point query at the vertices (0,1) (1,2) (3,3), DISJOINT",
        "[0, 0, 0]",
        Arrays.toString(vertexHits(triangle, QueryRelation.DISJOINT)));
  }

  /** a hexagon in projected (metre-like) coordinates; vertex 3 is (518885, 4395432) */
  public void testHexagonProjectedCoordinates() throws Exception {
    XYPolygon hexagon =
        new XYPolygon(
            new float[] {539509, 522792, 517494, 518885, 528386, 528562, 539509},
            new float[] {4392295, 4390728, 4393155, 4395432, 4407722, 4411071, 4392295});
    assertEquals(
        "point query at each of the six vertices, INTERSECTS",
        "[1, 1, 1, 1, 1, 1]",
        Arrays.toString(vertexHits(hexagon, QueryRelation.INTERSECTS)));
  }
}
