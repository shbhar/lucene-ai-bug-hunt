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
import java.util.HashSet;
import java.util.Set;
import java.util.function.DoublePredicate;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LateInteractionField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.FunctionMatchQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SegmentCacheable says that objects depending on doc values should return
 * DocValues#isCacheable(ctx, fields), because updated doc-values fields are not suitable for
 * caching. LateInteractionFloatValuesSource reads a BinaryDocValues field (LateInteractionField)
 * but always reports cacheable. After an in-place doc-values update of that field, a query cache
 * entry filled before the update (same segment core key) is served for the new reader.
 *
 * <p>Only public API is used. Every test uses one segment with 10 documents: ids 0..4 hold a
 * multi-vector whose score against the query is 1.0, ids 5..9 one whose score is 0.5; the filter
 * keeps scores above 0.75. Then document 0 is updated in place to the 0.5 vector, so the correct
 * match count afterwards is 4.
 */
public class TestLateInteractionSourceCacheability extends LuceneTestCase {

  private static final String ID = "id";
  private static final String MV = "mv";
  private static final String NUM = "num";
  private static final float[][] QUERY = {{1f, 0f}};
  private static final float[][] NEAR = {{1f, 0f}}; // COSINE score (1 + 1) / 2 = 1.0
  private static final float[][] FAR = {{0f, 1f}}; // COSINE score (1 + 0) / 2 = 0.5
  private static final DoublePredicate ABOVE = v -> v > 0.75;

  /** Caches every query it is asked about. */
  private static final QueryCachingPolicy ALWAYS =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  private static IndexWriterConfig config() {
    // NoMergePolicy keeps the single flushed segment, so the reopened reader shares its core.
    return new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
  }

  private static void index(IndexWriter w) throws IOException {
    for (int i = 0; i < 10; i++) {
      Document doc = new Document();
      doc.add(new StringField(ID, Integer.toString(i), Field.Store.YES));
      doc.add(new LateInteractionField(MV, i < 5 ? NEAR : FAR));
      doc.add(new DoubleDocValuesField(NUM, i < 5 ? 1.0 : 0.5));
      w.addDocument(doc);
    }
  }

  private static LRUQueryCache newCache() {
    return new LRUQueryCache(1000, 1L << 26, ctx -> true, Float.POSITIVE_INFINITY);
  }

  private static IndexSearcher cachedSearcher(DirectoryReader r, QueryCache cache) {
    IndexSearcher s = new IndexSearcher(r);
    s.setQueryCache(cache);
    s.setQueryCachingPolicy(ALWAYS);
    return s;
  }

  private static IndexSearcher uncachedSearcher(DirectoryReader r) {
    IndexSearcher s = new IndexSearcher(r);
    s.setQueryCache(null);
    return s;
  }

  private static Object coreKey(DirectoryReader r) {
    return r.leaves().get(0).reader().getCoreCacheHelper().getKey();
  }

