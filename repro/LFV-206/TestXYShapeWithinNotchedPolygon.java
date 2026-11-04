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
 * The query polygon is a 4x4 square with a V-shaped notch cut into its top edge: (0,0) (4,0) (4,4)
 * (2,1) (0,4). The segment (0,4)-(4,4) and the triangle (0,4) (4,4) (2,1) have every vertex on the
 * polygon's boundary, but both run through the notch, which is outside the polygon: (2,4) and (2,3)
 * are not in it. So neither shape is within the polygon.
 */
public class TestXYShapeWithinNotchedPolygon extends LuceneTestCase {

  public void testShapesAcrossTheNotchAreNotWithin() throws Exception {
    XYPolygon notched =
        new XYPolygon(new float[] {0, 4, 4, 2, 0, 0}, new float[] {0, 0, 4, 1, 4, 0});
    XYLine segment = new XYLine(new float[] {0, 4}, new float[] {4, 4});
    XYPolygon notch = new XYPolygon(new float[] {0, 4, 2, 0}, new float[] {4, 4, 1, 4});
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (Field[] fields :
            new Field[][] {
              XYShape.createIndexableFields("shape", segment),
              XYShape.createIndexableFields("shape", notch)
            }) {
          Document doc = new Document();
          for (Field f : fields) {
            doc.add(f);
          }
          w.addDocument(doc);
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        // both shapes touch the polygon
        assertEquals(
            2, searcher.count(XYShape.newPolygonQuery("shape", QueryRelation.INTERSECTS, notched)));
        // neither lies within it
        assertEquals(
            0, searcher.count(XYShape.newPolygonQuery("shape", QueryRelation.WITHIN, notched)));
      }
    }
  }
}
