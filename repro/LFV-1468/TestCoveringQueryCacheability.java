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
package org.apache.lucene.sandbox.search;

import java.util.List;
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

public class TestCoveringQueryCacheability extends LuceneTestCase {

  public void testCachedCountAfterDocValuesUpdate() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    Document doc = new Document();
    doc.add(new StringField("id", "0", Field.Store.NO));
    doc.add(new NumericDocValuesField("x", 1));
    doc.add(new NumericDocValuesField("min", 1));
    w.addDocument(doc);
    Query query =
        new CoveringQuery(
            List.of(NumericDocValuesField.newSlowExactQuery("x", 1)),
            LongValuesSource.fromLongField("min"));
    LRUQueryCache cache = new LRUQueryCache(100, 1 << 20, _ -> true, Float.POSITIVE_INFINITY);
    QueryCachingPolicy alwaysCache =
        new QueryCachingPolicy() {
          @Override
          public void onUse(Query q) {}

          @Override
          public boolean shouldCache(Query q) {
            return true;
          }
        };

    DirectoryReader r1 = DirectoryReader.open(w);
    IndexSearcher s1 = new IndexSearcher(r1);
    s1.setQueryCache(cache);
    s1.setQueryCachingPolicy(alwaysCache);
    assertEquals(1, s1.count(query));

    w.updateNumericDocValue(new Term("id", "0"), "x", 2);
    DirectoryReader r2 = DirectoryReader.openIfChanged(r1, w);
    IndexSearcher s2 = new IndexSearcher(r2);
    s2.setQueryCache(cache);
    s2.setQueryCachingPolicy(alwaysCache);
    // expected 0 hits after x changed to 2; actual: the cache returns the stale 1
    assertEquals(0, s2.count(query));

    r2.close();
    r1.close();
    w.close();
    dir.close();
  }
}
