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

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLRUQueryCacheEntryCallbackBytes extends LuceneTestCase {

  public void testEvictedEntryReportsItsInsertedBytes() throws Exception {
    Map<Object, Long> inserted = new HashMap<>();
    Map<Object, Long> evicted = new HashMap<>();
    LRUQueryCache cache =
        new LRUQueryCache(100, 1 << 20, leaf -> true, 10f) {
          @Override
          protected void onCacheEntryInserted(Object readerCoreKey, Query query, long ramBytes) {
            inserted.put(readerCoreKey, ramBytes);
          }

          @Override
          protected void onCacheEntryEvicted(Object readerCoreKey, Query query, long ramBytes) {
            evicted.put(readerCoreKey, ramBytes);
          }
        };
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
        Document doc = new Document();
        doc.add(new StringField("color", "red", Field.Store.NO));
        w.addDocument(doc);
        w.commit();
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
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
        searcher.search(new ConstantScoreQuery(new TermQuery(new Term("color", "red"))), 1);
        IndexReader.CacheKey segment0 =
            reader.leaves().get(0).reader().getCoreCacheHelper().getKey();
        cache.clearCoreCacheKey(segment0);
        cache.cleanUp();
        // javadoc: both callbacks report "the total RAM cost of this entry", so the amounts match
        assertEquals(
            "segment 0 entry: onCacheEntryInserted vs onCacheEntryEvicted ramBytesUsed",
            inserted.get(segment0),
            evicted.get(segment0));
      }
    }
  }
}
