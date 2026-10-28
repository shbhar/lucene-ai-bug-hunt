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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.MultiDoubleValuesSource;
import org.apache.lucene.facet.MultiLongValuesSource;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.NumericUtils;

/**
 * Independent verifier repro (public API only).
 *
 * <p>Claim under test: {@code LongRange/DoubleRange#getQuery(fastMatchQuery, valueSource)} reports
 * its Weight as cacheable from the value source alone. When the fast-match query reads a doc-values
 * field that is later updated in place, a query cache that was filled before the update keeps
 * serving the old matches after an NRT reopen (the segment core, and therefore the cache key, is
 * unchanged by a doc-values update).
 *
 * <p>Index: 6 docs; docs 0-4 have price 10..50 and doc 5 has price 500. All have inStock=1. The
 * query is "price in [0, 100] with fast match inStock == 1". Before the update it matches {0..4};
 * after setting doc 0's inStock to 0 it must match {1..4}.
 */
public class TestRangeGetQueryCacheV1 extends LuceneTestCase {

  /** Caches every query on its first use, so the test does not depend on usage-frequency rules. */
  private static final QueryCachingPolicy CACHE_EVERYTHING =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  private static final TreeSet<Integer> BEFORE = new TreeSet<>(List.of(0, 1, 2, 3, 4));
  private static final TreeSet<Integer> AFTER = new TreeSet<>(List.of(1, 2, 3, 4));

  private interface QueryFactory {
    Query make(Query fastMatch);
  }

  private static Query fastMatch() {
    return NumericDocValuesField.newSlowExactQuery("inStock", 1);
  }

  private Directory dir;
  private IndexWriter writer;

  private void buildIndex() throws IOException {
    dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    writer = new IndexWriter(dir, iwc);
    long[] prices = {10, 20, 30, 40, 50, 500};
    for (int i = 0; i < prices.length; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
      doc.add(new NumericDocValuesField("price", prices[i]));
      doc.add(new DoubleDocValuesField("priceD", prices[i]));
      doc.add(new SortedNumericDocValuesField("priceM", prices[i]));
      doc.add(
          new SortedNumericDocValuesField(
              "priceMD", NumericUtils.doubleToSortableLong((double) prices[i])));
      doc.add(new NumericDocValuesField("inStock", 1));
      writer.addDocument(doc);
    }
    writer.commit();
  }

  /** Collects global doc ids without scores, so the query cache is eligible. */
  private static final class DocSetCollector extends SimpleCollector {
    final TreeSet<Integer> docs = new TreeSet<>();
    private int docBase;

    @Override
    protected void doSetNextReader(LeafReaderContext context) {
      docBase = context.docBase;
    }

    @Override
    public void collect(int doc) {
      docs.add(docBase + doc);
    }

    @Override
    public ScoreMode scoreMode() {
      return ScoreMode.COMPLETE_NO_SCORES;
    }
  }

  private static TreeSet<Integer> matches(IndexSearcher searcher, Query query) throws IOException {
    return searcher.search(
        query,
        new CollectorManager<DocSetCollector, TreeSet<Integer>>() {
          @Override
          public DocSetCollector newCollector() {
            return new DocSetCollector();
          }

          @Override
          public TreeSet<Integer> reduce(Collection<DocSetCollector> collectors) {
            TreeSet<Integer> all = new TreeSet<>();
            for (DocSetCollector c : collectors) {
              all.addAll(c.docs);
            }
            return all;
          }
        });
  }

  private static IndexSearcher cachedSearcher(DirectoryReader reader, LRUQueryCache cache) {
    IndexSearcher s = new IndexSearcher(reader);
    s.setQueryCache(cache);
    s.setQueryCachingPolicy(CACHE_EVERYTHING);
    return s;
  }

  private static IndexSearcher uncachedSearcher(DirectoryReader reader) {
    IndexSearcher s = new IndexSearcher(reader);
    s.setQueryCache(null);
    return s;
  }

  private static List<Object> coreKeys(DirectoryReader reader) {
    List<Object> keys = new ArrayList<>();
    for (LeafReaderContext ctx : reader.leaves()) {
      keys.add(ctx.reader().getCoreCacheHelper().getKey());
    }
    return keys;
  }

