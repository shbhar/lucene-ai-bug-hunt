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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * FloatVectorSimilarityQuery's Weight reports isCacheable(ctx) == true, but the per-segment doc set
 * it produces depends on the reader's live docs and on the searcher's QueryTimeout, neither of which
 * is part of the query cache key (query + segment core). SegmentCacheable: "Objects that are not
 * segment-immutable ... should return false". A cached entry is therefore served to readers and
 * searches it is wrong for.
 */
public class TestVectorSimilarityQueryCaching extends LuceneTestCase {

  private static final QueryCachingPolicy ALWAYS_CACHE =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  private static LRUQueryCache newCache() {
    return new LRUQueryCache(1000, 1 << 24, context -> true, Float.POSITIVE_INFINITY);
  }

  private static IndexSearcher searcher(DirectoryReader reader, QueryCache cache) {
    IndexSearcher s = new IndexSearcher(reader);
    s.setQueryCache(cache);
    s.setQueryCachingPolicy(ALWAYS_CACHE);
    return s;
  }

  private static void addDocs(IndexWriter w) throws Exception {
    for (int i = 0; i < 100; i++) {
      Document d = new Document();
      d.add(new StringField("id", Integer.toString(i), Field.Store.NO));
      d.add(new KnnFloatVectorField("v", new float[] {i, 1}, VectorSimilarityFunction.EUCLIDEAN));
      w.addDocument(d);
    }
    w.forceMerge(1);
    w.commit();
  }

  /**
   * An older point-in-time reader shares the segment core with a newer reader that has a deletion.
   * The newer reader's search populates the cache; the older reader then misses a document that is
   * live in it. (Release variant: pre-10.5 constructor, traversalSimilarity = -infinity.)
   */
  public void testCachedResultBakesInLiveDocsOfAnotherReader() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      addDocs(w);
      try (DirectoryReader oldReader = DirectoryReader.open(w)) {
        w.deleteDocuments(new Term("id", "0"));
        try (DirectoryReader newReader = DirectoryReader.openIfChanged(oldReader, w)) {
          assertNotNull(newReader);
          // same segment core, so the same query cache key
          assertSame(
              oldReader.leaves().get(0).reader().getCoreCacheHelper().getKey(),
              newReader.leaves().get(0).reader().getCoreCacheHelper().getKey());
          Query q =
              new ConstantScoreQuery(
                  new FloatVectorSimilarityQuery("v", new float[] {0, 1}, Float.NEGATIVE_INFINITY, 0f, null));
          assertEquals(100, searcher(oldReader, null).count(q)); // control, no cache
          LRUQueryCache cache = newCache();
          assertEquals(99, searcher(newReader, cache).search(q, 1000).scoreDocs.length);
          // doc 0 is live in oldReader: it must still match there
          assertEquals(100, searcher(oldReader, cache).search(q, 1000).scoreDocs.length);
        }
      }
    }
  }

  /**
   * Same as the first test with the query cache settings that IndexSearcher uses by default in the
   * 9.x and 10.x releases (LRUQueryCache(1000, 32 MB) and UsageTrackingQueryCachingPolicy): after a
   * few searches on the newer reader the entry is cached, and the older reader misses a live doc.
   */
  public void testCachedResultBakesInLiveDocsWithReleaseDefaultCacheSettings() throws Exception {
    final int numDocs = 12_000; // the default cache only caches segments of at least 10,000 docs
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      for (int i = 0; i < numDocs; i++) {
        Document d = new Document();
        d.add(new StringField("id", Integer.toString(i), Field.Store.NO));
        d.add(new KnnFloatVectorField("v", new float[] {i, 1}, VectorSimilarityFunction.EUCLIDEAN));
        w.addDocument(d);
      }
      w.forceMerge(1);
      w.commit();
      try (DirectoryReader oldReader = DirectoryReader.open(w)) {
        w.deleteDocuments(new Term("id", "0"));
        try (DirectoryReader newReader = DirectoryReader.openIfChanged(oldReader, w)) {
          QueryCache cache = new LRUQueryCache(1000, 32L << 20);
          QueryCachingPolicy policy = new UsageTrackingQueryCachingPolicy();
          Query q =
              new ConstantScoreQuery(
                  new FloatVectorSimilarityQuery("v", new float[] {0, 1}, Float.NEGATIVE_INFINITY, 0f, null));
          for (int i = 0; i < 6; i++) { // the default policy caches after 5 uses
            IndexSearcher s = new IndexSearcher(newReader);
            s.setQueryCache(cache);
            s.setQueryCachingPolicy(policy);
            assertEquals(numDocs - 1, s.count(q));
          }
          IndexSearcher old = new IndexSearcher(oldReader);
          old.setQueryCache(cache);
          old.setQueryCachingPolicy(policy);
          assertEquals(numDocs, old.search(q, numDocs + 1).scoreDocs.length);
        }
      }
    }
  }

  /**
   * A search that hits the timeout caches its partial (here: empty) per-segment result; a later
   * search without a timeout, on another searcher sharing the cache, gets the partial result.
   */
  public void testTimedOutPartialResultIsCached() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      addDocs(w);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        // graph search, which honours the searcher timeout (pre-10.5 constructor)
        Query q =
            new ConstantScoreQuery(
                new FloatVectorSimilarityQuery("v", new float[] {0, 1}, 0f));
        assertEquals(100, searcher(reader, null).count(q)); // control, no cache, no timeout
        LRUQueryCache cache = newCache();
        IndexSearcher timedOut = searcher(reader, cache);
        timedOut.setTimeout(() -> true);
        timedOut.search(q, 1000); // partial results are expected for this search only
        IndexSearcher later = searcher(reader, cache);
        assertEquals(100, later.search(q, 1000).scoreDocs.length);
      }
    }
  }
}
