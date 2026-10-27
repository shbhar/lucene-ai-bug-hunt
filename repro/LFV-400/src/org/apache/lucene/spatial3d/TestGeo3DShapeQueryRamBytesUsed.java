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
package org.apache.lucene.spatial3d;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.RamUsageTester;
import org.apache.lucene.util.Accountable;

/**
 * Geo3DPoint polygon and path queries implement Accountable, and LRUQueryCache charges a cached
 * query its Accountable#ramBytesUsed(). PointInGeo3DShapeQuery reports a constant size whatever the
 * shape, because RamUsageEstimator.sizeOfObject(shape) prices any GeoShape as a 256-byte unknown
 * object.
 */
public class TestGeo3DShapeQueryRamBytesUsed extends LuceneTestCase {

  /** Heap retained by the query, without the shared PlanetModel singleton. */
  private static long retained(Object query) {
    return RamUsageTester.ramUsed(
        query,
        new RamUsageTester.Accumulator() {
          @Override
          public long accumulateObject(
              Object o, long shallowSize, Map<Field, Object> fieldValues, Collection<Object> queue) {
            for (Object v : fieldValues.values()) {
              if (v != null && v instanceof PlanetModel == false) {
                queue.add(v);
              }
            }
            return shallowSize;
          }
        });
  }

  /** A closed polygon of {@code n} vertices around (lat, lon), clockwise. */
  private static Polygon polygon(int n, double lat, double lon) {
    double[] lats = new double[n + 1];
    double[] lons = new double[n + 1];
    for (int i = 0; i < n; i++) {
      double a = -2 * Math.PI * i / n;
      lats[i] = lat + 30 * Math.sin(a);
      lons[i] = lon + 40 * Math.cos(a);
    }
    lats[n] = lats[0];
    lons[n] = lons[0];
    return new Polygon(lats, lons);
  }

  public void testRamBytesUsedCoversTheShape() {
    // newPolygonQuery is documented for polygons with "a limited number of edges (less than 100)";
    // newLargePolygonQuery "is optimized for very large numbers of polygon edges"; newPathQuery has
    // no documented limit on the number of points.
    double[] pathLats = new double[200];
    double[] pathLons = new double[200];
    for (int i = 0; i < 200; i++) {
      pathLats[i] = -50 + i * 0.5;
      pathLons[i] = -100 + i;
    }
    Query[] queries = {
      Geo3DPoint.newPolygonQuery("g", PlanetModel.WGS84, polygon(8, 0, 0)),
      Geo3DPoint.newPolygonQuery("g", PlanetModel.WGS84, polygon(99, 0, 0)),
      Geo3DPoint.newLargePolygonQuery("g", PlanetModel.WGS84, polygon(2000, 0, 0)),
      Geo3DPoint.newPathQuery("g", pathLats, pathLons, 10_000, PlanetModel.WGS84)
    };
    String[] names = {"polygon 8 edges", "polygon 99 edges", "large polygon 2000 edges", "path 200 points"};
    StringBuilder msg = new StringBuilder();
    boolean ok = true;
    for (int i = 0; i < queries.length; i++) {
      long reported = ((Accountable) queries[i]).ramBytesUsed();
      long retained = retained(queries[i]);
      msg.append(names[i])
          .append(": ramBytesUsed=")
          .append(reported)
          .append(" retained heap=")
          .append(retained)
          .append("; ");
      // Accountable#ramBytesUsed is an estimate of the heap this object uses: allow a factor of 2
      if (2 * reported < retained) {
        ok = false;
      }
    }
    assertTrue(msg.toString(), ok);
  }

  public void testQueryCacheStaysWithinItsRamBudget() throws Exception {
    final long maxRamBytesUsed = 1 << 20; // 1 MiB
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 100; i++) {
          Document doc = new Document();
          doc.add(new Geo3DPoint("g", PlanetModel.WGS84, -45 + i * 0.9, -90 + i * 1.8));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LRUQueryCache cache =
            new LRUQueryCache(1000, maxRamBytesUsed, context -> true, Float.POSITIVE_INFINITY);
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(cache);
        searcher.setQueryCachingPolicy(
            new QueryCachingPolicy() {
              @Override
              public void onUse(Query query) {}

              @Override
              public boolean shouldCache(Query query) {
                return true;
              }
            });
        List<Query> queries = new ArrayList<>();
        long retainedByKeys = 0;
        for (int i = 0; i < 8; i++) {
          Query q = Geo3DPoint.newLargePolygonQuery("g", PlanetModel.WGS84, polygon(2000, i, i));
          queries.add(q);
          searcher.search(new ConstantScoreQuery(q), 1); // non-scoring use: cached
          retainedByKeys += retained(q);
        }
        String msg =
            "cache entries="
                + cache.getCacheSize()
                + " evictions="
                + cache.getEvictionCount()
                + " cache.ramBytesUsed()="
                + cache.ramBytesUsed()
                + " maxRamBytesUsed="
                + maxRamBytesUsed
                + " heap retained by the cached query keys="
                + retainedByKeys;
        // every query is still cached (the cache believes it is far below its budget) ...
        assertEquals(msg, queries.size(), cache.getCacheSize());
        // ... so the heap the cache keeps alive must fit in maxRamBytesUsed
        assertTrue(msg, retainedByKeys <= maxRamBytesUsed);
      }
    }
  }
}
