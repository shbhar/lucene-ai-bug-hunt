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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestDrillSidewaysTopScoresHitCollector extends LuceneTestCase {

  public void testSidewaysCountsDoNotDependOnHitCollectorThreshold() throws Exception {
    FacetsConfig config = new FacetsConfig();
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        // 4000 docs, half red and half blue; the first 150 are shoes (75 red, 75 blue)
        for (int i = 0; i < 4000; i++) {
          Document doc = new Document();
          doc.add(new SortedSetDocValuesFacetField("color", i % 2 == 0 ? "red" : "blue"));
          if (i < 150) {
            doc.add(new StringField("category", "shoes", Field.Store.NO));
          }
          w.addDocument(config.build(doc));
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null); // as on main by default
        SortedSetDocValuesReaderState state =
            new DefaultSortedSetDocValuesReaderState(reader, config);

        // filter on shoes, drill down on color=red
        Query shoes =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("category", "shoes")), Occur.FILTER)
                .build();
        DrillDownQuery ddq = new DrillDownQuery(config, shoes);
        ddq.add("color", "red");

        // no executor: the sequential DrillSideways; top 10 hits, count hits up to 10
        DrillSideways ds = new DrillSideways(searcher, config, state);
        DrillSideways.Result<TopDocs, FacetsCollector> result =
            ds.search(
                ddq,
                new TopScoreDocCollectorManager(10, 10),
                List.of(new FacetsCollectorManager()));

        // the color dimension ignores its own drill-down: every shoe counts, 75 red and 75 blue
        FacetsCollector sideways = result.drillSidewaysResults().get(0);
        SortedSetDocValuesFacetCounts counts = new SortedSetDocValuesFacetCounts(state, sideways);
        int red = counts.getSpecificValue("color", "red").intValue();
        int blue = counts.getSpecificValue("color", "blue").intValue();
        assertEquals("sideways color counts red/blue", "75/75", red + "/" + blue);
      }
    }
  }
}
