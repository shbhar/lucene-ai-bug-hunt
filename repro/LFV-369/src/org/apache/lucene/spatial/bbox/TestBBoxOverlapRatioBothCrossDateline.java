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
package org.apache.lucene.spatial.bbox;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;

public class TestBBoxOverlapRatioBothCrossDateline extends LuceneTestCase {

  public void testTwoPieceOverlap() throws Exception {
    SpatialContext ctx = SpatialContext.GEO;
    BBoxStrategy strategy = BBoxStrategy.newInstance(ctx, "bbox");
    Rectangle query = ctx.getShapeFactory().rect(10, -170, -10, 10);
    Rectangle target = ctx.getShapeFactory().rect(170, 20, -10, 10);
    // overlap = 170..-170 (20 deg) + 10..20 (10 deg) = 30 x 20; query 180 x 20, target 210 x 20
    double expected = (30.0 * 20) / (180 * 20) * 0.5 + (30.0 * 20) / (210 * 20) * 0.5;
    try (Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(target)) {
          doc.add(f);
        }
        writer.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        DoubleValues values =
            strategy.makeOverlapRatioValueSource(query, 0.5).getValues(reader.leaves().get(0), null);
        assertTrue(values.advanceExact(0));
        assertEquals("overlap ratio, intersection 30 x 20", expected, values.doubleValue(), 1e-9);
      }
    }
  }
}
