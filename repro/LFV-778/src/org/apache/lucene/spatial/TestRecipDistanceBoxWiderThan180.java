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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.spatial.vector.PointVectorStrategy;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Rectangle;

public class TestRecipDistanceBoxWiderThan180 extends LuceneTestCase {

  public void testScoreAtFurthestEdge() throws Exception {
    SpatialContext ctx = SpatialContext.GEO;
    SpatialStrategy strategy = PointVectorStrategy.newInstance(ctx, "pv");
    Rectangle box = ctx.getShapeFactory().rect(-170, 170, -10, 10);
    Point edge = ctx.getShapeFactory().pointXY(170, 0);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(edge)) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        DoubleValues v =
            strategy.makeRecipDistanceValueSource(box).getValues(reader.leaves().get(0), null);
        assertTrue(v.advanceExact(0));
        // Expected ~0.1 (documented; 17 / (170 + 17) = 0.091), within 0.10 +- 0.09; actual 0.0082
        assertEquals("score at the furthest edge", 0.10, v.doubleValue(), 0.09);
      }
    }
  }
}
