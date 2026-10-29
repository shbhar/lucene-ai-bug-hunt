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
package verifyv1;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Weight;
import org.apache.lucene.spatial.composite.CompositeVerifyQuery;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.spatial.util.ShapeValuesPredicate;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;

/**
 * Independent check (public API only, package outside org.apache.lucene): a CompositeVerifyQuery
 * whose index query reads numeric doc values must not be served from a query cache after an
 * in-place doc-values update of that field.
 */
public class TestCompositeVerifyQueryCache extends LuceneTestCase {

  private static final int NUM_DOCS = 20;

  /** Caches every query on every use, so the test does not depend on usage-tracking heuristics. */
  private static final QueryCachingPolicy CACHE_EVERYTHING =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  private final SpatialContext ctx = SpatialContext.GEO;
  private final SerializedDVStrategy strategy = new SerializedDVStrategy(ctx, "shape");

  /** docs 0..2 have v=1, all others v=3; every doc has a point shape inside the world bounds. */
  private IndexWriter index(Directory dir) throws IOException {
    IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE));
    for (int i = 0; i < NUM_DOCS; i++) {
      Document d = new Document();
      d.add(new StringField("id", Integer.toString(i), Field.Store.YES));
      d.add(new NumericDocValuesField("v", i < 3 ? 1 : 3));
      for (Field f : strategy.createIndexableFields(ctx.makePoint(i, i))) {
        d.add(f);
      }
      w.addDocument(d);
    }
    w.commit();
    return w;
  }

  /** Composite query: the doc-values index query, verified by an always-true shape predicate. */
  private Query composite(Query indexQuery) {
    ShapeValuesPredicate everyPoint =
        new ShapeValuesPredicate(
            strategy.makeShapeValueSource(), SpatialOperation.Intersects, ctx.getWorldBounds());
    return new CompositeVerifyQuery(indexQuery, everyPoint);
  }

  private static IndexSearcher cachedSearcher(DirectoryReader r, LRUQueryCache cache) {
    IndexSearcher s = new IndexSearcher(r);
    s.setQueryCache(cache);
    s.setQueryCachingPolicy(CACHE_EVERYTHING);
    return s;
  }

  private static IndexSearcher uncachedSearcher(DirectoryReader r) {
    IndexSearcher s = new IndexSearcher(r);
    s.setQueryCache(null);
    return s;
  }

  /** Stored ids of the matches; the ConstantScoreQuery makes the inner query a non-scoring one. */
  private static Set<Integer> ids(IndexSearcher s, Query q) throws IOException {
    TopDocs td = s.search(new ConstantScoreQuery(q), 10 * NUM_DOCS);
    StoredFields sf = s.storedFields();
    Set<Integer> out = new TreeSet<>();
    for (ScoreDoc sd : td.scoreDocs) {
      out.add(Integer.parseInt(sf.document(sd.doc).get("id")));
    }
    return out;
  }

  private static LRUQueryCache newCache() {
    return new LRUQueryCache(1000, 1L << 24, leaf -> true, Float.POSITIVE_INFINITY);
  }

  public void testCachedHitsAfterInPlaceNumericDocValuesUpdate() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = index(dir)) {
      DirectoryReader r1 = DirectoryReader.open(dir);
      LRUQueryCache cache = newCache();
      Query indexQuery = NumericDocValuesField.newSlowExactQuery("v", 1);
      Query cvq = composite(indexQuery);

      assertEquals(Set.of(0, 1, 2), ids(cachedSearcher(r1, cache), cvq));
      assertTrue("the first search must have filled the cache", cache.getCacheSize() > 0);
      Object coreKey1 = r1.leaves().get(0).reader().getCoreCacheHelper().getKey();

      // in-place updates: doc 0 leaves the index query, doc 5 enters it
      w.updateNumericDocValue(new Term("id", "0"), "v", 2);
      w.updateNumericDocValue(new Term("id", "5"), "v", 1);
      w.commit();
      DirectoryReader r2 = DirectoryReader.openIfChanged(r1);
      assertNotNull(r2);
      r1.close(); // the cache entry keyed on the shared segment core survives
      assertEquals(1, r2.leaves().size());
      assertSame(
          "a doc-values update keeps the segment core cache key",
          coreKey1,
          r2.leaves().get(0).reader().getCoreCacheHelper().getKey());

      try (r2) {
        Set<Integer> expected = new TreeSet<>(Set.of(1, 2, 5));
        // oracle: no cache
        assertEquals(expected, ids(uncachedSearcher(r2), cvq));
        // the bare index query is correctly not served from the cache
        assertEquals(expected, ids(cachedSearcher(r2, cache), indexQuery));
        // the composite must agree with the uncached search
        assertEquals(
            "cached CompositeVerifyQuery after an in-place doc-values update",
            expected,
            ids(cachedSearcher(r2, cache), cvq));
      }
    }
  }

  public void testCachedCountAfterInPlaceNumericDocValuesUpdate() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = index(dir)) {
      DirectoryReader r1 = DirectoryReader.open(dir);
      LRUQueryCache cache = newCache();
      Query cvq = composite(NumericDocValuesField.newSlowRangeQuery("v", 0, 1));

      assertEquals(3, cachedSearcher(r1, cache).count(cvq));

      w.updateNumericDocValue(new Term("id", "0"), "v", 2);
      w.commit();
      DirectoryReader r2 = DirectoryReader.openIfChanged(r1);
      assertNotNull(r2);
      r1.close();
      try (r2) {
        assertEquals(2, uncachedSearcher(r2).count(cvq));
        assertEquals(
            "cached CompositeVerifyQuery count after an in-place doc-values update",
            2,
            cachedSearcher(r2, cache).count(cvq));
      }
    }
  }

  /** The structural contract alone: the wrapper must not be cacheable where its index query is not. */
  public void testWeightNotCacheableWhenIndexQueryWeightIsNot() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = index(dir)) {
      w.updateNumericDocValue(new Term("id", "0"), "v", 2);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = uncachedSearcher(r);
        LeafReaderContext leaf = r.leaves().get(0);
        Query indexQuery = NumericDocValuesField.newSlowExactQuery("v", 1);
        Weight indexWeight =
            s.createWeight(s.rewrite(indexQuery), ScoreMode.COMPLETE_NO_SCORES, 1f);
        Weight cvqWeight =
            s.createWeight(s.rewrite(composite(indexQuery)), ScoreMode.COMPLETE_NO_SCORES, 1f);
        assertFalse("sanity: the updated doc-values field", indexWeight.isCacheable(leaf));
        assertFalse(
            "CompositeVerifyQuery runs the index query, so it cannot be cacheable here",
            cvqWeight.isCacheable(leaf));
      }
    }
  }
}
