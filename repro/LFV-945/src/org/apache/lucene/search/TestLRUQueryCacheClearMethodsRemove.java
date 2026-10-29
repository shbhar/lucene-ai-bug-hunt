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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * LRUQueryCache#clearQuery and #clearCoreCacheKey are documented as "Remove all cache entries for the given query" /
 * "... for the given core cache key". After the call returns, the entry should no longer be counted by
 * getCacheSize() and the next search with the query should be a cache miss. clear() is the control.
 *
 * <p>Uses public API only.
 */
public class TestLRUQueryCacheClearMethodsRemove extends LuceneTestCase {

  private static final QueryCachingPolicy ALWAYS_CACHE =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  private enum Op {
    CLEAR_QUERY,
    CLEAR_CORE_CACHE_KEY,
    CLEAR
  }

  private void doTest(Op op) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        for (int i = 0; i < 100; i++) {
          Document doc = new Document();
          doc.add(new StringField("color", i % 2 == 0 ? "blue" : "red", Field.Store.NO));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        LRUQueryCache cache =
            new LRUQueryCache(1000, 10 * 1024 * 1024, context -> true, Float.POSITIVE_INFINITY);
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(cache);
        searcher.setQueryCachingPolicy(ALWAYS_CACHE);

        Query blue = new TermQuery(new Term("color", "blue"));
        assertEquals(50, searcher.search(new ConstantScoreQuery(blue), 10).totalHits.value());
        assertEquals("the query is cached on the only segment", 1, cache.getCacheSize());
        long hitsBefore = cache.getHitCount();

        switch (op) {
          case CLEAR_QUERY -> cache.clearQuery(blue);
          case CLEAR_CORE_CACHE_KEY ->
              cache.clearCoreCacheKey(reader.leaves().get(0).reader().getCoreCacheHelper().getKey());
          case CLEAR -> cache.clear();
        }

        long sizeAfter = cache.getCacheSize();
        assertEquals(50, searcher.search(new ConstantScoreQuery(blue), 10).totalHits.value());
        long newHits = cache.getHitCount() - hitsBefore;
        assertTrue(
            op
                + " returned, so the entry should be removed: cacheSize after the call="
                + sizeAfter
                + " (expected 0), cache hits on the next search="
                + newHits
                + " (expected 0)",
            sizeAfter == 0 && newHits == 0);
      }
    }
  }

  public void testClearQueryRemovesEntries() throws Exception {
    doTest(Op.CLEAR_QUERY);
  }

  public void testClearCoreCacheKeyRemovesEntries() throws Exception {
    doTest(Op.CLEAR_CORE_CACHE_KEY);
  }

  /** Control: clear() removes synchronously on every version. */
  public void testClearRemovesEntries() throws Exception {
    doTest(Op.CLEAR);
  }
}
