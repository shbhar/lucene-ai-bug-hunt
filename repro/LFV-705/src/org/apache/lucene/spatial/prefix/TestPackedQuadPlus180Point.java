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
package org.apache.lucene.spatial.prefix;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.spatial.prefix.tree.PackedQuadPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;

public class TestPackedQuadPlus180Point extends LuceneTestCase {

  public void testIntersectsSamePointAtPlus180() throws Exception {
    RecursivePrefixTreeStrategy strategy =
        new RecursivePrefixTreeStrategy(new PackedQuadPrefixTree(SpatialContext.GEO, 8), "geo");
    Point p = SpatialContext.GEO.getShapeFactory().pointXY(180, 10);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      for (Field f : strategy.createIndexableFields(p)) {
        doc.add(f);
      }
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        // the document at Pt(180,10) must match Intersects(Pt(180,10)): expected 1 hit, got 0
        assertEquals(
            1,
            new IndexSearcher(r)
                .count(strategy.makeQuery(new SpatialArgs(SpatialOperation.Intersects, p))));
      }
    }
  }
}
