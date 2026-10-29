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

public class TestHolesTouchingSameOuterEdgeContains extends LuceneTestCase {

  public void testBoxCrossingOuterEdgeIsNotContained() throws Exception {
    XYPolygon holeA = new XYPolygon(new float[] {2, 5, 3, 2}, new float[] {0, 1, 3, 0});
    XYPolygon holeB = new XYPolygon(new float[] {5, 8, 6, 5}, new float[] {0, 1, 3, 0});
    XYPolygon polygon =
        new XYPolygon(
            new float[] {0, 7, 12, 5, 2, 0}, new float[] {0, 12, 0, 0, 0, 0}, holeA, holeB);

    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : XYShape.createIndexableFields("shape", polygon)) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        // expected 0: the box x [3,4], y [-0.5,0.3] sticks out below the outer edge y = 0
        int hits =
            new IndexSearcher(r)
                .count(XYShape.newBoxQuery("shape", QueryRelation.CONTAINS, 3, 4, -0.5f, 0.3f));
        assertEquals("CONTAINS hits for a box that leaves the polygon", 0, hits);
      }
    }
  }
}
