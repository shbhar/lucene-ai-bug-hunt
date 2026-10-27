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

import java.io.IOException;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.Line;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.XYLine;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The query polygon is the square [0,10] x [0,10] with two holes, A = [1,3] x [4,6] and B = [7,9] x
 * [4,6]. The strip between the holes (for example the point (5,5)) belongs to the polygon.
 *
 * <p>Doc 0 is a line from (2,5), inside hole A, to (8,5), inside hole B: it runs through (5,5).
 * Doc 1 is the box [2,8] x [4.5,5.5], whose four corners lie in the holes and which covers (5,5).
 * Doc 2 is the point (5,5) (control). All three intersect the polygon, none is disjoint from it.
 * Outer rings are clockwise and holes counter-clockwise, as the Polygon javadoc requires.
 */
public class TestPolygonHolesIntersects extends LuceneTestCase {

  public void testXYShapeLineAndBoxBetweenHoles() throws IOException {
    XYPolygon holeA = new XYPolygon(new float[] {1, 3, 3, 1, 1}, new float[] {4, 4, 6, 6, 4});
    XYPolygon holeB = new XYPolygon(new float[] {7, 9, 9, 7, 7}, new float[] {4, 4, 6, 6, 4});
    XYPolygon query =
        new XYPolygon(new float[] {0, 0, 10, 10, 0}, new float[] {0, 10, 10, 0, 0}, holeA, holeB);
    XYLine line = new XYLine(new float[] {2, 8}, new float[] {5, 5});
    XYPolygon box =
        new XYPolygon(new float[] {2, 2, 8, 8, 2}, new float[] {4.5f, 5.5f, 5.5f, 4.5f, 4.5f});

    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        add(w, XYShape.createIndexableFields("f", line));
        add(w, XYShape.createIndexableFields("f", box));
        add(w, XYShape.createIndexableFields("f", 5f, 5f));
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        int intersects = s.count(XYShape.newPolygonQuery("f", QueryRelation.INTERSECTS, query));
        int disjoint = s.count(XYShape.newPolygonQuery("f", QueryRelation.DISJOINT, query));
        assertEquals(
            "INTERSECTS must match the line, the box and the point (all pass through (5,5))",
            3,
            intersects);
        assertEquals("DISJOINT must match nothing", 0, disjoint);
      }
    }
  }

  public void testLatLonShapeLineAndBoxBetweenHoles() throws IOException {
    // same geometry with lon = x, lat = y (degrees)
    Polygon holeA = new Polygon(new double[] {4, 4, 6, 6, 4}, new double[] {1, 3, 3, 1, 1});
    Polygon holeB = new Polygon(new double[] {4, 4, 6, 6, 4}, new double[] {7, 9, 9, 7, 7});
    Polygon query =
        new Polygon(new double[] {0, 10, 10, 0, 0}, new double[] {0, 0, 10, 10, 0}, holeA, holeB);
    Line line = new Line(new double[] {5, 5}, new double[] {2, 8});
    Polygon box = new Polygon(new double[] {4.5, 5.5, 5.5, 4.5, 4.5}, new double[] {2, 2, 8, 8, 2});

    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        add(w, LatLonShape.createIndexableFields("f", line));
        add(w, LatLonShape.createIndexableFields("f", box));
        add(w, LatLonShape.createIndexableFields("f", 5, 5));
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        int intersects =
            s.count(LatLonShape.newPolygonQuery("f", QueryRelation.INTERSECTS, query));
        int disjoint = s.count(LatLonShape.newPolygonQuery("f", QueryRelation.DISJOINT, query));
        assertEquals(
            "INTERSECTS must match the line, the box and the point (all pass through (5,5))",
            3,
            intersects);
        assertEquals("DISJOINT must match nothing", 0, disjoint);
      }
    }
  }

  private static void add(IndexWriter w, Field[] fields) throws IOException {
    Document d = new Document();
    for (Field f : fields) {
      d.add(f);
    }
    w.addDocument(d);
  }
}
