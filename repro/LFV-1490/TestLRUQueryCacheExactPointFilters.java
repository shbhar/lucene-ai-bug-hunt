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
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLRUQueryCacheExactPointFilters extends LuceneTestCase {

  // Expected: 100 exact filters on one segment fit a 1,000-entry cache, so their third use is
  // served from it (100 hits, as on 10.x); actual: 0 hits.
  public void testRepeatedExactPointFiltersAreServedFromCache() throws Exception {
    Query[] filters = new Query[100];
    for (int value = 0; value < filters.length; value++) {
      filters[value] = new ConstantScoreQuery(IntPoint.newExactQuery("f", value));
    }
    try (Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 10_000; i++) {
          Document doc = new Document();
          doc.add(new IntPoint("f", i % filters.length));
          writer.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LRUQueryCache cache = new LRUQueryCache(1_000, 50 * 1024 * 1024);
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(cache);
        searcher.setQueryCachingPolicy(new UsageTrackingQueryCachingPolicy());
        for (int use = 0; use < 3; use++) {
          for (Query filter : filters) {
            searcher.search(filter, 1);
          }
        }
        assertEquals(filters.length, cache.getHitCount());
      }
    }
  }
}
