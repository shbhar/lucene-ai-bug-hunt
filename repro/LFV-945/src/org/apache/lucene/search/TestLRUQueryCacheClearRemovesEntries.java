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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLRUQueryCacheClearRemovesEntries extends LuceneTestCase {

  public void testClearQueryAndClearCoreCacheKeyRemoveEntries() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new StringField("f", "a", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LRUQueryCache cache = new LRUQueryCache(100, 1 << 20, context -> true, 1f);
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
        Query query = new TermQuery(new Term("f", "a"));
        IndexReader.CacheKey coreKey =
            reader.leaves().get(0).reader().getCoreCacheHelper().getKey();

        searcher.search(new ConstantScoreQuery(query), 1);
        cache.clearQuery(query);
        long sizeAfterClearQuery = cache.getCacheSize();

        cache.cleanUp();
        searcher.search(new ConstantScoreQuery(query), 1);
        cache.clearCoreCacheKey(coreKey);
        long sizeAfterClearCoreCacheKey = cache.getCacheSize();

        // expected 0 / 0 (javadocs: "Remove all cache entries for the given ..."); actual 1 / 1
        assertEquals(
            "cache size after clearQuery / after clearCoreCacheKey",
            "0 / 0",
            sizeAfterClearQuery + " / " + sizeAfterClearCoreCacheKey);
      }
    }
  }
}
