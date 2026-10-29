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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

public class TestLRUQueryCacheCallbacksOverlap extends LuceneTestCase {

  public void testInsertionCallbacksOfTwoPartitionsRunAtTheSameTime() throws Exception {
    int defaultPartitionMask = 15;
    TermQuery first = new TermQuery(new Term("f", "t0"));
    TermQuery second = null;
    for (int i = 1; second == null; i++) {
      TermQuery q = new TermQuery(new Term("f", "t" + i));
      if ((q.hashCode() & defaultPartitionMask) != (first.hashCode() & defaultPartitionMask)) {
        second = q;
      }
    }
    CountDownLatch firstInside = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    AtomicInteger inside = new AtomicInteger();
    AtomicInteger maxInside = new AtomicInteger();
    LRUQueryCache cache =
        new LRUQueryCache(100, 1 << 20, context -> true, 1f) {
          @Override
          protected void onCacheEntryInserted(Object coreKey, Query query, long ramBytesUsed) {
            maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
            try {
              if (query.equals(first)) {
                firstInside.countDown();
                releaseFirst.await(10, TimeUnit.SECONDS);
              }
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            } finally {
              inside.decrementAndGet();
            }
          }
        };
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new StringField("f", "t0", Field.Store.NO));
        doc.add(new StringField("f", second.getTerm().text(), Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(cache);
        searcher.setQueryCachingPolicy(ALWAYS_CACHE);
        Thread t = new Thread(() -> search(searcher, first));
        t.start();
        assertTrue(firstInside.await(10, TimeUnit.SECONDS));
        search(searcher, second);
        releaseFirst.countDown();
        t.join();
      }
    }
    // expected (javadoc: "called synchronously and under a lock"): 1 at a time; actual: 2
    assertEquals("insertion callbacks running at the same time", 1, maxInside.get());
  }

  private static final QueryCachingPolicy ALWAYS_CACHE =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  private static void search(IndexSearcher searcher, Query query) {
    try {
      searcher.search(new ConstantScoreQuery(query), 1);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
