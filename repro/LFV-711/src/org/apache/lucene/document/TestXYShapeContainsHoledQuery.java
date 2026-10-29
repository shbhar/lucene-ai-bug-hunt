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
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestXYShapeContainsHoledQuery extends LuceneTestCase {

  public void testContainsChecksQueryHoleEdges() throws Exception {
    // the doc's hole removes (5,5), a point of the query: CONTAINS expected 0 hits, actual 1
    XYPolygon query =
        new XYPolygon(
            new float[] {0, 0, 10, 10, 0},
            new float[] {0, 10, 10, 0, 0},
            new XYPolygon(new float[] {1, 3, 3, 1, 1}, new float[] {4, 4, 6, 6, 4}),
            new XYPolygon(new float[] {7, 9, 9, 7, 7}, new float[] {4, 4, 6, 6, 4}));
    XYPolygon doc =
        new XYPolygon(
            new float[] {-1, -1, 11, 11, -1},
            new float[] {-1, 11, 11, -1, -1},
            new XYPolygon(new float[] {2, 8, 8, 2, 2}, new float[] {4.5f, 4.5f, 5.5f, 5.5f, 4.5f}));
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document d = new Document();
        for (Field f : XYShape.createIndexableFields("f", doc)) {
          d.add(f);
        }
        w.addDocument(d);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        assertEquals(
            "doc does not cover the query point (5,5)",
            0,
            s.count(XYShape.newPointQuery("f", QueryRelation.INTERSECTS, new float[] {5, 5})));
        assertEquals(
            "doc must not CONTAIN the query",
            0,
            s.count(XYShape.newPolygonQuery("f", QueryRelation.CONTAINS, query)));
      }
    }
  }
}
