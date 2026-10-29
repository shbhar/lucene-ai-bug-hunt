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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLRUQueryCacheClearOnClear extends LuceneTestCase {

  public void testInsertBeforeOnClearIsNotLost() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new StringField("f", "t", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        AtomicLong entries = new AtomicLong();
        CountDownLatch inOnClear = new CountDownLatch(1);
        CountDownLatch inserted = new CountDownLatch(1);
        LRUQueryCache cache =
            new LRUQueryCache(10, 1 << 20, context -> true, 1f, 1, null) {
              @Override
              protected void onCacheEntryInserted(Object coreKey, Query query, long ramBytesUsed) {
                entries.incrementAndGet();
              }

              @Override
              protected void onClear() {
                inOnClear.countDown();
                try {
                  inserted.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                  throw new AssertionError(e);
                }
                entries.set(0);
              }
            };
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
        Thread clearer = new Thread(cache::clear);
        clearer.start();
        inOnClear.await();
        searcher.search(new ConstantScoreQuery(new TermQuery(new Term("f", "t"))), 1);
        inserted.countDown();
        clearer.join();
        // expected: 1, the entry cached before onClear() is still cached; actual: 0
        assertEquals("callback entry count", cache.getCacheSize(), entries.get());
      }
    }
  }
}
