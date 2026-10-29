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
package org.apache.lucene.spatial.util;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queries.function.FunctionScoreQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.spatial.bbox.BBoxStrategy;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;

public class TestShapeAreaValueSourceMultiplier extends LuceneTestCase {

  public void testDisjunctionOfTwoMultipliersScoresTheSum() throws Exception {
    SpatialContext ctx = SpatialContext.GEO;
    BBoxStrategy strategy = BBoxStrategy.newInstance(ctx, "bbox");
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field field :
            strategy.createIndexableFields(ctx.getShapeFactory().rect(0, 20, 40, 80))) {
          doc.add(field);
        }
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query area1 =
            new FunctionScoreQuery(
                MatchAllDocsQuery.INSTANCE,
                new ShapeAreaValueSource(strategy.makeShapeValueSource(), ctx, false, 1));
        Query area3 =
            new FunctionScoreQuery(
                MatchAllDocsQuery.INSTANCE,
                new ShapeAreaValueSource(strategy.makeShapeValueSource(), ctx, false, 3));
        Query query =
            new BooleanQuery.Builder().add(area1, Occur.SHOULD).add(area3, Occur.SHOULD).build();
        // expected: 800 x 1 + 800 x 3 = 3200; actual: 1600 (the clauses are equal and merged)
        assertEquals(3200f, new IndexSearcher(reader).search(query, 1).scoreDocs[0].score, 0f);
      }
    }
  }
}
