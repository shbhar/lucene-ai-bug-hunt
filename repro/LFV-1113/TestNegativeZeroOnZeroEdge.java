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
package org.apache.lucene.spatial;

import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.spatial.bbox.BBoxStrategy;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.vector.PointVectorStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Rectangle;

public class TestNegativeZeroOnZeroEdge extends LuceneTestCase {

  public void testPointAtNegativeZeroOnZeroEdge() throws Exception {
    SpatialContext ctx = SpatialContext.GEO;
    Point point = ctx.makePoint(-0.0, 5);
    Rectangle rect = ctx.makeRectangle(0.0, 10, 0, 10);
    assertTrue(SpatialOperation.IsWithin.evaluate(point, rect));
    SpatialStrategy bbox = BBoxStrategy.newInstance(ctx, "bbox");
    SpatialStrategy pv = PointVectorStrategy.newInstance(ctx, "pv");
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : bbox.createIndexableFields(point)) doc.add(f);
        for (Field f : pv.createIndexableFields(point)) doc.add(f);
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        int[] hits = {
          s.count(bbox.makeQuery(new SpatialArgs(SpatialOperation.Intersects, rect))),
          s.count(bbox.makeQuery(new SpatialArgs(SpatialOperation.IsWithin, rect))),
          s.count(pv.makeQuery(new SpatialArgs(SpatialOperation.Intersects, rect))),
          s.count(pv.makeQuery(new SpatialArgs(SpatialOperation.IsWithin, rect)))
        };
        // hits of BBox Intersects, BBox IsWithin, PointVector Intersects, PointVector IsWithin
        assertEquals("[1, 1, 1, 1]", Arrays.toString(hits));
      }
    }
  }
}
