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
package org.apache.lucene.document;

import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestShapeDocValuesQueryCachedAfterUpdate extends LuceneTestCase {

  private static final QueryCachingPolicy ALWAYS_CACHE =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  public void testCachedCountSeesDocValuesUpdate() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new StringField("id", "0", Field.Store.NO));
      doc.add(LatLonShape.createDocValueField("shape", 0, 0));
      w.addDocument(doc);
      Query query =
          LatLonShape.newSlowDocValuesBoxQuery("shape", QueryRelation.INTERSECTS, -1, 1, -1, 1);
      LRUQueryCache cache = new LRUQueryCache(1000, 1 << 20, ctx -> true, Float.MAX_VALUE);
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        IndexSearcher s1 = new IndexSearcher(r1);
        s1.setQueryCache(cache);
        s1.setQueryCachingPolicy(ALWAYS_CACHE);
        assertEquals(1, s1.count(query));
        w.updateDocValues(new Term("id", "0"), LatLonShape.createDocValueField("shape", 10, 10));
        try (DirectoryReader r2 = DirectoryReader.openIfChanged(r1, w)) {
          IndexSearcher s2 = new IndexSearcher(r2);
          s2.setQueryCache(cache);
          s2.setQueryCachingPolicy(ALWAYS_CACHE);
          // expected 0 (the only shape was moved out of the box); actual 1, served from the cache
          assertEquals("count after the doc-values update", 0, s2.count(query));
        }
      }
    }
  }
}