  /**
   * Runs: fill cache on reader 1, update doc 0's fast-match field in place, reopen, compare cached
   * vs uncached on reader 2.
   */
  private void runScenario(QueryFactory factory, String updateField, long newValue)
      throws IOException {
    buildIndex();
    // Leaves cached regardless of size; skip factor large enough that the cache is never bypassed.
    LRUQueryCache cache = new LRUQueryCache(1000, 64L * 1024 * 1024, ctx -> true, 1000f);
    Query q = factory.make(fastMatch());

    DirectoryReader r1 = DirectoryReader.open(writer);
    try {
      assertEquals("before update, uncached", BEFORE, matches(uncachedSearcher(r1), q));
      assertEquals("before update, cached", BEFORE, matches(cachedSearcher(r1, cache), q));
      assertTrue("cache was filled", cache.getCacheCount() > 0);

      writer.updateNumericDocValue(new Term("id", "0"), updateField, newValue);
      DirectoryReader r2 = DirectoryReader.openIfChanged(r1, writer);
      assertNotNull("reopen must see the doc-values update", r2);
      try {
        // Precondition: the doc-values update does not change the segment core (the cache key).
        assertEquals("same segment cores", coreKeys(r1), coreKeys(r2));

        TreeSet<Integer> uncached = matches(uncachedSearcher(r2), q);
        TreeSet<Integer> cached = matches(cachedSearcher(r2, cache), q);
        // Both updates used here remove doc 0 from the match set.
        assertEquals("after update, uncached (oracle)", AFTER, uncached);
        assertEquals("after update, cached must equal uncached", uncached, cached);
      } finally {
        r2.close();
      }
    } finally {
      r1.close();
      writer.close();
      dir.close();
    }
  }

  // ---- the claim: four query variants, fast match on an updated doc-values field ----

  public void testLongRangeLongValuesSource() throws IOException {
    runScenario(
        fm ->
            new LongRange("r", 0, true, 100, true)
                .getQuery(fm, LongValuesSource.fromLongField("price")),
        "inStock",
        0L);
  }

  public void testLongRangeMultiLongValuesSource() throws IOException {
    runScenario(
        fm ->
            new LongRange("r", 0, true, 100, true)
                .getQuery(fm, MultiLongValuesSource.fromLongField("priceM")),
        "inStock",
        0L);
  }

  public void testDoubleRangeDoubleValuesSource() throws IOException {
    runScenario(
        fm ->
            new DoubleRange("r", 0d, true, 100d, true)
                .getQuery(fm, DoubleValuesSource.fromDoubleField("priceD")),
        "inStock",
        0L);
  }

  public void testDoubleRangeMultiDoubleValuesSource() throws IOException {
    runScenario(
        fm ->
            new DoubleRange("r", 0d, true, 100d, true)
                .getQuery(fm, MultiDoubleValuesSource.fromDoubleField("priceMD")),
        "inStock",
        0L);
  }

  // ---- direct check of the SegmentCacheable contract on the updated segment ----

  public void testWeightCacheabilityOnUpdatedSegment() throws IOException {
    buildIndex();
    writer.updateNumericDocValue(new Term("id", "0"), "inStock", 0L);
    DirectoryReader r = DirectoryReader.open(writer);
    try {
      IndexSearcher s = uncachedSearcher(r);
      Query range =
          s.rewrite(
              new LongRange("r", 0, true, 100, true)
                  .getQuery(fastMatch(), LongValuesSource.fromLongField("price")));
      Weight rangeW = s.createWeight(range, ScoreMode.COMPLETE_NO_SCORES, 1f);
      Weight fmW = s.createWeight(s.rewrite(fastMatch()), ScoreMode.COMPLETE_NO_SCORES, 1f);
      for (LeafReaderContext ctx : r.leaves()) {
        if (fmW.isCacheable(ctx) == false) {
          assertFalse(
              "range Weight reads the fast-match Weight, which is not cacheable on this leaf",
              rangeW.isCacheable(ctx));
        }
      }
    } finally {
      r.close();
      writer.close();
      dir.close();
    }
  }

  // ---- controls (expected to pass on every version) ----

  /** The fast-match query alone, through the same cache and the same update, is correct. */
  public void testControlFastMatchAlone() throws IOException {
    buildIndex();
    LRUQueryCache cache = new LRUQueryCache(1000, 64L * 1024 * 1024, ctx -> true, 1000f);
    DirectoryReader r1 = DirectoryReader.open(writer);
    try {
      TreeSet<Integer> all = new TreeSet<>(List.of(0, 1, 2, 3, 4, 5));
      assertEquals(all, matches(cachedSearcher(r1, cache), fastMatch()));
      writer.updateNumericDocValue(new Term("id", "0"), "inStock", 0L);
      DirectoryReader r2 = DirectoryReader.openIfChanged(r1, writer);
      assertNotNull(r2);
      try {
        assertEquals(
            new TreeSet<>(List.of(1, 2, 3, 4, 5)), matches(cachedSearcher(r2, cache), fastMatch()));
      } finally {
        r2.close();
      }
    } finally {
      r1.close();
      writer.close();
      dir.close();
    }
  }

  /**
   * Updating the value-source field (not the fast-match field) makes the range query uncacheable,
   * so cached == uncached: shows the gap is specific to the fast-match side.
   */
  public void testControlValueSourceFieldUpdated() throws IOException {
    runScenario(
        fm ->
            new LongRange("r", 0, true, 100, true)
                .getQuery(fm, LongValuesSource.fromLongField("price")),
        "price",
        1000L); // doc 0 moves out of [0, 100]
  }
}
