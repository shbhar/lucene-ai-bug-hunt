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
package repro.h61v1;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.IntFunction;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LatLonShape;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.XYShape;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.Scorable;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent repro (public API only; the class is outside every Lucene package).
 *
 * <p>Claim under test: the Weight of a shape doc-values query (LatLonShape/XYShape
 * newSlowDocValuesBoxQuery) reports isCacheable == true on a segment whose shape doc values were
 * updated in place, so a query cache entry built before the update is reused after the reopen
 * (the segment core cache key does not change on a doc-values update) and returns the old
 * matches. SegmentCacheable says doc-values-dependent objects should return
 * DocValues#isCacheable, which is false for updated fields.
 *
 * <p>Control: NumericDocValuesField.newSlowRangeQuery, same sequence, must stay correct.
 */
public class TestShapeDocValuesQueryCacheStaleness extends LuceneTestCase {

  private static final int NUM_INSIDE = 5;

  /** Caches every query on first use, so the test does not depend on usage-tracking heuristics. */
  private static final QueryCachingPolicy ALWAYS_CACHE =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  /** Collects global doc ids without scores, so the query cache is consulted. */
  private static final class DocListCollector implements Collector {
    final List<Integer> docs = new ArrayList<>();

    @Override
    public LeafCollector getLeafCollector(LeafReaderContext context) {
      final int base = context.docBase;
      return new LeafCollector() {
        @Override
        public void setScorer(Scorable scorer) {}

        @Override
        public void collect(int doc) {
          docs.add(base + doc);
        }
      };
    }

    @Override
    public ScoreMode scoreMode() {
      return ScoreMode.COMPLETE_NO_SCORES;
    }
  }

  private static List<Integer> matchingDocs(IndexSearcher searcher, Query query)
      throws IOException {
    return searcher.search(
        query,
        new CollectorManager<DocListCollector, List<Integer>>() {
          @Override
          public DocListCollector newCollector() {
            return new DocListCollector();
          }

          @Override
          public List<Integer> reduce(Collection<DocListCollector> collectors) {
            List<Integer> all = new ArrayList<>();
            for (DocListCollector c : collectors) {
              all.addAll(c.docs);
            }
            all.sort(null);
            return all;
          }
        });
  }

  private static IndexSearcher cachedSearcher(DirectoryReader reader, LRUQueryCache cache) {
    IndexSearcher s = new IndexSearcher(reader);
    s.setQueryCache(cache);
    s.setQueryCachingPolicy(ALWAYS_CACHE);
    return s;
  }

  private static IndexSearcher uncachedSearcher(DirectoryReader reader) {
    IndexSearcher s = new IndexSearcher(reader);
    s.setQueryCache(null);
    return s;
  }

  /**
   * @param field the doc-values field name
   * @param valueFactory produces the doc-values field; true = a value matched by {@code query},
   *     false = a value not matched
   * @param query a doc-values query on {@code field}
   */
  private void runScenario(String field, IntFunction<Field> valueFactory, Query query)
      throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        // docs 0..4 match, doc 5 does not; one flush -> one segment
        for (int i = 0; i <= NUM_INSIDE; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
          doc.add(valueFactory.apply(i < NUM_INSIDE ? 1 : 0));
          w.addDocument(doc);
        }
        w.commit();

        LRUQueryCache cache = new LRUQueryCache(100, 1 << 20, ctx -> true, 1000f);
        try (DirectoryReader r1 = DirectoryReader.open(dir)) {
          assertEquals(1, r1.leaves().size());
          List<Integer> before = matchingDocs(cachedSearcher(r1, cache), query);
          assertEquals(List.of(0, 1, 2, 3, 4), before);
          assertEquals("query must be cached before the update", 1, cache.getCacheSize());

          // move doc 0 out of the query region with an in-place doc-values update
          w.updateDocValues(new Term("id", "0"), valueFactory.apply(0));
          w.commit();

          try (DirectoryReader r2 = DirectoryReader.openIfChanged(r1)) {
            assertNotNull(r2);
            LeafReaderContext leaf1 = r1.leaves().get(0);
            LeafReaderContext leaf2 = r2.leaves().get(0);
            // same segment core => same cache key; the doc-values generation moved
            assertSame(
                leaf1.reader().getCoreCacheHelper().getKey(),
                leaf2.reader().getCoreCacheHelper().getKey());
            assertTrue(DocValues.isCacheable(leaf1, field));
            assertFalse(
                "doc values of the updated field are not cacheable",
                DocValues.isCacheable(leaf2, field));

            IndexSearcher uncached = uncachedSearcher(r2);
            List<Integer> truth = matchingDocs(uncached, query);
            assertEquals(List.of(1, 2, 3, 4), truth);

            Weight weight =
                uncached.createWeight(
                    uncached.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1f);
            boolean weightCacheable = weight.isCacheable(leaf2);

            List<Integer> viaCache = matchingDocs(cachedSearcher(r2, cache), query);
            assertEquals(
                "result through the query cache after a doc-values update ("
                    + query.getClass().getSimpleName()
                    + ", Weight#isCacheable on updated segment="
                    + weightCacheable
                    + ")",
                truth,
                viaCache);
            assertFalse(
                "Weight#isCacheable must be false on a segment with updated doc values ("
                    + query.getClass().getSimpleName()
                    + ")",
                weightCacheable);
          }
        }
      }
    }
  }

  public void testLatLonShapeDocValuesBoxQuery() throws IOException {
    final String f = "geo";
    runScenario(
        f,
        inside ->
            inside == 1
                ? LatLonShape.createDocValueField(f, 10.0, 20.0)
                : LatLonShape.createDocValueField(f, -50.0, -120.0),
        LatLonShape.newSlowDocValuesBoxQuery(f, QueryRelation.INTERSECTS, 0.0, 30.0, 0.0, 40.0));
  }

  public void testXYShapeDocValuesBoxQuery() throws IOException {
    final String f = "xy";
    runScenario(
        f,
        inside ->
            inside == 1
                ? XYShape.createDocValueField(f, 10f, 20f)
                : XYShape.createDocValueField(f, -500f, -900f),
        XYShape.newSlowDocValuesBoxQuery(f, QueryRelation.INTERSECTS, 0f, 30f, 0f, 40f));
  }

  /** Control: a numeric doc-values query, which uses DocValues#isCacheable, is not affected. */
  public void testControlNumericDocValuesRangeQuery() throws IOException {
    final String f = "num";
    runScenario(
        f,
        inside -> new NumericDocValuesField(f, inside == 1 ? 15L : 1000L),
        NumericDocValuesField.newSlowRangeQuery(f, 10L, 20L));
  }
}
