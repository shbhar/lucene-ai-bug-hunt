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

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.MultiDoubleValuesSource;
import org.apache.lucene.facet.MultiLongValuesSource;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The query returned by LongRange/DoubleRange#getQuery(fastMatchQuery, valueSource) iterates the
 * fast-match query, so it must not be cached on a segment where a doc-values field read by the
 * fast-match query was updated in place.
 */
public class TestRangeQueryFastMatchCacheability extends LuceneTestCase {

  private static final QueryCachingPolicy ALWAYS_CACHE =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  /** Records the queries served from the cache. Caches every segment, so 20 docs are enough. */
  private static class RecordingCache extends LRUQueryCache {
    final Set<Query> hits = new HashSet<>();

    RecordingCache() {
      super(1000, 1 << 20, _ -> true, Float.MAX_VALUE);
    }

    @Override
    protected void onHit(Object readerCoreKey, Query query) {
      super.onHit(readerCoreKey, query);
      hits.add(query);
    }
  }

  /** The fast match: shoes (postings) that are in stock (a doc-values flag). */
  private static Query inStockShoes() {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("category", "shoes")), Occur.MUST)
        .add(NumericDocValuesField.newSlowExactQuery("inStock", 1), Occur.FILTER)
        .build();
  }

  private static IndexSearcher searcher(DirectoryReader reader, LRUQueryCache cache) {
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setQueryCache(cache);
    searcher.setQueryCachingPolicy(ALWAYS_CACHE);
    return searcher;
  }

  private static void indexDocs(IndexWriter w) throws IOException {
    // 20 docs, price 10 each; docs 0-9 are shoes, docs 0-4 are in stock.
    for (int i = 0; i < 20; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
      doc.add(new StringField("category", i < 10 ? "shoes" : "hats", Field.Store.NO));
      doc.add(new NumericDocValuesField("inStock", i < 5 ? 1 : 0));
      doc.add(new NumericDocValuesField("price", 10));
      w.addDocument(doc);
    }
  }

  /**
   * Counts the query through a cache (5 hits), marks doc 0 out of stock in place, reopens, and
   * checks that the cached count equals the uncached count (4) on the reopened reader.
   */
  private void assertNotStaleAfterUpdate(Supplier<Query> query) throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      indexDocs(w);
      LRUQueryCache cache = new RecordingCache();
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        assertEquals(5, searcher(r1, cache).count(query.get()));
        w.updateNumericDocValue(new Term("id", "0"), "inStock", 0);
        try (DirectoryReader r2 = DirectoryReader.openIfChanged(r1, w)) {
          assertNotNull(r2);
          IndexSearcher uncached = new IndexSearcher(r2);
          uncached.setQueryCache(null);
          assertEquals(4, uncached.count(query.get()));
          assertEquals("cached count after the update", 4, searcher(r2, cache).count(query.get()));
        }
      }
    }
  }

  private static LongRange longRange() {
    return new LongRange("cheap", 0, true, 100, true);
  }

  private static DoubleRange doubleRange() {
    return new DoubleRange("cheap", 0, true, 100, true);
  }

  public void testDrillDownWithDocValuesFastMatch() throws IOException {
    assertNotStaleAfterUpdate(
        () -> {
          DrillDownQuery ddq = new DrillDownQuery(new FacetsConfig(), new MatchAllDocsQuery());
          ddq.add(
              "price",
              longRange().getQuery(inStockShoes(), LongValuesSource.fromLongField("price")));
          return ddq;
        });
  }

  public void testLongRangeLongValuesSource() throws IOException {
    assertNotStaleAfterUpdate(
        () -> longRange().getQuery(inStockShoes(), LongValuesSource.fromLongField("price")));
  }

  public void testLongRangeMultiLongValuesSource() throws IOException {
    assertNotStaleAfterUpdate(
        () -> longRange().getQuery(inStockShoes(), MultiLongValuesSource.fromLongField("price")));
  }

  public void testDoubleRangeDoubleValuesSource() throws IOException {
    assertNotStaleAfterUpdate(
        () -> doubleRange().getQuery(inStockShoes(), DoubleValuesSource.fromLongField("price")));
  }

  public void testDoubleRangeMultiDoubleValuesSource() throws IOException {
    assertNotStaleAfterUpdate(
        () ->
            doubleRange().getQuery(inStockShoes(), MultiDoubleValuesSource.fromLongField("price")));
  }

  /**
   * The Weight-level contract: the fast-match Weight is not cacheable, so the range Weight is not.
   */
  public void testRangeWeightIsNotCacheableWhenFastMatchIsNot() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      indexDocs(w);
      w.updateNumericDocValue(new Term("id", "0"), "inStock", 0);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        s.setQueryCache(null);
        LeafReaderContext leaf = r.leaves().get(0);
        Query range = longRange().getQuery(inStockShoes(), LongValuesSource.fromLongField("price"));
        Weight fastMatch =
            s.createWeight(s.rewrite(inStockShoes()), ScoreMode.COMPLETE_NO_SCORES, 1);
        Weight rangeWeight = s.createWeight(s.rewrite(range), ScoreMode.COMPLETE_NO_SCORES, 1);
        assertFalse(fastMatch.isCacheable(leaf));
        assertFalse("range Weight reports cacheable", rangeWeight.isCacheable(leaf));
      }
    }
  }

  /** Control: the same restriction in the base query instead of the fast match. */
  public void testControlRestrictionInBaseQuery() throws IOException {
    assertNotStaleAfterUpdate(
        () -> {
          DrillDownQuery ddq = new DrillDownQuery(new FacetsConfig(), inStockShoes());
          ddq.add("price", longRange().getQuery(null, LongValuesSource.fromLongField("price")));
          return ddq;
        });
  }

  /** Control: without doc-values updates, the range query is still served from the cache. */
  public void testControlStillCachedWithoutUpdates() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      indexDocs(w);
      RecordingCache cache = new RecordingCache();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = searcher(r, cache);
        Query range = longRange().getQuery(inStockShoes(), LongValuesSource.fromLongField("price"));
        assertEquals(5, s.count(range));
        assertEquals(5, s.count(range));
        assertTrue("range query served from the cache", cache.hits.contains(s.rewrite(range)));
      }
    }
  }
}
