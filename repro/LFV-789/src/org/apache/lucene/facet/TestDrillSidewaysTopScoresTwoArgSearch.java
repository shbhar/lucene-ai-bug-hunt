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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/** Same bug through DrillSideways#search(DrillDownQuery, CollectorManager) and its Facets. */
public class TestDrillSidewaysTopScoresTwoArgSearch extends LuceneTestCase {

  private static int redCount(boolean nullDrillDownFacets, int threshold) throws Exception {
    FacetsConfig config = new FacetsConfig();
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 2400; i++) {
          Document d = new Document();
          d.add(new SortedSetDocValuesFacetField("color", "red"));
          d.add(new TextField("body", i < 100 ? "a b" : "z", Field.Store.NO));
          w.addDocument(config.build(d));
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(reader, config);
        Query base =
            new BooleanQuery.Builder().add(new TermQuery(new Term("body", "a")), Occur.FILTER).build();
        DrillDownQuery ddq = new DrillDownQuery(config, base);
        ddq.add("color", "red");
        DrillSideways ds =
            nullDrillDownFacets
                ? new DrillSideways(searcher, config, state) {
                  @Override
                  protected FacetsCollectorManager createDrillDownFacetsCollectorManager() {
                    return null; // documented as valid: no drill-down facets needed
                  }
                }
                : new DrillSideways(searcher, config, state);
        DrillSideways.ConcurrentDrillSidewaysResult<?> r =
            ds.search(ddq, new TopScoreDocCollectorManager(10, threshold));
        return r.facets.getSpecificValue("color", "red").intValue();
      }
    }
  }

  public void testDefaultHooks() throws Exception {
    assertEquals(100, redCount(false, Integer.MAX_VALUE));
    assertEquals(100, redCount(false, 10));
  }

  public void testNoDrillDownFacets() throws Exception {
    assertEquals(100, redCount(true, Integer.MAX_VALUE));
    assertEquals(100, redCount(true, 10));
  }
}
