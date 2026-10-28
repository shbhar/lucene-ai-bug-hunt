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

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A shape doc-values box query used as a restriction inside a larger query. One request fills a
 * shared query cache; the application then moves a document's shape with an in-place doc-values
 * update and reopens; a different request that carries the same restriction is answered from the
 * entry computed from the old shape. Each test asserts the uncached (correct) answer.
 */
public class TestShapeDvCacheRestrictionProbe extends LuceneTestCase {

  static final int NUM_DOCS = 20;

  static final QueryCachingPolicy ALWAYS_CACHE =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  static Polygon box(double c) {
    double d = 0.1;
    return new Polygon(
        new double[] {c - d, c - d, c + d, c + d, c - d},
        new double[] {c - d, c + d, c + d, c - d, c - d});
  }

  /** The region: shapes centred at 0..4 intersect it, shapes centred at 5 and above do not. */
  static Query region() {
    return LatLonShape.newSlowDocValuesBoxQuery("shape", QueryRelation.INTERSECTS, -1, 4.5, -1, 4.5);
  }

  static Query allowed(String term) {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("body", term)), Occur.MUST)
        .add(region(), Occur.FILTER)
        .build();
  }

  static Query denied(String term) {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("body", term)), Occur.MUST)
        .add(region(), Occur.MUST_NOT)
        .build();
  }

  static IndexWriter newWriter(Directory dir) throws IOException {
    IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE));
    for (int i = 0; i < NUM_DOCS; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.YES));
      doc.add(new StringField("body", "x", Field.Store.NO));
      doc.add(new StringField("body", "y", Field.Store.NO));
      doc.add(LatLonShape.createDocValueField("shape", box(i)));
      w.addDocument(doc);
    }
    w.commit();
    return w;
  }

  static IndexSearcher cached(DirectoryReader r, LRUQueryCache cache) {
    IndexSearcher s = new IndexSearcher(r);
    s.setQueryCache(cache);
    s.setQueryCachingPolicy(ALWAYS_CACHE);
    return s;
  }

  static IndexSearcher uncached(DirectoryReader r) {
    IndexSearcher s = new IndexSearcher(r);
    s.setQueryCache(null);
    return s;
  }

  static Set<Integer> ids(IndexSearcher s, Query q) throws IOException {
    Set<Integer> out = new TreeSet<>();
    StoredFields sf = s.getIndexReader().storedFields();
    for (ScoreDoc sd : s.search(q, 1000).scoreDocs) {
      out.add(Integer.parseInt(sf.document(sd.doc).get("id")));
    }
    return out;
  }

  /** FILTER restriction: a document moved out of the region is still returned. */
  public void testAllowRegionReturnsDocMovedOut() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = newWriter(dir)) {
      LRUQueryCache cache = new LRUQueryCache(1000, 1 << 20, ctx -> true, Float.MAX_VALUE);
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        assertEquals(Set.of(0, 1, 2, 3, 4), ids(cached(r1, cache), allowed("x"))); // request A
        w.updateDocValues(new Term("id", "0"), LatLonShape.createDocValueField("shape", box(60)));
        try (DirectoryReader r2 = DirectoryReader.openIfChanged(r1, w)) {
          Set<Integer> expected = ids(uncached(r2), allowed("y"));
          assertEquals(Set.of(1, 2, 3, 4), expected);
          Set<Integer> actual = ids(cached(r2, cache), allowed("y")); // request B, never run before
          assertEquals("FILTER region, request B through the shared cache", expected, actual);
        }
      }
    }
  }

  /** MUST_NOT restriction: a document moved into the excluded region is still returned. */
  public void testDenyRegionReturnsDocMovedIn() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = newWriter(dir)) {
      LRUQueryCache cache = new LRUQueryCache(1000, 1 << 20, ctx -> true, Float.MAX_VALUE);
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        Set<Integer> before = ids(cached(r1, cache), denied("x")); // request A
        assertEquals(15, before.size());
        assertTrue(before.contains(10));
        w.updateDocValues(new Term("id", "10"), LatLonShape.createDocValueField("shape", box(2)));
        try (DirectoryReader r2 = DirectoryReader.openIfChanged(r1, w)) {
          Set<Integer> expected = ids(uncached(r2), denied("y"));
          assertEquals(14, expected.size());
          assertFalse(expected.contains(10));
          Set<Integer> actual = ids(cached(r2, cache), denied("y")); // request B, never run before
          assertEquals("MUST_NOT region, request B through the shared cache", expected, actual);
        }
      }
    }
  }
}
