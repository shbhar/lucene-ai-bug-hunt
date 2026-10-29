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
package org.apache.lucene.spatial.prefix.tree;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.spatial4j.Geo3dSpatialContextFactory;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;

public class TestS2PointOnCellEdgeContains extends LuceneTestCase {

  public void testContainsPointOnEquator() throws Exception {
    SpatialContext ctx = new Geo3dSpatialContextFactory().newSpatialContext();
    RecursivePrefixTreeStrategy strategy =
        new RecursivePrefixTreeStrategy(new S2PrefixTree(ctx, 8), "geo");
    Point p = ctx.getShapeFactory().pointXY(10, 0);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      for (Field f : strategy.createIndexableFields(p)) {
        doc.add(f);
      }
      writer.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(writer)) {
        // expected: Contains(p) finds the one document indexed with p; actual: 0 hits
        assertEquals(
            1,
            new IndexSearcher(reader)
                .count(strategy.makeQuery(new SpatialArgs(SpatialOperation.Contains, p))));
      }
    }
  }
}
