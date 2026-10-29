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
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestAmortizeWithoutDrillDownTerms extends LuceneTestCase {

  public void testAmortizedCountWithDrillDownTermsIndexingNone() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setDrillDownTermsIndexing("dim", FacetsConfig.DrillDownTermsIndexing.NONE);
    ByteBuffersDirectory dir = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    for (int i = 0; i < 2; i++) {
      Document doc = new Document();
      doc.add(new SortedSetDocValuesFacetField("dim", "x"));
      w.addDocument(config.build(doc));
    }
    w.close();
    DirectoryReader r = DirectoryReader.open(dir);
    IndexSearcher searcher = new IndexSearcher(r);
    RandomSamplingFacetsCollector sampler =
        searcher.search(
            MatchAllDocsQuery.INSTANCE, RandomSamplingFacetsCollector.createManager(1, 42));
    FacetResult sampled =
        new SortedSetDocValuesFacetCounts(
                new DefaultSortedSetDocValuesReaderState(r, config), sampler)
            .getTopChildren(10, "dim");
    FacetResult amortized = sampler.amortizeFacetCounts(sampled, config, searcher);
    r.close();
    dir.close();
    // expected: 1 sampled hit / sampling rate 0.5 = 2 (both docs have x); actual: 0
    assertEquals("amortized count of dim/x", 2, amortized.labelValues[0].value.intValue());
  }
}