  /** The SegmentCacheable contract itself, no query cache involved. */
  public void testIsCacheableAfterInPlaceUpdate() throws IOException {
    LateInteractionFloatValuesSource source = new LateInteractionFloatValuesSource(MV, QUERY);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      index(w);
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        assertEquals(1, r1.leaves().size());
        assertTrue(source.isCacheable(r1.leaves().get(0)));
        w.updateDocValues(new Term(ID, "0"), new LateInteractionField(MV, FAR));
        try (DirectoryReader r2 = DirectoryReader.openIfChanged(r1, w)) {
          assertNotNull(r2);
          LeafReaderContext ctx = r2.leaves().get(0);
          assertFalse(
              "precondition: the field was updated in place", DocValues.isCacheable(ctx, MV));
          assertFalse(
              "LateInteractionFloatValuesSource reads the updated BinaryDocValues field '"
                  + MV
                  + "' but isCacheable returns true",
              source.isCacheable(ctx));
        }
      }
    }
  }

  /** IndexSearcher#count through a query cache shared across an NRT reopen. */
  public void testCachedCountAfterInPlaceUpdate() throws IOException {
    Query q = new FunctionMatchQuery(new LateInteractionFloatValuesSource(MV, QUERY), ABOVE);
    LRUQueryCache cache = newCache();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      index(w);
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        assertEquals(1, r1.leaves().size());
        assertEquals(5, cachedSearcher(r1, cache).count(q));
        assertEquals(5, uncachedSearcher(r1).count(q));
        assertEquals("precondition: the filter was cached", 1, cache.getCacheSize());

        w.updateDocValues(new Term(ID, "0"), new LateInteractionField(MV, FAR));
        try (DirectoryReader r2 = DirectoryReader.openIfChanged(r1, w)) {
          assertNotNull(r2);
          assertSame("precondition: same segment core", coreKey(r1), coreKey(r2));
          assertEquals("precondition: update visible uncached", 4, uncachedSearcher(r2).count(q));
          long hits = cache.getHitCount();
          int cached = cachedSearcher(r2, cache).count(q);
          System.out.println(
              "count: cached="
                  + cached
                  + " uncached=4 cacheHitsDuringCachedCount="
                  + (cache.getHitCount() - hits));
          assertEquals("cached count differs from uncached count after the update", 4, cached);
        }
      }
    }
  }

  /** A FILTER clause in a scoring BooleanQuery returns a document that no longer matches. */
  public void testCachedFilterReturnsNonMatchingDocAfterInPlaceUpdate() throws IOException {
    LateInteractionFloatValuesSource source = new LateInteractionFloatValuesSource(MV, QUERY);
    Query filter = new FunctionMatchQuery(source, ABOVE);
    Query q =
        new BooleanQuery.Builder()
            .add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST)
            .add(filter, BooleanClause.Occur.FILTER)
            .build();
    LRUQueryCache cache = newCache();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      index(w);
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        assertEquals(Set.of("0", "1", "2", "3", "4"), ids(cachedSearcher(r1, cache), q));
        assertEquals("precondition: the filter was cached", 1, cache.getCacheSize());

        w.updateDocValues(new Term(ID, "0"), new LateInteractionField(MV, FAR));
        try (DirectoryReader r2 = DirectoryReader.openIfChanged(r1, w)) {
          assertNotNull(r2);
          assertSame("precondition: same segment core", coreKey(r1), coreKey(r2));
          // The new reader sees doc 0's new value: 0.5, which the filter rejects.
          int doc0 = docOf(r2, "0");
          LeafReaderContext ctx = r2.leaves().get(0);
          DoubleValues values = source.getValues(ctx, null);
          assertTrue(values.advanceExact(doc0));
          assertEquals(0.5, values.doubleValue(), 1e-6);
          assertEquals(Set.of("1", "2", "3", "4"), ids(uncachedSearcher(r2), q));

          Set<String> cachedIds = ids(cachedSearcher(r2, cache), q);
          System.out.println("filter hits: cached=" + cachedIds);
          assertEquals(
              "cached filter returned a document whose current value fails the predicate",
              Set.of("1", "2", "3", "4"),
              cachedIds);
        }
      }
    }
  }

  /**
   * Control: the same sequence with a doc-values source that follows the contract
   * (DoubleValuesSource.fromDoubleField returns DocValues#isCacheable). Expected to pass.
   */
  public void testControlNumericDocValuesSource() throws IOException {
    Query q = new FunctionMatchQuery(DoubleValuesSource.fromDoubleField(NUM), ABOVE);
    LRUQueryCache cache = newCache();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      index(w);
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        assertEquals(5, cachedSearcher(r1, cache).count(q));
        assertEquals("precondition: the filter was cached", 1, cache.getCacheSize());
        w.updateDocValues(new Term(ID, "0"), new DoubleDocValuesField(NUM, 0.5));
        try (DirectoryReader r2 = DirectoryReader.openIfChanged(r1, w)) {
          assertNotNull(r2);
          assertSame("precondition: same segment core", coreKey(r1), coreKey(r2));
          assertEquals(4, uncachedSearcher(r2).count(q));
          assertEquals(4, cachedSearcher(r2, cache).count(q));
        }
      }
    }
  }

  private static Set<String> ids(IndexSearcher s, Query q) throws IOException {
    TopDocs td = s.search(q, 100);
    StoredFields sf = s.storedFields();
    Set<String> out = new HashSet<>();
    for (ScoreDoc sd : td.scoreDocs) {
      out.add(sf.document(sd.doc).get(ID));
    }
    return out;
  }

  private static int docOf(DirectoryReader r, String id) throws IOException {
    TopDocs td = new IndexSearcher(r).search(new TermQuery(new Term(ID, id)), 1);
    assertEquals(1, td.totalHits.value());
    return td.scoreDocs[0].doc;
  }
}
