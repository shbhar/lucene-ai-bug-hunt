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
package org.apache.lucene.spatial.vector;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Shape;

public class TestPointVectorDatelineEdge extends LuceneTestCase {

  public void testPointAtPlus180InBoxFromMinus180() throws Exception {
    SpatialContext ctx = SpatialContext.GEO;
    PointVectorStrategy strategy = PointVectorStrategy.newInstance(ctx, "pv");
    Shape box = ctx.getShapeFactory().rect(-180, -170, -10, 10);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      for (Field f : strategy.createIndexableFields(ctx.getShapeFactory().pointXY(180, 0))) {
        doc.add(f);
      }
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        // geo: x=+180 is the box's west edge x=-180 (same meridian): expected 1 hit, actual 0
        assertEquals(
            "Intersects rect(-180,-170,-10,10) with Pt(180,0) indexed",
            1,
            searcher.count(
                strategy.makeQuery(new SpatialArgs(SpatialOperation.Intersects, box))));
      }
    }
  }
}
