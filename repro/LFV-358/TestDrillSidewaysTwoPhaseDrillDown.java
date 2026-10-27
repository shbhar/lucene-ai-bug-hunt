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
package org.apache.lucene.facet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.facet.range.LongRange;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.NamedThreadFactory;

/**
 * Sequential DrillSideways (no executor) must return the same hits and sideways counts as
 * concurrent DrillSideways, also when a drill-down dimension is a two-phase query such as a range
 * drill-down with a fast-match query.
 */
public class TestDrillSidewaysTwoPhaseDrillDown extends LuceneTestCase {

  public void testRangeDrillDownWithFastMatchQuery() throws Exception {
    // 200 docs; only docs 0-3 have a brand and a price:
    //   doc 0: acme, 5   doc 1: acme, 50   doc 2: other, 5   doc 3: other, 50
    String[] brands = {"acme", "acme", "other", "other"};
    long[] prices = {5, 50, 5, 50};
    FacetsConfig config = new FacetsConfig();
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 200; i++) {
          Document doc = new Document();
          if (i < 4) {
            doc.add(new SortedSetDocValuesFacetField("brand", brands[i]));
            doc.add(new LongPoint("price", prices[i]));
            doc.add(new NumericDocValuesField("price", prices[i]));
          }
          w.addDocument(config.build(doc));
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        assertFalse(reader.hasDeletions());
        IndexSearcher searcher = new IndexSearcher(reader);
        // A cached drill-down has no two-phase iterator; the test framework may install a cache.
        searcher.setQueryCache(null);
        SortedSetDocValuesReaderState state =
            new DefaultSortedSetDocValuesReaderState(reader, config);

        // Browse all docs, drill down on brand = acme and on price in [0, 10]. The range query
        // first takes the docs of the fast-match query (price in [0, 100]) as candidates, then
        // checks each candidate's exact value.
        Query cheap =
            new LongRange("cheap", 0, true, 10, true)
                .getQuery(
                    LongPoint.newRangeQuery("price", 0, 100),
                    LongValuesSource.fromLongField("price"));
        DrillDownQuery ddq = new DrillDownQuery(config);
        ddq.add("brand", "acme");
        ddq.add("price", cheap);

        // Hits: doc 0 only. Sideways counts for brand: the docs with price in [0, 10], i.e. doc 0
        // (acme) and doc 2 (other).
        List<Object> expected = List.of(List.of(0), new TreeMap<>(Map.of("acme", 1, "other", 1)));
        assertEquals(1, searcher.count(ddq));

        ExecutorService executor =
            Executors.newFixedThreadPool(2, new NamedThreadFactory("drill-sideways"));
        try {
          DrillSideways concurrent = new DrillSideways(searcher, config, null, state, executor);
          assertEquals("concurrent", expected, describe(concurrent.search(ddq, 10)));
        } finally {
          executor.shutdown();
          assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }

        DrillSideways sequential = new DrillSideways(searcher, config, state);
        assertEquals("sequential", expected, describe(sequential.search(ddq, 10)));
      }
    }
  }

  /** Returns [sorted hit doc ids, brand sideways counts]. */
  private static List<Object> describe(DrillSideways.DrillSidewaysResult r) throws Exception {
    List<Integer> hits = new ArrayList<>();
    for (ScoreDoc sd : r.hits.scoreDocs) {
      hits.add(sd.doc);
    }
    hits.sort(null);
    Map<String, Number> brand = new TreeMap<>();
    for (LabelAndValue lv : r.facets.getTopChildren(10, "brand").labelValues) {
      brand.put(lv.label, lv.value);
    }
    return List.of(hits, brand);
  }
}
