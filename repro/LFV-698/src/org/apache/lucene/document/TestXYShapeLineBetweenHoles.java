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
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestXYShapeLineBetweenHoles extends LuceneTestCase {

  public void testLineFromHoleToHoleIntersects() throws Exception {
    XYPolygon holeA = new XYPolygon(new float[] {1, 3, 3, 1, 1}, new float[] {4, 4, 6, 6, 4});
    XYPolygon holeB = new XYPolygon(new float[] {7, 9, 9, 7, 7}, new float[] {4, 4, 6, 6, 4});
    XYPolygon square =
        new XYPolygon(new float[] {0, 0, 10, 10, 0}, new float[] {0, 10, 10, 0, 0}, holeA, holeB);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      for (Field f :
          XYShape.createIndexableFields("f", new XYLine(new float[] {2, 8}, new float[] {5, 5}))) {
        doc.add(f);
      }
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        // expected 1: the line crosses the polygon between the holes at (5,5); actual 0
        assertEquals(
            1,
            new IndexSearcher(r)
                .count(XYShape.newPolygonQuery("f", QueryRelation.INTERSECTS, square)));
      }
    }
  }
}
