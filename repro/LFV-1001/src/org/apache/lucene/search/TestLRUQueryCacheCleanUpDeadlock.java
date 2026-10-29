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

import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.Test;

/** Plain JUnit: deadlocked threads cannot be stopped, which LuceneTestCase reports as a leak. */
public class TestLRUQueryCacheCleanUpDeadlock {

  /** Keeps its statistics under the cache's monitor; cleanUp() runs during an insertion. */
  static class StatsCache extends LRUQueryCache {
    long inserted;
    volatile Thread cleaner;

    StatsCache() {
      super(100, 1 << 20, context -> true, 1f, 1, null);
    }

    @Override
    protected void onCacheEntryInserted(Object readerCoreKey, Query query, long ramBytesUsed) {
      cleaner = new Thread(this::cleanUp);
      cleaner.setDaemon(true);
      cleaner.start();
      while (cleaner.isAlive() && cleaner.getState() != Thread.State.WAITING) {
        Thread.onSpinWait(); // until cleanUp() waits for the partition lock this search holds
      }
      synchronized (this) {
        inserted++;
      }
    }
  }

  @Test
  public void testCleanUpDuringInsertionCallback() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new StringField("f", "x", Field.Store.NO));
      w.addDocument(doc);
    }
    StatsCache cache = new StatsCache();
    cache.clearQuery(new TermQuery(new Term("f", "y"))); // gives cleanUp() something to do
    IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(dir));
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
    Query query = new ConstantScoreQuery(new TermQuery(new Term("f", "x")));
    Thread search =
        new Thread(
            () -> {
              try {
                searcher.search(query, 1);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
    search.setDaemon(true);
    search.start();
    search.join(10_000);
    // expected: the search finishes; actual: it blocks on the monitor held by cleanUp(), forever
    assertFalse(
        "expected the search to finish; after 10 s search="
            + search.getState()
            + ", cleanUp()="
            + cache.cleaner.getState(),
        search.isAlive());
  }
}
