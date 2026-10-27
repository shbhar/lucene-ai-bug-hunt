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
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.FacetsCollector.MatchingDocs;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * RandomSamplingFacetsCollector computes samplingRate = sampleSize / totalHits, but samples one hit
 * per bin of binSize = (int) (1 / samplingRate) hits. The truncation makes the real sampling rate
 * 1 / binSize larger than samplingRate (up to twice as large), while amortizeFacetCounts divides by
 * samplingRate. With 1999 hits and sampleSize 1000, binSize is 1: every hit is "sampled", yet the
 * amortized counts are the true counts divided by 0.50025, i.e. about twice too high.
 */
public class TestRandomSamplingBinSize extends LuceneTestCase {

  public void testAmortizedCountsWhenEveryHitIsSampled() throws Exception {
    final int numDocs = 3998; // the query matches the 1999 even documents
    final int sampleSize = 1000;
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      FacetsConfig config = new FacetsConfig();
      try (DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
          IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < numDocs; i++) {
          Document doc = new Document();
          doc.add(new StringField("EvenOdd", i % 2 == 0 ? "even" : "odd", Field.Store.NO));
          doc.add(new FacetField("iMod3", Integer.toString(i % 3)));
          writer.addDocument(config.build(taxoWriter, doc));
        }
        writer.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir);
          TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TermQuery query = new TermQuery(new Term("EvenOdd", "even"));
        RandomSamplingFacetsCollector sampler =
            searcher.search(query, RandomSamplingFacetsCollector.createManager(sampleSize, 42L));

        int hits = 0, sampledDocs = 0;
        for (MatchingDocs md : sampler.getMatchingDocs()) {
          hits += md.totalHits();
          DocIdSetIterator it = md.bits().iterator();
          while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            sampledDocs++;
          }
        }
        assertEquals(1999, hits);
        double rate = sampler.getSamplingRate(); // on main: 1000 / 1999 = 0.50025...

        // exact counts over all hits
        FacetsCollector all = searcher.search(query, new FacetsCollectorManager());
        FacetResult exact =
            new FastTaxonomyFacetCounts(taxoReader, config, all).getTopChildren(10, "iMod3");
        FacetResult sampled =
            new FastTaxonomyFacetCounts(taxoReader, config, sampler).getTopChildren(10, "iMod3");
        FacetResult amortized = sampler.amortizeFacetCounts(sampled, config, searcher);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < exact.labelValues.length; i++) {
          sb.append(exact.labelValues[i].label)
              .append(": exact=")
              .append(exact.labelValues[i].value)
              .append(" sampled=")
              .append(sampled.labelValues[i].value)
              .append(" amortized=")
              .append(amortized.labelValues[i].value)
              .append("; ");
        }
        String detail =
            "hits=" + hits + " sampleSize=" + sampleSize + " sampledDocs=" + sampledDocs
                + " samplingRate=" + rate + " | " + sb;

        // amortized counts estimate the exact counts (here they should be close to exact)
        for (int i = 0; i < exact.labelValues.length; i++) {
          assertEquals(exact.labelValues[i].label, amortized.labelValues[i].label);
          double e = exact.labelValues[i].value.doubleValue();
          double a = amortized.labelValues[i].value.doubleValue();
          assertTrue("amortized " + a + " vs exact " + e + " | " + detail, Math.abs(a - e) <= 0.1 * e);
        }

        // The sample should be about sampleSize documents, i.e. a fraction ~samplingRate of the
        // hits (javadoc: "sampling will be done using a sample ratio of sampling size / totalN").
        assertTrue(
            "sampled fraction " + ((double) sampledDocs / hits) + " vs samplingRate " + rate
                + " | " + detail,
            Math.abs((double) sampledDocs / hits - rate) < 0.05);
      }
    }
  }
}
