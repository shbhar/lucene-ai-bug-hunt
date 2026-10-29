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
package org.apache.lucene.facet.range;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestRangeFastMatchQueryCache extends LuceneTestCase {

  public void testCachedRangeQuerySeesFastMatchDocValuesUpdate() throws Exception {
    Query query =
        new LongRange("r", 0, true, 100, true)
            .getQuery(
                NumericDocValuesField.newSlowExactQuery("inStock", 1),
                LongValuesSource.fromLongField("price"));
    LRUQueryCache cache = new LRUQueryCache(1000, 1 << 20, ctx -> true, Float.MAX_VALUE);
    QueryCachingPolicy alwaysCache =
        new QueryCachingPolicy() {
          @Override
          public void onUse(Query q) {}

          @Override
          public boolean shouldCache(Query q) {
            return true;
          }
        };
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new StringField("id", "0", Field.Store.NO));
      doc.add(new NumericDocValuesField("inStock", 1));
      doc.add(new NumericDocValuesField("price", 10));
      w.addDocument(doc);
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        IndexSearcher s1 = new IndexSearcher(r1);
        s1.setQueryCache(cache);
        s1.setQueryCachingPolicy(alwaysCache);
        s1.count(query);
        w.updateNumericDocValue(new Term("id", "0"), "inStock", 0);
        try (DirectoryReader r2 = DirectoryReader.openIfChanged(r1, w)) {
          IndexSearcher s2 = new IndexSearcher(r2);
          s2.setQueryCache(cache);
          s2.setQueryCachingPolicy(alwaysCache);
          // expected 0 hits (inStock is now 0); actual 1, the cached pre-update result
          assertEquals("cached count after the in-place inStock update", 0, s2.count(query));
        }
      }
    }
  }
}
