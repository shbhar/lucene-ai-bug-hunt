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
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.search.DummyTotalHitCountCollector;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLRUQueryCacheManySegments extends LuceneTestCase {

  private static final QueryCachingPolicy ALWAYS_CACHE =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  public void testFilterOverSeventeenSegmentsIsCached() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig config = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, config)) {
        for (int i = 0; i < 17; i++) {
          Document doc = new Document();
          doc.add(new StringField("f", "x", Field.Store.NO));
          w.addDocument(doc);
          w.flush();
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LRUQueryCache cache =
            new LRUQueryCache(256, 64 << 20, ctx -> true, Float.POSITIVE_INFINITY);
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(cache);
        searcher.setQueryCachingPolicy(ALWAYS_CACHE);
        Query filter = new TermQuery(new Term("f", "x"));
        searcher.search(filter, DummyTotalHitCountCollector.createManager());
        searcher.search(filter, DummyTotalHitCountCollector.createManager());
        // expected 17 (17 of 256 entries); actual 0: one query's entries share a 16-entry partition
        assertEquals("cache hits on the second search", 17, cache.getHitCount());
      }
    }
  }
}
