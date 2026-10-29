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

public class TestXYShapePointQueryAtPolygonVertex extends LuceneTestCase {

  public void testPointQueryAtVertexOfClockwiseTriangle() throws Exception {
    XYPolygon triangle = new XYPolygon(new float[] {0, 1, 3, 0}, new float[] {1, 2, 3, 1});
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      for (Field f : XYShape.createIndexableFields("shape", triangle)) {
        doc.add(f);
      }
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        // (1,2) is a vertex of the triangle: expected 1 hit, actual 0
        assertEquals(
            "INTERSECTS point query at vertex (1,2)",
            1,
            searcher.count(
                XYShape.newPointQuery("shape", QueryRelation.INTERSECTS, new float[] {1, 2})));
      }
    }
  }
}
