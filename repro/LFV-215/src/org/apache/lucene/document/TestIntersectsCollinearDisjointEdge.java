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
import org.apache.lucene.geo.Component2D;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.XYGeometry;
import org.apache.lucene.geo.XYLine;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The indexed triangle (3,0) (4,0) (1.5,4) and the query square [0,2]x[0,2] are disjoint: the line
 * x + 0.375 y = 3 separates them (at most 2.75 on the square, at least 3 on the triangle). But the
 * triangle edge (3,0)-(4,0) lies on the same line (y = 0) as the square's edge (0,0)-(2,0), the
 * triangle's bounding box overlaps that edge, and two collinear segments are reported as crossing
 * even when they do not overlap. INTERSECTS then matches the triangle and DISJOINT drops it.
 */
public class TestIntersectsCollinearDisjointEdge extends LuceneTestCase {

  private static final float[] SQ_X = {0, 2, 2, 0, 0};
  private static final float[] SQ_Y = {0, 0, 2, 2, 0};
  private static final float[] TRI_X = {3, 4, 1.5f, 3};
  private static final float[] TRI_Y = {0, 0, 4, 0};

  private static int count(Field[] fields, Query query) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : fields) doc.add(f);
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        return new IndexSearcher(reader).count(query);
      }
    }
  }

  public void testSeparatingLine() {
    double maxOnSquare = Double.NEGATIVE_INFINITY, minOnTriangle = Double.POSITIVE_INFINITY;
    for (int i = 0; i < 4; i++) maxOnSquare = Math.max(maxOnSquare, SQ_X[i] + 0.375 * SQ_Y[i]);
    for (int i = 0; i < 3; i++) minOnTriangle = Math.min(minOnTriangle, TRI_X[i] + 0.375 * TRI_Y[i]);
    assertEquals(2.75, maxOnSquare, 0);
    assertEquals(3.0, minOnTriangle, 0);
  }

  public void testComponentLevel() {
    Component2D square = XYGeometry.create(new XYPolygon(SQ_X, SQ_Y));
    assertFalse(square.intersectsTriangle(3, 0, 4, 0, 1.5, 4));
    Component2D line = XYGeometry.create(new XYLine(new float[] {0, 2}, new float[] {0, 0}));
    assertFalse(line.intersectsTriangle(3, 0, 4, 0, 1.5, 4));
  }

  public void testXYPolygonQuery() throws Exception {
    Field[] fields = XYShape.createIndexableFields("shape", new XYPolygon(TRI_X, TRI_Y));
    XYPolygon square = new XYPolygon(SQ_X, SQ_Y);
    assertEquals(0, count(fields, XYShape.newPolygonQuery("shape", QueryRelation.INTERSECTS, square)));
    assertEquals(1, count(fields, XYShape.newPolygonQuery("shape", QueryRelation.DISJOINT, square)));
  }

  public void testXYLineQuery() throws Exception {
    Field[] fields = XYShape.createIndexableFields("shape", new XYPolygon(TRI_X, TRI_Y));
    XYLine bottom = new XYLine(new float[] {0, 2}, new float[] {0, 0});
    assertEquals(0, count(fields, XYShape.newLineQuery("shape", QueryRelation.INTERSECTS, bottom)));
  }

  public void testLatLonPolygonQuery() throws Exception {
    // same numbers in degrees: x = longitude, y = latitude (latitude 0 is exact in the encoding)
    Polygon tri = new Polygon(new double[] {0, 0, 4, 0}, new double[] {3, 4, 1.5, 3});
    Polygon square = new Polygon(new double[] {0, 0, 2, 2, 0}, new double[] {0, 2, 2, 0, 0});
    Field[] fields = LatLonShape.createIndexableFields("shape", tri);
    assertEquals(
        0, count(fields, LatLonShape.newPolygonQuery("shape", QueryRelation.INTERSECTS, square)));
    assertEquals(
        1, count(fields, LatLonShape.newPolygonQuery("shape", QueryRelation.DISJOINT, square)));
  }
}
