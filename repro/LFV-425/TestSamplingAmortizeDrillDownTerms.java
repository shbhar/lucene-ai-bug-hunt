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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetsConfig.DrillDownTermsIndexing;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * {@link RandomSamplingFacetsCollector#amortizeFacetCounts} caps each estimate at the docFreq of
 * the label's drill-down term. A non-default {@link DrillDownTermsIndexing} does not index that
 * term (docFreq 0) or indexes it only for some of the documents with the label.
 */
public class TestSamplingAmortizeDrillDownTerms extends LuceneTestCase {

  private static final int NUM_DOCS = 1000;
  private static final int SAMPLE_SIZE = 100; // sampling rate 0.1

  public void testDefaultIndexingControl() throws Exception {
    FacetsConfig config = new FacetsConfig(); // DrillDownTermsIndexing.ALL: passes
    assertAmortizedCounts(
        config, false, i -> new FacetField("iMod10", Integer.toString(i % 10)), "iMod10");
  }

  public void testNoDrillDownTerms() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setDrillDownTermsIndexing("iMod10", DrillDownTermsIndexing.NONE);
    assertAmortizedCounts(
        config, false, i -> new FacetField("iMod10", Integer.toString(i % 10)), "iMod10");
  }

  public void testNoDrillDownTermsSortedSet() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setDrillDownTermsIndexing("iMod10", DrillDownTermsIndexing.NONE);
    assertAmortizedCounts(
        config,
        true,
        i -> new SortedSetDocValuesFacetField("iMod10", Integer.toString(i % 10)),
        "iMod10");
  }

  public void testFullPathOnlyHierarchical() throws Exception {
    // labels x and y of dimension a are never a full path, so their terms are not indexed
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("a", true);
    config.setDrillDownTermsIndexing("a", DrillDownTermsIndexing.FULL_PATH_ONLY);
    assertAmortizedCounts(
        config,
        false,
        i -> new FacetField("a", i % 2 == 0 ? "x" : "y", Integer.toString(i % 5)),
        "a");
  }

  public void testFullPathOnlyHierarchicalSortedSet() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("a", true);
    config.setDrillDownTermsIndexing("a", DrillDownTermsIndexing.FULL_PATH_ONLY);
    assertAmortizedCounts(
        config,
        true,
        i -> new SortedSetDocValuesFacetField("a", i % 2 == 0 ? "x" : "y", Integer.toString(i % 5)),
        "a");
  }

  public void testFullPathOnlyMixedDepth() throws Exception {
    // all 1000 documents have label x, but only the 500 documents a/x index the term "a/x"
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("a", true);
    config.setDrillDownTermsIndexing("a", DrillDownTermsIndexing.FULL_PATH_ONLY);
    assertAmortizedCounts(
        config,
        false,
        i -> i % 2 == 0 ? new FacetField("a", "x") : new FacetField("a", "x", "1"),
        "a");
  }

  /**
   * Indexes NUM_DOCS documents, samples them with SAMPLE_SIZE and checks each amortized child count
   * of dim: it is the estimate sampled / samplingRate, lowered by a documented "upper bound", so it
   * may not be above that estimate, nor below min(true count, estimate).
   */
  private void assertAmortizedCounts(
      FacetsConfig config, boolean sortedSet, IntFunction<IndexableField> facet, String dim)
      throws IOException {
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      try (DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
          IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < NUM_DOCS; i++) {
          Document doc = new Document();
          doc.add(facet.apply(i));
          writer.addDocument(config.build(taxoWriter, doc));
        }
        writer.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir);
          TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        FacetsCollector all =
            searcher.search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
        RandomSamplingFacetsCollector sampler =
            searcher.search(
                MatchAllDocsQuery.INSTANCE,
                RandomSamplingFacetsCollector.createManager(SAMPLE_SIZE, 42L));
        FacetResult exact =
            counts(config, sortedSet, reader, taxoReader, all).getTopChildren(10, dim);
        FacetResult sampled =
            counts(config, sortedSet, reader, taxoReader, sampler).getTopChildren(10, dim);
        FacetResult amortized = sampler.amortizeFacetCounts(sampled, config, searcher);
        assertEquals(0.1, sampler.getSamplingRate(), 0.0);

        Map<String, Integer> trueCounts = new HashMap<>();
        for (LabelAndValue lv : exact.labelValues) {
          trueCounts.put(lv.label, lv.value.intValue());
        }
        List<String> wrong = new ArrayList<>();
        for (int i = 0; i < sampled.labelValues.length; i++) {
          String label = sampled.labelValues[i].label;
          int s = sampled.labelValues[i].value.intValue();
          int estimate = (int) (s / sampler.getSamplingRate());
          int trueCount = trueCounts.get(label);
          int a = amortized.labelValues[i].value.intValue();
          if (a > estimate || a < Math.min(trueCount, estimate)) {
            wrong.add(
                String.format(
                    Locale.ROOT,
                    "%s: sampled %d, estimate %d, true count %d, amortized %d",
                    label,
                    s,
                    estimate,
                    trueCount,
                    a));
          }
        }
        assertTrue(
            String.format(
                Locale.ROOT,
                "%s: %d of %d labels wrong (amortized total %s, true total %s), first: %s",
                dim,
                wrong.size(),
                sampled.labelValues.length,
                amortized.value,
                exact.value,
                wrong.subList(0, Math.min(2, wrong.size()))),
            wrong.isEmpty());
      }
    }
  }

  private static Facets counts(
      FacetsConfig config,
      boolean sortedSet,
      IndexReader reader,
      TaxonomyReader taxoReader,
      FacetsCollector hits)
      throws IOException {
    return sortedSet
        ? new SortedSetDocValuesFacetCounts(
            new DefaultSortedSetDocValuesReaderState(reader, config), hits)
        : new FastTaxonomyFacetCounts(taxoReader, config, hits);
  }
}
