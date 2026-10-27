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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.NumericDocValuesField;
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
 * A vector similarity query computes its per-segment result with the reader's live docs, the
 * searcher's timeout and its filter, yet its Weight reports isCacheable == true. The query cache
 * keys entries on the query and the segment core, which readers that differ only in deletions or
 * doc-values updates share (LeafReader#getCoreCacheHelper). So whichever reader or search fills the
 * cache entry decides what the others get.
 */
public class TestVectorSimilarityQueryCaching extends LuceneTestCase {

  private static final int NUM_DOCS = 100;

  /** Caches every query on every segment, so a single search fills the cache. */
  private static final QueryCachingPolicy ALWAYS_CACHE =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  private static QueryCache newCache() {
    return new LRUQueryCache(1000, 1 << 24, _ -> true, Float.POSITIVE_INFINITY);
  }

  private static IndexSearcher searcher(
      DirectoryReader reader, QueryCache cache, QueryCachingPolicy policy) {
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setQueryCache(cache);
    searcher.setQueryCachingPolicy(policy);
    return searcher;
  }

  private static IndexSearcher searcher(DirectoryReader reader, QueryCache cache) {
    return searcher(reader, cache, ALWAYS_CACHE);
  }

  private static int hits(IndexSearcher searcher, Query query) throws IOException {
    return searcher.search(query, 100_000).scoreDocs.length;
  }

  private static Object coreKey(DirectoryReader reader) {
    return reader.leaves().get(0).reader().getCoreCacheHelper().getKey();
  }

  /** One segment; doc i has id i, vector (i, 1) and doc-values field n = 0. */
  private static IndexWriter newWriter(Directory dir, int numDocs) throws IOException {
    IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()));
    for (int i = 0; i < numDocs; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
      doc.add(new KnnFloatVectorField("v", new float[] {i, 1}, VectorSimilarityFunction.EUCLIDEAN));
      doc.add(new NumericDocValuesField("n", 0));
      w.addDocument(doc);
    }
    w.forceMerge(1);
    w.commit();
    return w;
  }

  /**
   * Every vector has a similarity above 0 to the target, so this matches every doc that passes the
   * filter. decay = 1 is the exact path ("to guarantee completeness"); decay = 0.5 is the default,
   * an approximate graph search.
   */
  private static Query allVectors(float decay, Query filter) {
    return new ConstantScoreQuery(
        new FloatVectorSimilarityQuery.Adaptive("v", new float[] {0, 1}, 0f, decay, filter));
  }

  /**
   * An older point-in-time reader and a newer reader in which doc 0 is deleted share the segment
   * core. The newer reader searches first and fills the cache; returns the older reader's hits.
   */
  private int olderReaderHitsAfterNewerReader(Query query) throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = newWriter(dir, NUM_DOCS);
        DirectoryReader older = DirectoryReader.open(w)) {
      w.deleteDocuments(new Term("id", "0"));
      try (DirectoryReader newer = DirectoryReader.openIfChanged(older, w)) {
        assertSame(coreKey(older), coreKey(newer));
        assertEquals(NUM_DOCS, hits(searcher(older, null), query)); // no cache: 100
        QueryCache cache = newCache();
        assertEquals(NUM_DOCS - 1, hits(searcher(newer, cache), query));
        return hits(searcher(older, cache), query);
      }
    }
  }

  /** Doc 0 is live in the older reader, so it must still match there. */
  public void testOlderReaderAfterNewerReader() throws IOException {
    assertEquals(NUM_DOCS, olderReaderHitsAfterNewerReader(allVectors(1f, null)));
  }

  /** Control: the same sequence with an ordinary cacheable query. */
  public void testOlderReaderAfterNewerReaderControl() throws IOException {
    Query query = new ConstantScoreQuery(new FieldExistsQuery("v"));
    assertEquals(NUM_DOCS, olderReaderHitsAfterNewerReader(query));
  }

  /**
   * The same with the cache settings of the default IndexSearcher cache of the 9.x and 10.x
   * releases: UsageTrackingQueryCachingPolicy caches after a few uses, on segments of 10,000+ docs.
   */
  public void testOlderReaderAfterNewerReaderReleaseDefaultCacheSettings() throws IOException {
    int numDocs = 12_000;
    Query query = allVectors(1f, null);
    try (Directory dir = newDirectory();
        IndexWriter w = newWriter(dir, numDocs);
        DirectoryReader older = DirectoryReader.open(w)) {
      w.deleteDocuments(new Term("id", "0"));
      try (DirectoryReader newer = DirectoryReader.openIfChanged(older, w)) {
        QueryCache cache = new LRUQueryCache(1000, 32L << 20);
        QueryCachingPolicy policy = new UsageTrackingQueryCachingPolicy();
        for (int i = 0; i < 6; i++) {
          assertEquals(numDocs - 1, hits(searcher(newer, cache, policy), query));
        }
        assertEquals(numDocs, hits(searcher(older, cache, policy), query));
      }
    }
  }

  /** A search whose timeout fires fills the cache with its partial (here: empty) result. */
  public void testTimedOutSearchFillsCache() throws IOException {
    Query query = allVectors(0.5f, null); // the graph search honours the searcher's timeout
    try (Directory dir = newDirectory();
        IndexWriter w = newWriter(dir, NUM_DOCS);
        DirectoryReader reader = DirectoryReader.open(w)) {
      assertEquals(NUM_DOCS, hits(searcher(reader, null), query)); // no cache, no timeout: 100
      QueryCache cache = newCache();
      IndexSearcher timedOut = searcher(reader, cache);
      timedOut.setTimeout(() -> true);
      timedOut.search(query, NUM_DOCS); // a partial result is expected for this search only
      assertEquals(NUM_DOCS, hits(searcher(reader, cache), query)); // no timeout here
    }
  }

  /**
   * A filter on an updated doc-values field reports isCacheable == false, but it is cached inside
   * the similarity query anyway. Doc-values updates keep the segment core, so after doc 0 stops
   * matching the filter the newer reader still gets it.
   */
  public void testFilterOnUpdatedDocValues() throws IOException {
    Query query = allVectors(1f, NumericDocValuesField.newSlowExactQuery("n", 0));
    try (Directory dir = newDirectory();
        IndexWriter w = newWriter(dir, NUM_DOCS)) {
      w.updateNumericDocValue(new Term("id", "1"), "n", 0); // "n" is now an updated field
      try (DirectoryReader older = DirectoryReader.open(w)) {
        QueryCache cache = newCache();
        assertEquals(NUM_DOCS, hits(searcher(older, cache), query));
        w.updateNumericDocValue(new Term("id", "0"), "n", 1); // doc 0 no longer has n == 0
        try (DirectoryReader newer = DirectoryReader.openIfChanged(older, w)) {
          assertSame(coreKey(older), coreKey(newer));
          assertEquals(NUM_DOCS - 1, hits(searcher(newer, null), query)); // no cache: 99
          assertEquals(NUM_DOCS - 1, hits(searcher(newer, cache), query));
        }
      }
    }
  }
}
