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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;

/**
 * Two rectangles that both cross the dateline can overlap in two separate longitude pieces (one
 * containing the dateline, one elsewhere). The overlap-ratio relevance of BBoxStrategy only counts
 * the piece that contains the dateline, so intersectionArea, and with it the score, is too small.
 * Public API only (BBoxStrategy indexing + makeOverlapRatioValueSource).
 */
public class TestOverlapRatioTwoPieceIntersection extends LuceneTestCase {

  public void testBothCrossDatelineTwoPieceOverlap() throws IOException {
    SpatialContext ctx = SpatialContext.GEO;
    BBoxStrategy strategy = BBoxStrategy.newInstance(ctx, "bbox");
    // target covers [170,180] U [-180,20] (width 210); query covers [10,180] U [-180,-170] (180)
    Rectangle target = ctx.getShapeFactory().rect(170, 20, -10, 10);
    Rectangle query = ctx.getShapeFactory().rect(10, -170, -10, 10);
    // their intersection: [170,180] U [-180,-170] (20 degrees) plus [10,20] (10 degrees) = 30 x 20
    double intersectionArea = 30.0 * 20.0;
    double p = 0.5;
    double expected =
        intersectionArea / (180.0 * 20.0) * p + intersectionArea / (210.0 * 20.0) * (1 - p);
    try (Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(target)) {
          doc.add(f);
        }
        writer.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        DoubleValuesSource overlap = strategy.makeOverlapRatioValueSource(query, p);
        LeafReaderContext leaf = reader.leaves().get(0);
        DoubleValues values = overlap.getValues(leaf, null);
        assertTrue(values.advanceExact(0));
        assertEquals(expected, values.doubleValue(), 1e-9);
      }
    }
  }
}
