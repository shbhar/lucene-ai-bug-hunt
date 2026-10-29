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
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTessellatorSharedVertexLaterHoleBridge extends LuceneTestCase {

  public void testPointInsideHoleDoesNotIntersect() throws Exception {
    XYPolygon holeA = new XYPolygon(new float[] {2, 5, 4, 2}, new float[] {5, 6, 8, 5});
    XYPolygon holeB = new XYPolygon(new float[] {6, 8, 8, 6}, new float[] {5, 4, 6, 5});
    XYPolygon polygon =
        new XYPolygon(
            new float[] {0, 2, 0, 10, 10, 0}, new float[] {0, 5, 10, 10, 0, 0}, holeA, holeB);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : XYShape.createIndexableFields("shape", polygon)) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Query q = XYShape.newPointQuery("shape", QueryRelation.INTERSECTS, new float[] {3.75f, 6.5f});
        // (3.75, 6.5) is strictly inside hole A, which touches the outer ring at (2,5): expected 0 hits, actual 1
        assertEquals(0, new IndexSearcher(r).count(q));
      }
    }
  }
}
