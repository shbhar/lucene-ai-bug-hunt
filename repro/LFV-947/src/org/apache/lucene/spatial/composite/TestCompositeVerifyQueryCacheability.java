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
package org.apache.lucene.spatial.composite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.UsageTrackingQueryCachingPolicy;
import org.apache.lucene.search.Weight;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.spatial.util.ShapeValuesPredicate;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;

/**
 * CompositeVerifyQuery's weight answers isCacheable from the shape predicate only and never asks
 * the weight of the index query it executes. When the index query reads a doc-values field that is
 * then updated in place, the segment keeps its core cache key, so a query cache keeps serving the
 * result computed from the old doc values.
 */
public class TestCompositeVerifyQueryCacheability extends LuceneTestCase {

  private static final SpatialContext CTX = SpatialContext.GEO;

  /** Every document has a point (i, i); docs 0-2 have v=1, docs 3-5 have v=2. */
  private static void index(IndexWriter w, SerializedDVStrategy shapes) throws IOException {
    for (int i = 0; i < 6; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.YES));
      doc.add(new NumericDocValuesField("v", i < 3 ? 1 : 2));
      for (Field f : shapes.createIndexableFields(CTX.getShapeFactory().pointXY(i, i))) {
        doc.add(f);
      }
      w.addDocument(doc);
    }
    w.commit();
  }

  /** A predicate that every indexed point satisfies. */
  private static ShapeValuesPredicate everywhere(SerializedDVStrategy shapes) {
    return new ShapeValuesPredicate(
        shapes.makeShapeValueSource(),
        SpatialOperation.Intersects,
        CTX.getShapeFactory().rect(-10, 10, -10, 10));
  }

  private static final QueryCachingPolicy ALWAYS_CACHE =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  private static IndexSearcher searcher(DirectoryReader reader, LRUQueryCache cache) {
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setQueryCache(cache);
    searcher.setQueryCachingPolicy(ALWAYS_CACHE);
    return searcher;
  }

  /** Sorted ids of the matching documents, collected without scores (so the cache is used). */
  private static List<String> ids(IndexSearcher searcher, Query query) throws IOException {
    List<Integer> docs =
        searcher.search(
            query,
            new CollectorManager<Collector, List<Integer>>() {
              final List<Integer> all = new ArrayList<>();

              @Override
              public Collector newCollector() {
                return new SimpleCollector() {
                  int docBase;

                  @Override
                  protected void doSetNextReader(LeafReaderContext context) {
                    docBase = context.docBase;
                  }

                  @Override
                  public void collect(int doc) {
                    synchronized (all) {
                      all.add(docBase + doc);
                    }
                  }

                  @Override
                  public ScoreMode scoreMode() {
                    return ScoreMode.COMPLETE_NO_SCORES;
                  }
                };
              }

              @Override
              public List<Integer> reduce(Collection<Collector> collectors) {
                return all;
              }
            });
    StoredFields storedFields = searcher.getIndexReader().storedFields();
    TreeSet<String> ids = new TreeSet<>();
    for (int doc : docs) {
      ids.add(storedFields.document(doc).get("id"));
    }
    return new ArrayList<>(ids);
  }

  public void testCachedHitsAfterInPlaceDocValuesUpdate() throws IOException {
    SerializedDVStrategy shapes = new SerializedDVStrategy(CTX, "shape");
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      index(w, shapes);
      Query indexQuery = NumericDocValuesField.newSlowExactQuery("v", 1);
      Query query = new CompositeVerifyQuery(indexQuery, everywhere(shapes));
      LRUQueryCache cache = new LRUQueryCache(100, 1 << 24, context -> true, Float.POSITIVE_INFINITY);

      try (DirectoryReader before = DirectoryReader.open(w)) {
        assertEquals(List.of("0", "1", "2"), ids(searcher(before, cache), query));
        assertTrue("the first search must fill the cache", cache.getCacheSize() > 0);

        // doc 0 no longer has v=1
        w.updateNumericDocValue(new Term("id", "0"), "v", 2L);
        try (DirectoryReader after = DirectoryReader.openIfChanged(before, w)) {
          assertNotNull(after);
          assertEquals(1, after.leaves().size());
          assertSame(
              "an in-place doc-values update keeps the segment's core cache key",
              before.leaves().get(0).reader().getCoreCacheHelper().getKey(),
              after.leaves().get(0).reader().getCoreCacheHelper().getKey());

          IndexSearcher uncached = new IndexSearcher(after);
          uncached.setQueryCache(null);
          assertEquals(List.of("1", "2"), ids(uncached, query));
          // the bare index query is correctly not cached any more
          assertEquals(List.of("1", "2"), ids(searcher(after, cache), indexQuery));

          assertEquals(
              "the cached CompositeVerifyQuery must see the updated doc values",
              List.of("1", "2"),
              ids(searcher(after, cache), query));
        }
      }
    }
  }

  public void testWeightIsNotCacheableWhenIndexQueryIsNot() throws IOException {
    SerializedDVStrategy shapes = new SerializedDVStrategy(CTX, "shape");
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      index(w, shapes);
      w.updateNumericDocValue(new Term("id", "0"), "v", 2L);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        LeafReaderContext leaf = reader.leaves().get(0);
        Query indexQuery = searcher.rewrite(NumericDocValuesField.newSlowExactQuery("v", 1));
        Weight indexWeight = indexQuery.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f);
        assertFalse(
            "precondition: the index query reads updated doc values", indexWeight.isCacheable(leaf));

        Query query = searcher.rewrite(new CompositeVerifyQuery(indexQuery, everywhere(shapes)));
        Weight weight = query.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f);
        assertFalse(
            "CompositeVerifyQuery executes the index query, so it cannot be more cacheable than it",
            weight.isCacheable(leaf));
      }
    }
  }

  /**
   * The same staleness with the query cache settings that IndexSearcher uses by default in the 10.x
   * releases (LRUQueryCache(1000, 32MB) with UsageTrackingQueryCachingPolicy; main disables the
   * default cache, see MIGRATE.md): one segment of more than 10,000 documents and a query that is
   * reused a few times.
   */
  public void testStaleCountWithReleaseDefaultCacheSettings() throws IOException {
    SerializedDVStrategy shapes = new SerializedDVStrategy(CTX, "shape");
    int numDocs = 10_050;
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(
                dir,
                new IndexWriterConfig()
                    .setMergePolicy(NoMergePolicy.INSTANCE)
                    .setMaxBufferedDocs(numDocs + 1)
                    .setRAMBufferSizeMB(256))) {
      for (int i = 0; i < numDocs; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(i), Field.Store.YES));
        doc.add(new NumericDocValuesField("v", i % 2 == 0 ? 1 : 2));
        for (Field f : shapes.createIndexableFields(CTX.getShapeFactory().pointXY(i % 7, i % 5))) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      w.commit();
      Query query =
          new CompositeVerifyQuery(NumericDocValuesField.newSlowExactQuery("v", 1), everywhere(shapes));
      LRUQueryCache cache = new LRUQueryCache(1000, 32L << 20);
      QueryCachingPolicy policy = new UsageTrackingQueryCachingPolicy();
      int expectedBefore = (numDocs + 1) / 2;
      try (DirectoryReader before = DirectoryReader.open(w)) {
        assertEquals(1, before.leaves().size());
        for (int i = 0; i < 10; i++) {
          IndexSearcher s = new IndexSearcher(before);
          s.setQueryCache(cache);
          s.setQueryCachingPolicy(policy);
          assertEquals(expectedBefore, s.count(query));
        }
        assertTrue("the repeated query must have been cached", cache.getCacheSize() > 0);

        w.updateNumericDocValue(new Term("id", "0"), "v", 2L); // doc 0 no longer has v=1
        try (DirectoryReader after = DirectoryReader.openIfChanged(before, w)) {
          assertNotNull(after);
          IndexSearcher uncached = new IndexSearcher(after);
          uncached.setQueryCache(null);
          assertEquals(expectedBefore - 1, uncached.count(query));

          IndexSearcher s = new IndexSearcher(after);
          s.setQueryCache(cache);
          s.setQueryCachingPolicy(policy);
          assertEquals(
              "the cached CompositeVerifyQuery must see the updated doc values",
              expectedBefore - 1,
              s.count(query));
        }
      }
    }
  }
}
