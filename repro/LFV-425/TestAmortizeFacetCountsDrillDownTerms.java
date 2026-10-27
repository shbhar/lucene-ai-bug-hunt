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
 * RandomSamplingFacetsCollector#amortizeFacetCounts scales each sampled count by 1/samplingRate
 * and caps it at the docFreq of the label's drill-down term. FacetsConfig#setDrillDownTermsIndexing
 * (LUCENE-9385) lets a dimension skip indexing some or all drill-down terms; then the docFreq is 0
 * and every amortized count becomes 0, even for labels that were sampled.
 */
public class TestAmortizeFacetCountsDrillDownTerms extends LuceneTestCase {

  private static final int NUM_DOCS = 1000;
  private static final int SAMPLE_SIZE = 100; // samplingRate = 0.1

  /** Samples 10% of a flat dimension "iMod10" (10 labels x 100 docs) and checks amortization. */
  private static void checkFlat(DrillDownTermsIndexing indexing) throws Exception {
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      FacetsConfig config = new FacetsConfig();
      config.setDrillDownTermsIndexing("iMod10", indexing);
      try (DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
          IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < NUM_DOCS; i++) {
          Document doc = new Document();
          doc.add(new FacetField("iMod10", Integer.toString(i % 10)));
          writer.addDocument(config.build(taxoWriter, doc));
        }
        writer.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir);
          TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        RandomSamplingFacetsCollector sampler =
            searcher.search(
                MatchAllDocsQuery.INSTANCE,
                RandomSamplingFacetsCollector.createManager(SAMPLE_SIZE, 42L));
        FacetResult sampled =
            new FastTaxonomyFacetCounts(taxoReader, config, sampler).getTopChildren(10, "iMod10");
        FacetResult amortized = sampler.amortizeFacetCounts(sampled, config, searcher);
        assertEquals(0.1, sampler.getSamplingRate(), 0.0);
        assertEquals(sampled.labelValues.length, amortized.labelValues.length);
        for (int i = 0; i < sampled.labelValues.length; i++) {
          int s = sampled.labelValues[i].value.intValue();
          int a = amortized.labelValues[i].value.intValue();
          assertTrue("label was sampled", s > 0);
          // Every label really occurs in NUM_DOCS / 10 = 100 documents. With the default indexing
          // the amortized count is min(s / samplingRate, 100) (see upstream
          // TestRandomSamplingFacetsCollector). Whatever bound is used, an amortized count can
          // never be below the number s of sampled documents that have the label, nor above the
          // unbounded estimate s / samplingRate.
          String msg =
              indexing + ": amortized count of label " + sampled.labelValues[i].label
                  + " (sampled " + s + ", s / samplingRate = " + (10 * s) + ") was " + a;
          assertTrue(msg, a >= s && a <= 10 * s + 1);
        }
      }
    }
  }

  public void testDefaultIndexing() throws Exception {
    checkFlat(DrillDownTermsIndexing.ALL); // control: passes
  }

  public void testNoDrillDownTerms() throws Exception {
    checkFlat(DrillDownTermsIndexing.NONE); // fails: every amortized count is 0
  }

  public void testHierarchicalFullPathOnly() throws Exception {
    // a hierarchical dimension that indexes only full-path drill-down terms: the children of the
    // dimension ("a/x", "a/y") are not full paths, so their amortized counts are capped at 0
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      FacetsConfig config = new FacetsConfig();
      config.setHierarchical("a", true);
      config.setDrillDownTermsIndexing("a", DrillDownTermsIndexing.FULL_PATH_ONLY);
      try (DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
          IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < NUM_DOCS; i++) {
          Document doc = new Document();
          doc.add(new FacetField("a", i % 2 == 0 ? "x" : "y", Integer.toString(i % 5)));
          writer.addDocument(config.build(taxoWriter, doc));
        }
        writer.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir);
          TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        RandomSamplingFacetsCollector sampler =
            searcher.search(
                MatchAllDocsQuery.INSTANCE,
                RandomSamplingFacetsCollector.createManager(SAMPLE_SIZE, 42L));
        FacetResult sampled =
            new FastTaxonomyFacetCounts(taxoReader, config, sampler).getTopChildren(10, "a");
        FacetResult amortized = sampler.amortizeFacetCounts(sampled, config, searcher);
        for (int i = 0; i < sampled.labelValues.length; i++) {
          int s = sampled.labelValues[i].value.intValue();
          int a = amortized.labelValues[i].value.intValue();
          assertTrue("label was sampled", s > 0);
          String msg =
              "FULL_PATH_ONLY: amortized count of a/" + sampled.labelValues[i].label
                  + " (sampled " + s + ", s / samplingRate = " + (10 * s) + ") was " + a;
          assertTrue(msg, a >= s && a <= 10 * s + 1);
        }
      }
    }
  }

  public void testHierarchicalFullPathOnlyMixedDepth() throws Exception {
    // FULL_PATH_ONLY, hierarchical: half of the documents have the full path a/x, the other half
    // a/x/1. Label x has 1000 documents but only the a/x documents index the term "a/x", so its
    // docFreq (500) caps the amortized count at half the true count.
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      FacetsConfig config = new FacetsConfig();
      config.setHierarchical("a", true);
      config.setDrillDownTermsIndexing("a", DrillDownTermsIndexing.FULL_PATH_ONLY);
      try (DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
          IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < NUM_DOCS; i++) {
          Document doc = new Document();
          doc.add(i % 2 == 0 ? new FacetField("a", "x") : new FacetField("a", "x", "1"));
          writer.addDocument(config.build(taxoWriter, doc));
        }
        writer.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir);
          TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        RandomSamplingFacetsCollector sampler =
            searcher.search(
                MatchAllDocsQuery.INSTANCE,
                RandomSamplingFacetsCollector.createManager(SAMPLE_SIZE, 42L));
        FacetResult sampled =
            new FastTaxonomyFacetCounts(taxoReader, config, sampler).getTopChildren(10, "a");
        FacetResult amortized = sampler.amortizeFacetCounts(sampled, config, searcher);
        assertEquals(1, sampled.labelValues.length);
        int s = sampled.labelValues[0].value.intValue(); // every sampled document has label x
        int a = amortized.labelValues[0].value.intValue();
        String msg =
            "FULL_PATH_ONLY mixed depth: amortized count of a/x (sampled " + s
                + ", s / samplingRate = " + (10 * s) + ", true count " + NUM_DOCS + ") was " + a;
        // a correct bound is >= the true count, so the estimate is min(10 * s, true count)
        assertEquals(msg, Math.min(10 * s, NUM_DOCS), a, 1.0);
      }
    }
  }
}
