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
package org.apache.lucene.search;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Probe for the fix options, not a bug test: does the query still use the cache where caching is
 * safe (a segment without deletions, a searcher without a timeout, no filter or a cacheable one)?
 * Expected: passes on stock and with the narrow fix; fails with isCacheable == false.
 */
public class TestVectorSimilarityQueryStillCached extends LuceneTestCase {

  private static final QueryCachingPolicy ALWAYS_CACHE =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  private static IndexSearcher searcher(DirectoryReader reader, QueryCache cache) {
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setQueryCache(cache);
    searcher.setQueryCachingPolicy(ALWAYS_CACHE);
    return searcher;
  }

  /** No deletions, no timeout, a cacheable filter: the entry is cached and reused. */
  public void testCachedWhenSafe() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      for (int i = 0; i < 100; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
        doc.add(new StringField("all", "x", Field.Store.NO));
        doc.add(
            new KnnFloatVectorField("v", new float[] {i, 1}, VectorSimilarityFunction.EUCLIDEAN));
        w.addDocument(doc);
      }
      w.forceMerge(1);
      w.commit();
      try (DirectoryReader older = DirectoryReader.open(w)) {
        Query[] queries = {
          new FloatVectorSimilarityQuery.Adaptive("v", new float[] {0, 1}, 0f, 1f, null),
          new FloatVectorSimilarityQuery.Adaptive("v", new float[] {0, 1}, 0f, 0.5f, null),
          new FloatVectorSimilarityQuery.Adaptive(
              "v", new float[] {0, 1}, 0f, 1f, new TermQuery(new Term("all", "x")))
        };
        LRUQueryCache cache = new LRUQueryCache(1000, 1 << 24, _ -> true, Float.POSITIVE_INFINITY);
        for (Query q : queries) {
          Query query = new ConstantScoreQuery(q);
          assertEquals(100, searcher(older, cache).count(query));
          assertEquals(100, searcher(older, cache).count(query));
        }
        assertTrue("cache entries: " + cache.getCacheCount(), cache.getCacheCount() >= 3);
        assertTrue("cache hits: " + cache.getHitCount(), cache.getHitCount() >= 3);

        // the safe direction: the older reader (no deletions) filled the cache, the newer reader
        // (doc 0 deleted) applies its own live docs on top of the cached entry
        w.deleteDocuments(new Term("id", "0"));
        try (DirectoryReader newer = DirectoryReader.openIfChanged(older, w)) {
          for (Query q : queries) {
            assertEquals(99, searcher(newer, cache).count(new ConstantScoreQuery(q)));
          }
        }
      }
    }
  }
}
