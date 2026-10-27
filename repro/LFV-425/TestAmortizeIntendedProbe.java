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
import org.apache.lucene.facet.FacetsConfig.DrillDownTermsIndexing;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Devil's-advocate probe: with DrillDownTermsIndexing.NONE, is a 0 amortized count "consistent"
 * with what the application can see elsewhere? Prints, does not assert the bug.
 */
public class TestAmortizeIntendedProbe extends LuceneTestCase {

  public void testTaxonomyNone() throws Exception {
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      FacetsConfig config = new FacetsConfig();
      config.setDrillDownTermsIndexing("iMod10", DrillDownTermsIndexing.NONE);
      try (DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
          IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 1000; i++) {
          Document doc = new Document();
          doc.add(new FacetField("iMod10", Integer.toString(i % 10)));
          writer.addDocument(config.build(taxoWriter, doc));
        }
        writer.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir);
          TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        // 1) unsampled counting is unaffected by NONE
        FacetsCollector fc = searcher.search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
        FacetResult exact = new FastTaxonomyFacetCounts(taxoReader, config, fc).getTopChildren(10, "iMod10");
        System.out.println("PROBE exact (unsampled) counts with NONE: " + exact);
        // 2) drill-down is disabled by NONE (the user opted out of it)
        DrillDownQuery ddq = new DrillDownQuery(config);
        ddq.add("iMod10", "3");
        System.out.println("PROBE drill-down hit count for iMod10/3 with NONE: " + searcher.count(ddq));
        // 3) sampled + amortized
        RandomSamplingFacetsCollector sampler =
            searcher.search(MatchAllDocsQuery.INSTANCE, RandomSamplingFacetsCollector.createManager(100, 42L));
        FacetResult sampled = new FastTaxonomyFacetCounts(taxoReader, config, sampler).getTopChildren(10, "iMod10");
        FacetResult amortized = sampler.amortizeFacetCounts(sampled, config, searcher);
        System.out.println("PROBE sampled: " + sampled);
        System.out.println("PROBE amortized: " + amortized);
      }
    }
  }

  public void testSortedSetNone() throws Exception {
    try (Directory dir = newDirectory()) {
      FacetsConfig config = new FacetsConfig();
      config.setDrillDownTermsIndexing("iMod10", DrillDownTermsIndexing.NONE);
      try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 1000; i++) {
          Document doc = new Document();
          doc.add(new SortedSetDocValuesFacetField("iMod10", Integer.toString(i % 10)));
          writer.addDocument(config.build(doc));
        }
        writer.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(reader, config);
        RandomSamplingFacetsCollector sampler =
            searcher.search(MatchAllDocsQuery.INSTANCE, RandomSamplingFacetsCollector.createManager(100, 42L));
        FacetResult sampled = new SortedSetDocValuesFacetCounts(state, sampler).getTopChildren(10, "iMod10");
        FacetResult amortized = sampler.amortizeFacetCounts(sampled, config, searcher);
        System.out.println("PROBE SSDV sampled: " + sampled);
        System.out.println("PROBE SSDV amortized: " + amortized);
      }
    }
  }
}
