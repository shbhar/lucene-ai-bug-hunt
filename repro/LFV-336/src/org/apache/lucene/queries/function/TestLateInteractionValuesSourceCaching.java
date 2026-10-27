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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LateInteractionField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
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
 * LateInteractionFloatValuesSource reads the multi-vectors from BinaryDocValues, but its
 * isCacheable returns true unconditionally. A FunctionMatchQuery over it is therefore cached by
 * segment core, and after an in-place update of the LateInteractionField the cache keeps serving
 * the result computed from the old multi-vector.
 */
public class TestLateInteractionValuesSourceCaching extends LuceneTestCase {

  private static final int NUM_DOCS = 20;
  private static final float[][] QUERY = {{1f, 0f}};

  private static final QueryCachingPolicy ALWAYS_CACHE =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  /** Docs 0..4 are similar to the query (score 1.0), the others are orthogonal (score 0.5). */
  private static Directory buildIndex(IndexWriter[] writerOut) throws Exception {
    Directory dir = newDirectory();
    IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE));
    for (int i = 0; i < NUM_DOCS; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
      doc.add(new LateInteractionField("mv", i < 5 ? new float[][] {{1f, 0f}} : new float[][] {{0f, 1f}}));
      w.addDocument(doc);
    }
    w.commit();
    writerOut[0] = w;
    return dir;
  }

  public void testCachedFunctionMatchQueryAfterUpdate() throws Exception {
    IndexWriter[] wh = new IndexWriter[1];
    try (Directory dir = buildIndex(wh);
        IndexWriter w = wh[0]) {
      // COSINE similarity, SUM_MAX_SIM: 1.0 for docs 0..4, 0.5 for the others
      DoubleValuesSource source = new LateInteractionFloatValuesSource("mv", QUERY);
      Query query = new FunctionMatchQuery(source, score -> score > 0.75);

      LRUQueryCache cache = new LRUQueryCache(1000, 1 << 20, ctx -> true, Float.MAX_VALUE);
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        IndexSearcher s1 = new IndexSearcher(r1);
        s1.setQueryCache(cache);
        s1.setQueryCachingPolicy(ALWAYS_CACHE);
        assertEquals(5, s1.count(query)); // fills the cache entry for the segment

        // in-place doc-values update: doc 0 becomes orthogonal to the query
        w.updateBinaryDocValue(
            new Term("id", "0"), "mv", LateInteractionField.encode(new float[][] {{0f, 1f}}));
        try (DirectoryReader r2 = DirectoryReader.openIfChanged(r1, w)) {
          assertNotNull(r2);
          assertSame(
              r1.leaves().get(0).reader().getCoreCacheHelper().getKey(),
              r2.leaves().get(0).reader().getCoreCacheHelper().getKey());

          IndexSearcher uncached = new IndexSearcher(r2);
          uncached.setQueryCache(null);
          assertEquals(4, uncached.count(query));

          IndexSearcher s2 = new IndexSearcher(r2);
          s2.setQueryCache(cache);
          s2.setQueryCachingPolicy(ALWAYS_CACHE);
          // Same query, same reader: the cached search must agree with the uncached one.
          assertEquals("cached result differs from uncached result", 4, s2.count(query));
        }
      }
    }
  }

  /**
   * The SegmentCacheable contract: objects that depend on doc values return
   * DocValues#isCacheable, which is false once the field has been updated in place (as the
   * doc-values-backed DoubleValuesSource.fromLongField reports).
   */
  public void testIsCacheableAfterUpdate() throws Exception {
    IndexWriter[] wh = new IndexWriter[1];
    try (Directory dir = buildIndex(wh);
        IndexWriter w = wh[0]) {
      w.updateBinaryDocValue(
          new Term("id", "0"), "mv", LateInteractionField.encode(new float[][] {{0f, 1f}}));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        DoubleValuesSource source = new LateInteractionFloatValuesSource("mv", QUERY);
        assertFalse(
            "source reads the updated binary doc values field but reports cacheable",
            source.isCacheable(r.leaves().get(0)));
      }
    }
  }
}
