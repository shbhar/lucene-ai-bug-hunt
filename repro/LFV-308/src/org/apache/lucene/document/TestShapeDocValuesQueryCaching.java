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
package org.apache.lucene.document;

import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * LatLonShape/XYShape#newSlowDocValuesBoxQuery read the shape from BinaryDocValues, but their
 * Weight#isCacheable is inherited unchanged from SpatialQuery (always true). After an in-place
 * update of the shape doc value, the query cache keeps serving the result computed from the old
 * shape.
 */
public class TestShapeDocValuesQueryCaching extends LuceneTestCase {

  private static final int NUM_DOCS = 20;

  private static final QueryCachingPolicy ALWAYS_CACHE =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  interface ShapeFieldFactory {
    Field create(int i);
  }

  /** Docs 0..4 lie inside the query box; doc 0 is then moved far outside it. */
  private void doTest(ShapeFieldFactory initial, Field moved, Query query) throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < NUM_DOCS; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
          doc.add(initial.create(i));
          w.addDocument(doc);
        }
        w.commit();

        LRUQueryCache cache = new LRUQueryCache(1000, 1 << 20, ctx -> true, Float.MAX_VALUE);
        try (DirectoryReader r1 = DirectoryReader.open(w)) {
          IndexSearcher s1 = new IndexSearcher(r1);
          s1.setQueryCache(cache);
          s1.setQueryCachingPolicy(ALWAYS_CACHE);
          assertEquals(5, s1.count(query)); // fills the cache entry for the segment

          // in-place doc-values update of doc 0's shape: same segment core
          w.updateDocValues(new Term("id", "0"), moved);
          try (DirectoryReader r2 = DirectoryReader.openIfChanged(r1, w)) {
            assertNotNull(r2);
            assertSame(
                r1.leaves().get(0).reader().getCoreCacheHelper().getKey(),
                r2.leaves().get(0).reader().getCoreCacheHelper().getKey());

            IndexSearcher uncached = new IndexSearcher(r2);
            uncached.setQueryCache(null);
            assertEquals(4, uncached.count(query));

            Weight weight =
                uncached.createWeight(uncached.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1f);
            boolean cacheable = weight.isCacheable(r2.leaves().get(0));

            IndexSearcher s2 = new IndexSearcher(r2);
            s2.setQueryCache(cache);
            s2.setQueryCachingPolicy(ALWAYS_CACHE);
            // Same query, same reader: the cached search must agree with the uncached one.
            assertEquals(
                "cached result differs from uncached result (isCacheable=" + cacheable + ")",
                4,
                s2.count(query));
          }
        }
      }
    }
  }

  private static Polygon latLonBox(double lat, double lon) {
    double d = 0.1;
    return new Polygon(
        new double[] {lat - d, lat - d, lat + d, lat + d, lat - d},
        new double[] {lon - d, lon + d, lon + d, lon - d, lon - d});
  }

  private static XYPolygon xyBox(float x, float y) {
    float d = 0.1f;
    return new XYPolygon(
        new float[] {x - d, x + d, x + d, x - d, x - d},
        new float[] {y - d, y - d, y + d, y + d, y - d});
  }

  public void testLatLonShapeDocValuesBoxQuery() throws Exception {
    doTest(
        i -> LatLonShape.createDocValueField("shape", latLonBox(i, i)),
        LatLonShape.createDocValueField("shape", latLonBox(60, 60)),
        LatLonShape.newSlowDocValuesBoxQuery("shape", QueryRelation.INTERSECTS, -1, 4.5, -1, 4.5));
  }

  public void testXYShapeDocValuesBoxQuery() throws Exception {
    doTest(
        i -> XYShape.createDocValueField("shape", xyBox(i, i)),
        XYShape.createDocValueField("shape", xyBox(60, 60)),
        XYShape.newSlowDocValuesBoxQuery("shape", QueryRelation.INTERSECTS, -1, 4.5f, -1, 4.5f));
  }

  /**
   * Control: a binary doc-values range query over the same layout (DocValues#isCacheable) honours
   * the update.
   */
  public void testBinaryRangeDocValuesQueryControl() throws Exception {
    doTest(
        i -> new IntRangeDocValuesField("shape", new int[] {i}, new int[] {i}),
        new IntRangeDocValuesField("shape", new int[] {60}, new int[] {60}),
        IntRangeDocValuesField.newSlowIntersectsQuery("shape", new int[] {-1}, new int[] {4}));
  }
}
