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
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The query polygon is the square [0,10] x [0,10] with two holes, A = [1,3] x [4,6] and B = [7,9] x
 * [4,6]; the point (5,5) between the holes belongs to it.
 *
 * <p>Doc 0 is the square [-1,11] x [-1,11] with a hole [2,8] x [4.5,5.5]: the hole removes (5,5),
 * so doc 0 does NOT contain the query polygon. Doc 1 is the same square without a hole and does
 * contain it. CONTAINS must return doc 1 only. Outer rings are clockwise and holes
 * counter-clockwise, as the Polygon javadoc requires.
 */
public class TestContainsQueryPolygonHoles extends LuceneTestCase {

  public void testXYShapeContainsIgnoresQueryHoleEdges() throws IOException {
    XYPolygon query =
        new XYPolygon(
            new float[] {0, 0, 10, 10, 0},
            new float[] {0, 10, 10, 0, 0},
            new XYPolygon(new float[] {1, 3, 3, 1, 1}, new float[] {4, 4, 6, 6, 4}),
            new XYPolygon(new float[] {7, 9, 9, 7, 7}, new float[] {4, 4, 6, 6, 4}));
    XYPolygon docHole =
        new XYPolygon(new float[] {2, 8, 8, 2, 2}, new float[] {4.5f, 4.5f, 5.5f, 5.5f, 4.5f});
    XYPolygon doc0 =
        new XYPolygon(new float[] {-1, -1, 11, 11, -1}, new float[] {-1, 11, 11, -1, -1}, docHole);
    XYPolygon doc1 = new XYPolygon(new float[] {-1, -1, 11, 11, -1}, new float[] {-1, 11, 11, -1, -1});
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        add(w, XYShape.createIndexableFields("f", doc0));
        add(w, XYShape.createIndexableFields("f", doc1));
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        // sanity: (5,5) is in the query polygon, in doc 1 and not in doc 0
        assertEquals("[1]", hits(s, XYShape.newPointQuery("f", QueryRelation.INTERSECTS, new float[] {5, 5})));
        assertEquals("[1]", hits(s, XYShape.newPolygonQuery("f", QueryRelation.CONTAINS, query)));
      }
    }
  }

  public void testLatLonShapeContainsIgnoresQueryHoleEdges() throws IOException {
    // same geometry with lon = x, lat = y (degrees)
    Polygon query =
        new Polygon(
            new double[] {0, 10, 10, 0, 0},
            new double[] {0, 0, 10, 10, 0},
            new Polygon(new double[] {4, 4, 6, 6, 4}, new double[] {1, 3, 3, 1, 1}),
            new Polygon(new double[] {4, 4, 6, 6, 4}, new double[] {7, 9, 9, 7, 7}));
    Polygon docHole = new Polygon(new double[] {4.5, 4.5, 5.5, 5.5, 4.5}, new double[] {2, 8, 8, 2, 2});
    Polygon doc0 =
        new Polygon(new double[] {-1, 11, 11, -1, -1}, new double[] {-1, -1, 11, 11, -1}, docHole);
    Polygon doc1 = new Polygon(new double[] {-1, 11, 11, -1, -1}, new double[] {-1, -1, 11, 11, -1});
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        add(w, LatLonShape.createIndexableFields("f", doc0));
        add(w, LatLonShape.createIndexableFields("f", doc1));
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        assertEquals("[1]", hits(s, LatLonShape.newPointQuery("f", QueryRelation.INTERSECTS, new double[] {5, 5})));
        assertEquals("[1]", hits(s, LatLonShape.newPolygonQuery("f", QueryRelation.CONTAINS, query)));
      }
    }
  }

  private static String hits(IndexSearcher s, Query q) throws IOException {
    StringBuilder b = new StringBuilder("[");
    for (ScoreDoc sd : s.search(q, 10, new org.apache.lucene.search.Sort(org.apache.lucene.search.SortField.FIELD_DOC)).scoreDocs) {
      if (b.length() > 1) b.append(", ");
      b.append(sd.doc);
    }
    return b.append("]").toString();
  }

  private static void add(IndexWriter w, Field[] fields) throws IOException {
    Document d = new Document();
    for (Field f : fields) {
      d.add(f);
    }
    w.addDocument(d);
  }
}
