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
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Characterization with the query cache configuration that IndexSearcher uses by default in 10.x
 * releases (an LRUQueryCache with its default segment-size predicate, shared by all searchers, and
 * a UsageTrackingQueryCachingPolicy), set explicitly because the test framework replaces the static
 * defaults. One 20,000-doc segment; every round uses a fresh searcher with an expired timeout.
 * Prints the count per round; fails if the first round over-counts.
 */
public class TestTimedOutCountWithLruCache extends LuceneTestCase {

  public void testRounds() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 20_000; i++) {
          Document d = new Document();
          if (i % 10 != 9) d.add(new StringField("f", "a", Field.Store.NO));
          if (i % 20 == 0 || i % 100 == 99) d.add(new StringField("f", "b", Field.Store.NO));
          w.addDocument(d);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Query q = new BooleanQuery.Builder()
            .add(new TermQuery(new Term("f", "a")), BooleanClause.Occur.SHOULD)
            .add(new TermQuery(new Term("f", "b")), BooleanClause.Occur.SHOULD)
            .build();
        IndexSearcher plain = new IndexSearcher(r);
        plain.setQueryCache(null);
        final int trueCount = plain.count(q);
        LRUQueryCache cache = new LRUQueryCache(1000, 32 * 1024 * 1024);
        UsageTrackingQueryCachingPolicy policy = new UsageTrackingQueryCachingPolicy();
        StringBuilder rounds = new StringBuilder();
        int first = -1;
        for (int round = 0; round < 10; round++) {
          IndexSearcher s = new IndexSearcher(r);
          s.setQueryCache(cache);
          s.setQueryCachingPolicy(policy);
          s.setTimeout(() -> true);
          int c = s.count(q);
          if (round == 0) first = c;
          rounds.append(" r").append(round).append('=').append(c);
        }
        String msg = "maxDoc=" + r.maxDoc() + " trueCount=" + trueCount + " timed-out counts:" + rounds
            + " cachedQueries=" + cache.getCacheCount();
        System.out.println(msg);
        assertTrue(msg, first <= trueCount);
      }
    }
  }
}
