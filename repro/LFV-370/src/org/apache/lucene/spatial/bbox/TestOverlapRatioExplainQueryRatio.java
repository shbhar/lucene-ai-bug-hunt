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
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;

/**
 * The explanation of BBoxStrategy's overlap-ratio relevance lists the target ratio under
 * "queryFactor", so the explanation does not add up (queryFactor != ratio * queryTargetProportion).
 * Public API only.
 */
public class TestOverlapRatioExplainQueryRatio extends LuceneTestCase {

  public void testQueryFactorDetailShowsQueryRatio() throws IOException {
    SpatialContext ctx = SpatialContext.GEO;
    BBoxStrategy strategy = BBoxStrategy.newInstance(ctx, "bbox");
    double p = 0.5;
    try (Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        // target 20x10 inside a 40x40 query: queryRatio = 200/1600 = 0.125, targetRatio = 1
        for (Field f : strategy.createIndexableFields(ctx.getShapeFactory().rect(10, 30, 0, 10))) {
          doc.add(f);
        }
        writer.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        DoubleValuesSource overlap =
            strategy.makeOverlapRatioValueSource(ctx.getShapeFactory().rect(0, 40, -20, 20), p);
        Explanation exp = overlap.explain(reader.leaves().get(0), 0, Explanation.match(0f, "score"));
        Explanation queryFactor = exp.getDetails()[1];
        assertEquals("queryFactor", queryFactor.getDescription());
        Explanation ratio = queryFactor.getDetails()[0];
        assertEquals("ratio", ratio.getDescription());
        assertEquals(0.125f, ratio.getValue().floatValue(), 0f);
        assertEquals(
            queryFactor.getValue().floatValue(), ratio.getValue().floatValue() * (float) p, 1e-6f);
      }
    }
  }
}
