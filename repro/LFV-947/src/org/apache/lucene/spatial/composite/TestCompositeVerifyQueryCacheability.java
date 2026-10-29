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
package org.apache.lucene.spatial.composite;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.spatial.util.ShapeValuesPredicate;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;

public class TestCompositeVerifyQueryCacheability extends LuceneTestCase {

  public void testCachedCountAfterDocValuesUpdate() throws IOException {
    SpatialContext ctx = SpatialContext.GEO;
    SerializedDVStrategy shapes = new SerializedDVStrategy(ctx, "shape");
    Query query =
        new CompositeVerifyQuery(
            NumericDocValuesField.newSlowExactQuery("v", 1),
            new ShapeValuesPredicate(
                shapes.makeShapeValueSource(),
                SpatialOperation.Intersects,
                ctx.getShapeFactory().rect(-10, 10, -10, 10)));
    LRUQueryCache cache = new LRUQueryCache(100, 1 << 20, leaf -> true, Float.POSITIVE_INFINITY);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new StringField("id", "0", Field.Store.NO));
      doc.add(new NumericDocValuesField("v", 1));
      for (Field f : shapes.createIndexableFields(ctx.getShapeFactory().pointXY(0, 0))) {
        doc.add(f);
      }
      w.addDocument(doc);
      try (DirectoryReader before = DirectoryReader.open(w)) {
        cachingSearcher(before, cache).count(query);
        w.updateNumericDocValue(new Term("id", "0"), "v", 2);
        try (DirectoryReader after = DirectoryReader.openIfChanged(before, w)) {
          // expected 0 hits (doc 0 now has v=2); actual 1: the pre-update cache entry is served
          assertEquals(0, cachingSearcher(after, cache).count(query));
        }
      }
    }
  }

  private static IndexSearcher cachingSearcher(DirectoryReader reader, LRUQueryCache cache) {
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setQueryCache(cache);
    searcher.setQueryCachingPolicy(
        new QueryCachingPolicy() {
          @Override
          public void onUse(Query query) {}

          @Override
          public boolean shouldCache(Query query) {
            return true;
          }
        });
    return searcher;
  }
}
