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
import org.apache.lucene.facet.MultiDoubleValuesSource;
import org.apache.lucene.facet.MultiLongValuesSource;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The range query of {@link LongRange#getQuery(Query, LongValuesSource)} and its three siblings
 * reads the fast-match query, but its Weight#isCacheable only asks the value source. When the
 * fast-match query reads a doc-values field that is updated in place, the query cache keeps
 * serving the result computed before the update.
 */
public class TestRangeQueryFastMatchCaching extends LuceneTestCase {

  private static final int NUM_DOCS = 20;

  private static final QueryCachingPolicy ALWAYS_CACHE =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  /** All docs have price 10; docs 0..4 are in stock. The range [0, 100] matches every price. */
  private void doTest(Query rangeQuery) throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < NUM_DOCS; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
          doc.add(new NumericDocValuesField("inStock", i < 5 ? 1 : 0));
          doc.add(new NumericDocValuesField("price", 10));
          w.addDocument(doc);
        }
        w.commit();

        LRUQueryCache cache = new LRUQueryCache(1000, 1 << 20, ctx -> true, Float.MAX_VALUE);
        try (DirectoryReader r1 = DirectoryReader.open(w)) {
          IndexSearcher s1 = new IndexSearcher(r1);
          s1.setQueryCache(cache);
          s1.setQueryCachingPolicy(ALWAYS_CACHE);
          assertEquals(5, s1.count(rangeQuery)); // fills the cache entry for the segment

          // doc 0 is no longer in stock: an in-place doc-values update, same segment core
          w.updateNumericDocValue(new Term("id", "0"), "inStock", 0);
          try (DirectoryReader r2 = DirectoryReader.openIfChanged(r1, w)) {
            assertNotNull(r2);
            assertSame(
                r1.leaves().get(0).reader().getCoreCacheHelper().getKey(),
                r2.leaves().get(0).reader().getCoreCacheHelper().getKey());

            IndexSearcher uncached = new IndexSearcher(r2);
            uncached.setQueryCache(null);
            assertEquals(4, uncached.count(rangeQuery));

            IndexSearcher s2 = new IndexSearcher(r2);
            s2.setQueryCache(cache);
            s2.setQueryCachingPolicy(ALWAYS_CACHE);
            // Same query, same reader: the cached search must agree with the uncached one.
            assertEquals("cached result differs from uncached result", 4, s2.count(rangeQuery));
          }
        }
      }
    }
  }

  private static Query fastMatch() {
    return NumericDocValuesField.newSlowExactQuery("inStock", 1);
  }

  public void testLongRangeLongValuesSource() throws Exception {
    doTest(
        new LongRange("r", 0, true, 100, true)
            .getQuery(fastMatch(), LongValuesSource.fromLongField("price")));
  }

  public void testDoubleRangeDoubleValuesSource() throws Exception {
    doTest(
        new DoubleRange("r", 0, true, 100, true)
            .getQuery(fastMatch(), DoubleValuesSource.fromLongField("price")));
  }

  public void testLongRangeMultiLongValuesSource() throws Exception {
    doTest(
        new LongRange("r", 0, true, 100, true)
            .getQuery(fastMatch(), MultiLongValuesSource.fromLongField("price")));
  }

  public void testDoubleRangeMultiDoubleValuesSource() throws Exception {
    doTest(
        new DoubleRange("r", 0, true, 100, true)
            .getQuery(fastMatch(), MultiDoubleValuesSource.fromLongField("price")));
  }

  /**
   * The Weight-level contract (SegmentCacheable): after an in-place update of a doc-values field
   * that the query reads, the query is not cacheable on that segment, as the fast-match query
   * itself reports.
   */
  public void testIsCacheableAfterUpdate() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < NUM_DOCS; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
          doc.add(new NumericDocValuesField("inStock", i < 5 ? 1 : 0));
          doc.add(new NumericDocValuesField("price", 10));
          w.addDocument(doc);
        }
        w.updateNumericDocValue(new Term("id", "0"), "inStock", 0);
        try (DirectoryReader r = DirectoryReader.open(w)) {
          IndexSearcher s = new IndexSearcher(r);
          s.setQueryCache(null);
          LeafReaderContext leaf = r.leaves().get(0);
          Weight fastMatchWeight =
              s.createWeight(s.rewrite(fastMatch()), ScoreMode.COMPLETE_NO_SCORES, 1f);
          assertFalse(fastMatchWeight.isCacheable(leaf));
          Query rangeQuery =
              new LongRange("r", 0, true, 100, true)
                  .getQuery(fastMatch(), LongValuesSource.fromLongField("price"));
          Weight rangeWeight =
              s.createWeight(s.rewrite(rangeQuery), ScoreMode.COMPLETE_NO_SCORES, 1f);
          assertFalse(
              "range query reads the updated fast-match field but reports cacheable",
              rangeWeight.isCacheable(leaf));
        }
      }
    }
  }
}
