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
import org.apache.lucene.search.Explanation;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;

public class TestBBoxOverlapRatioExplainQueryRatio extends LuceneTestCase {

  public void testQueryFactorRatioIsQueryRatio() throws Exception {
    SpatialContext ctx = SpatialContext.GEO;
    BBoxStrategy strategy = BBoxStrategy.newInstance(ctx, "bbox");
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(ctx.getShapeFactory().rect(10, 30, 0, 10))) {
          doc.add(f);
        }
        writer.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Explanation exp =
            strategy
                .makeOverlapRatioValueSource(ctx.getShapeFactory().rect(0, 40, -20, 20), 0.5)
                .explain(reader.leaves().get(0), 0, Explanation.match(0f, "score"));
        Explanation queryFactor = exp.getDetails()[1];
        // queryFactor's "ratio" must be queryRatio 200/1600 = 0.125; main shows targetRatio 1.0
        assertEquals(
            queryFactor.getDescription() + " ratio",
            0.125f,
            queryFactor.getDetails()[0].getValue().floatValue(),
            0f);
      }
    }
  }
}
