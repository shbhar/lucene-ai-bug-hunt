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
package org.apache.lucene.queries.function;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LateInteractionField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.LateInteractionFloatValuesSource;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * LateInteractionFloatValuesSource reads the document multi-vectors from binary doc values, so
 * after an in-place update of the LateInteractionField it must not be cached against the segment
 * core (SegmentCacheable). The control does the same with a numeric doc-values source.
 */
public class TestLateInteractionValuesSourceQueryCache extends LuceneTestCase {

  private static final float[][] QUERY = {{1f, 0f}};
  private static final float[][] SAME = {{1f, 0f}}; // COSINE score 1.0
  private static final float[][] ORTHOGONAL = {{0f, 1f}}; // COSINE score 0.5

  private static final QueryCachingPolicy ALWAYS_CACHE =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  /** One segment of 20 docs: docs 0-4 point the same way as the query, docs 5-19 do not. */
  private static IndexWriter buildIndex(Directory dir) throws IOException {
    IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE));
    for (int i = 0; i < 20; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
      doc.add(new LateInteractionField("mv", i < 5 ? SAME : ORTHOGONAL));
      doc.add(new NumericDocValuesField("n", i < 5 ? 1 : 0));
      w.addDocument(doc);
    }
    w.commit();
    return w;
  }

  private static Query highScoreFilter(boolean lateInteraction) {
    DoubleValuesSource source =
        lateInteraction
            ? new LateInteractionFloatValuesSource("mv", QUERY)
            : DoubleValuesSource.fromLongField("n");
    return new FunctionMatchQuery(source, score -> score > 0.75);
  }

  /** Fills the cache, moves doc 0 out of the filter in place, reopens, counts again. */
  private void checkCachedCountAfterInPlaceUpdate(boolean lateInteraction) throws IOException {
    Query query = highScoreFilter(lateInteraction);
    LRUQueryCache cache = new LRUQueryCache(1000, 1 << 20, _ -> true, Float.MAX_VALUE);
    try (Directory dir = newDirectory();
        IndexWriter w = buildIndex(dir);
        DirectoryReader before = DirectoryReader.open(w)) {
      IndexSearcher s1 = new IndexSearcher(before);
      s1.setQueryCache(cache);
      s1.setQueryCachingPolicy(ALWAYS_CACHE);
      assertEquals(5, s1.count(query));
      assertEquals(1, cache.getCacheSize());

      if (lateInteraction) {
        w.updateBinaryDocValue(new Term("id", "0"), "mv", LateInteractionField.encode(ORTHOGONAL));
      } else {
        w.updateNumericDocValue(new Term("id", "0"), "n", 0L);
      }
      try (DirectoryReader after = DirectoryReader.openIfChanged(before, w)) {
        assertNotNull(after);
        // a doc-values update keeps the segment core, so the cache entry is still reachable
        assertSame(
            before.leaves().get(0).reader().getCoreCacheHelper().getKey(),
            after.leaves().get(0).reader().getCoreCacheHelper().getKey());

        IndexSearcher uncached = new IndexSearcher(after);
        uncached.setQueryCache(null);
        assertEquals(4, uncached.count(query));

        IndexSearcher s2 = new IndexSearcher(after);
        s2.setQueryCache(cache);
        s2.setQueryCachingPolicy(ALWAYS_CACHE);
        assertEquals("cached count after the in-place update", 4, s2.count(query));
      }
    }
  }

  public void testCachedFunctionMatchQueryAfterInPlaceUpdate() throws IOException {
    checkCachedCountAfterInPlaceUpdate(true);
  }

  public void testControlNumericDocValuesSourceAfterInPlaceUpdate() throws IOException {
    checkCachedCountAfterInPlaceUpdate(false);
  }

  public void testIsCacheableAfterInPlaceUpdate() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = buildIndex(dir)) {
      w.updateBinaryDocValue(new Term("id", "0"), "mv", LateInteractionField.encode(ORTHOGONAL));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        LeafReaderContext leaf = r.leaves().get(0);
        assertFalse("the field was updated in place", DocValues.isCacheable(leaf, "mv"));
        assertFalse(
            "isCacheable after an in-place update of the field it reads",
            new LateInteractionFloatValuesSource("mv", QUERY).isCacheable(leaf));
      }
    }
  }
}
