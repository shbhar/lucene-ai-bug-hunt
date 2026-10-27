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
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

/** Pre-9.0 variant (old LuceneTestCase package, 2-argument LRUQueryCache, 12,000 docs). */
public class TestRangeQueryFastMatchCachingPre9 extends LuceneTestCase {

  private static final int NUM_DOCS = 12_000;

  private static final QueryCachingPolicy ALWAYS_CACHE =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  private void doTest(Query rangeQuery) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(null).setMergePolicy(NoMergePolicy.INSTANCE);
    IndexWriter w = new IndexWriter(dir, iwc);
    for (int i = 0; i < NUM_DOCS; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
      doc.add(new NumericDocValuesField("inStock", i < 5 ? 1 : 0));
      doc.add(new NumericDocValuesField("price", 10));
      w.addDocument(doc);
    }
    w.forceMerge(1);
    w.commit();
    LRUQueryCache cache = new LRUQueryCache(1000, 1 << 20);
    DirectoryReader r1 = DirectoryReader.open(w);
    DirectoryReader r2 = null;
    try {
      assertEquals(1, r1.leaves().size());
      IndexSearcher s1 = new IndexSearcher(r1);
      s1.setQueryCache(cache);
      s1.setQueryCachingPolicy(ALWAYS_CACHE);
      assertEquals(5, s1.count(rangeQuery));
      w.updateNumericDocValue(new Term("id", "0"), "inStock", 0);
      r2 = DirectoryReader.openIfChanged(r1, w);
      assertNotNull(r2);
      IndexSearcher uncached = new IndexSearcher(r2);
      uncached.setQueryCache(null);
      assertEquals(4, uncached.count(rangeQuery));
      IndexSearcher s2 = new IndexSearcher(r2);
      s2.setQueryCache(cache);
      s2.setQueryCachingPolicy(ALWAYS_CACHE);
      assertEquals("cached result differs from uncached result", 4, s2.count(rangeQuery));
    } finally {
      r1.close();
      if (r2 != null) r2.close();
      w.close();
      dir.close();
    }
  }

  public void testLongRangeLongValuesSource() throws Exception {
    doTest(
        new LongRange("r", 0, true, 100, true)
            .getQuery(
                NumericDocValuesField.newSlowExactQuery("inStock", 1),
                LongValuesSource.fromLongField("price")));
  }

  public void testDoubleRangeDoubleValuesSource() throws Exception {
    doTest(
        new DoubleRange("r", 0, true, 100, true)
            .getQuery(
                NumericDocValuesField.newSlowExactQuery("inStock", 1),
                DoubleValuesSource.fromLongField("price")));
  }

  /** Control: the fast-match query on its own (passes from 7.2.0 on). */
  public void testFastMatchAloneControl() throws Exception {
    doTest(NumericDocValuesField.newSlowExactQuery("inStock", 1));
  }
}
