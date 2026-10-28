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
package org.apache.lucene.facet.range;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LateInteractionField;
import org.apache.lucene.document.NumericDocValuesField;
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
 * A range-facet drill-down query (DoubleRange#getQuery) over a late-interaction score, built
 * fresh for every request (no Query instance is reused; the queries are equal by value). After an
 * in-place update of the multi-vector the shared query cache serves the pre-update result. The
 * control uses the doc-values-backed DoubleValuesSource.fromLongField with an in-place numeric
 * update, which the cache handles correctly.
 */
public class TestLateInteractionRangeQueryCaching extends LuceneTestCase {

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

  private static Query lateInteractionRange() {
    return new DoubleRange("high", 0.75, true, Double.MAX_VALUE, true)
        .getQuery(null, new LateInteractionFloatValuesSource("mv", QUERY));
  }

  private static Query numericRange() {
    return new DoubleRange("high", 0.75, true, Double.MAX_VALUE, true)
        .getQuery(null, DoubleValuesSource.fromLongField("n"));
  }

  private static IndexWriter buildIndex(Directory dir) throws Exception {
    IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE));
    for (int i = 0; i < NUM_DOCS; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
      doc.add(
          new LateInteractionField(
              "mv", i < 5 ? new float[][] {{1f, 0f}} : new float[][] {{0f, 1f}}));
      doc.add(new NumericDocValuesField("n", i < 5 ? 1 : 0));
      w.addDocument(doc);
    }
    w.commit();
    return w;
  }

  private void check(boolean lateInteraction) throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = buildIndex(dir)) {
      LRUQueryCache cache = new LRUQueryCache(1000, 1 << 20, ctx -> true, Float.MAX_VALUE);
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        IndexSearcher s1 = new IndexSearcher(r1);
        s1.setQueryCache(cache);
        s1.setQueryCachingPolicy(ALWAYS_CACHE);
        assertEquals(5, s1.count(lateInteraction ? lateInteractionRange() : numericRange()));

        if (lateInteraction) {
          w.updateBinaryDocValue(
              new Term("id", "0"), "mv", LateInteractionField.encode(new float[][] {{0f, 1f}}));
        } else {
          w.updateNumericDocValue(new Term("id", "0"), "n", 0L);
        }
        try (DirectoryReader r2 = DirectoryReader.openIfChanged(r1, w)) {
          assertNotNull(r2);
          IndexSearcher uncached = new IndexSearcher(r2);
          uncached.setQueryCache(null);
          int expected =
              uncached.count(lateInteraction ? lateInteractionRange() : numericRange());
          assertEquals(4, expected);

          IndexSearcher s2 = new IndexSearcher(r2);
          s2.setQueryCache(cache);
          s2.setQueryCachingPolicy(ALWAYS_CACHE);
          assertEquals(
              "cached result differs from uncached result",
              expected,
              s2.count(lateInteraction ? lateInteractionRange() : numericRange()));
        }
      }
    }
  }

  public void testLateInteractionRangeQueryAfterUpdate() throws Exception {
    check(true);
  }

  public void testControlNumericRangeQueryAfterUpdate() throws Exception {
    check(false);
  }
}
