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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopFieldCollectorManager;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.NamedThreadFactory;

/**
 * Sequential DrillSideways creates the base query's weight with the hit collector's score mode. With
 * a TOP_SCORES hit collector (TopScoreDocCollectorManager, or a relevance-first
 * TopFieldCollectorManager, with a finite totalHitsThreshold) the hit collector's
 * setMinCompetitiveScore reaches the base scorer, which then skips documents that the sideways
 * facet collector still has to count. Sideways counts must not depend on how the hit collector
 * prunes its top hits; the concurrent DrillSideways and an exhaustive hit collector count them all.
 */
public class TestDrillSidewaysTopScoresCollector extends LuceneTestCase {

  /**
   * 2400 docs, every one in facet color/red. Docs 11-21: "a b c"; docs 0-10 and 22-99: "a b";
   * docs 100-2399: "z". So 100 docs match body:a.
   */
  private void check(
      Query base,
      Supplier<CollectorManager<?, ?>> topScoresHits,
      Supplier<CollectorManager<?, ?>> exhaustiveHits)
      throws Exception {
    // A fresh collector manager per search: managers with a hits threshold keep shared state.
    FacetsConfig config = new FacetsConfig();
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 2400; i++) {
          Document d = new Document();
          d.add(new SortedSetDocValuesFacetField("color", "red"));
          String body = i >= 11 && i <= 21 ? "a b c" : i < 100 ? "a b" : "z";
          d.add(new TextField("body", body, Field.Store.NO));
          w.addDocument(config.build(d));
        }
        w.forceMerge(1);
      }
      ExecutorService executor =
          Executors.newFixedThreadPool(2, new NamedThreadFactory("TestDrillSidewaysTopScores"));
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(reader, config);
        DrillDownQuery ddq = new DrillDownQuery(config, base);
        ddq.add("color", "red");
        DrillSideways sequential = new DrillSideways(searcher, config, state);
        DrillSideways concurrent = new DrillSideways(searcher, config, null, state, executor);

        // The "color" dimension is optional for its own sideways collector, so color/red must
        // count all 100 docs that match the base query.
        assertEquals(
            "sequential, exhaustive hit collector",
            100,
            sidewaysRedCount(sequential, ddq, exhaustiveHits.get(), state));
        assertEquals(
            "concurrent, TOP_SCORES hit collector",
            100,
            sidewaysRedCount(concurrent, ddq, topScoresHits.get(), state));
        assertEquals(
            "sequential, TOP_SCORES hit collector",
            100,
            sidewaysRedCount(sequential, ddq, topScoresHits.get(), state));
      } finally {
        executor.shutdown();
      }
    }
  }

  private static int sidewaysRedCount(
      DrillSideways ds,
      DrillDownQuery ddq,
      CollectorManager<?, ?> hits,
      SortedSetDocValuesReaderState state)
      throws Exception {
    DrillSideways.Result<?, FacetsCollector> res =
        ds.search(ddq, hits, List.of(new FacetsCollectorManager()));
    FacetsCollector sideways = res.drillSidewaysResults().get(0);
    return new SortedSetDocValuesFacetCounts(state, sideways)
        .getSpecificValue("color", "red")
        .intValue();
  }

  /** A filter-only base query with a top-score collector (TopScoreDocCollector, TOP_SCORES). */
  public void testFilterBaseWithTopScoreDocCollector() throws Exception {
    Query base =
        new BooleanQuery.Builder().add(new TermQuery(new Term("body", "a")), Occur.FILTER).build();
    check(
        base,
        () -> new TopScoreDocCollectorManager(10, 10),
        () -> new TopScoreDocCollectorManager(10, Integer.MAX_VALUE));
  }

  /** A scoring base query with a relevance-sorted TopFieldCollectorManager and a hits threshold. */
  public void testScoringBaseWithRelevanceSortedTopFieldCollector() throws Exception {
    Query base =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("body", "a")), Occur.SHOULD)
            .add(new TermQuery(new Term("body", "b")), Occur.SHOULD)
            .add(new TermQuery(new Term("body", "c")), Occur.SHOULD)
            .setMinimumNumberShouldMatch(2)
            .build();
    check(
        base,
        () -> new TopFieldCollectorManager(Sort.RELEVANCE, 10, 10),
        () -> new TopFieldCollectorManager(Sort.RELEVANCE, 10, Integer.MAX_VALUE));
  }
}
