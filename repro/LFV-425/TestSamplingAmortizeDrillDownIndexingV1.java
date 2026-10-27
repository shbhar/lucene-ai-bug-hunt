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
import java.util.List;
import java.util.function.IntFunction;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
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
import org.apache.lucene.util.IOUtils;

/**
 * Independent verifier test (public API only).
 *
 * <p>RandomSamplingFacetsCollector#amortizeFacetCounts documents that it "Uses the FacetsConfig and
 * the IndexSearcher to determine the upper bound for each facet value". A valid upper bound is
 * never below the true count. So for each label, with s = sampled count and r = sampling rate:
 *
 * <pre>
 *   amortized &gt;= min(trueCount, (int) (s / r))   and   amortized &gt;= s
 * </pre>
 *
 * The check runs over all FacetsConfig.DrillDownTermsIndexing values. The query is
 * MatchAllDocsQuery, so the true count of a label is the exact (non-sampled) facet count.
 */
public class TestSamplingAmortizeDrillDownIndexingV1 extends LuceneTestCase {

  private static final int NUM_DOCS = 10000;
  private static final int SAMPLE_SIZE = 1000; // sampling rate 0.1
  private static final long SAMPLING_SEED = 42L;

  /** One label's numbers. */
  private static final class Row {
    final String label;
    final int sampled;
    final int expectedEstimate; // (int) (sampled / rate)
    final int exact;
    final int amortized;

    Row(String label, int sampled, int expectedEstimate, int exact, int amortized) {
      this.label = label;
      this.sampled = sampled;
      this.expectedEstimate = expectedEstimate;
      this.exact = exact;
      this.amortized = amortized;
    }

    @Override
    public String toString() {
      return label
          + "{sampled="
          + sampled
          + ", sampled/rate="
          + expectedEstimate
          + ", exact="
          + exact
          + ", amortized="
          + amortized
          + "}";
    }
  }

  private interface FacetsFactory {
    Facets create(FacetsCollector fc) throws IOException;
  }

  private static List<Row> amortizeAndCompare(
      IndexSearcher searcher, FacetsConfig config, FacetsFactory factory, String dim, String... path)
      throws IOException {
    RandomSamplingFacetsCollector sampling =
        new RandomSamplingFacetsCollector(SAMPLE_SIZE, SAMPLING_SEED);
    searcher.search(new MatchAllDocsQuery(), sampling);
    FacetsCollector exactCollector = new FacetsCollector();
    searcher.search(new MatchAllDocsQuery(), exactCollector);

    Facets sampledFacets = factory.create(sampling);
    Facets exactFacets = factory.create(exactCollector);

    FacetResult sampled = sampledFacets.getTopChildren(10, dim, path);
    assertNotNull(sampled);
    double rate = sampling.getSamplingRate();
    assertTrue("sampling must be active, rate=" + rate, rate > 0 && rate < 1);

    FacetResult amortized = sampling.amortizeFacetCounts(sampled, config, searcher);
    assertEquals(sampled.labelValues.length, amortized.labelValues.length);

    List<Row> rows = new ArrayList<>();
    for (int i = 0; i < sampled.labelValues.length; i++) {
      String label = sampled.labelValues[i].label;
      assertEquals(label, amortized.labelValues[i].label);
      String[] fullPath = new String[path.length + 1];
      System.arraycopy(path, 0, fullPath, 0, path.length);
      fullPath[path.length] = label;
      int exact = exactFacets.getSpecificValue(dim, fullPath).intValue();
      int s = sampled.labelValues[i].value.intValue();
      rows.add(
          new Row(label, s, (int) (s / rate), exact, amortized.labelValues[i].value.intValue()));
    }
    if (VERBOSE) {
      System.out.println(
          "dim="
              + dim
              + " rate="
              + rate
              + " sampledDimValue="
              + sampled.value
              + " amortizedDimValue="
              + amortized.value
              + " rows="
              + rows);
    }
    return rows;
  }

  private static void assertValidUpperBound(String what, List<Row> rows) {
    assertFalse(what + ": no labels", rows.isEmpty());
    for (Row r : rows) {
      assertTrue(what + ": label not sampled, test data too small: " + r, r.sampled > 0);
      assertTrue(
          what + ": amortized count is below the sampled count: " + r, r.amortized >= r.sampled);
      int floor = Math.min(r.exact, r.expectedEstimate);
      assertTrue(
          what
              + ": amortized count is below min(exact count, sampled/rate) = "
              + floor
              + ", so the 'upper bound' used was below the true count: "
              + r,
          r.amortized >= floor);
    }
  }

  // ---------------------------------------------------------------- taxonomy helpers

  private List<Row> runTaxonomy(
      FacetsConfig config, IntFunction<FacetField[]> fieldsForDoc, String dim, String... path)
      throws IOException {
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig());
    for (int i = 0; i < NUM_DOCS; i++) {
      Document doc = new Document();
      for (FacetField f : fieldsForDoc.apply(i)) {
        doc.add(f);
      }
      writer.addDocument(config.build(taxoWriter, doc));
    }
    writer.commit();
    taxoWriter.commit();
    DirectoryReader reader = DirectoryReader.open(writer);
    TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);
    try {
      IndexSearcher searcher = new IndexSearcher(reader);
      return amortizeAndCompare(
          searcher, config, fc -> new FastTaxonomyFacetCounts(taxoReader, config, fc), dim, path);
    } finally {
      IOUtils.close(reader, taxoReader, writer, taxoWriter, dir, taxoDir);
    }
  }

  private static FacetField[] flat(int i) {
    return new FacetField[] {new FacetField("f", "v" + (i % 4))};
  }

  private static FacetField[] hier(int i) {
    return new FacetField[] {new FacetField("h", (i % 2 == 0) ? "x" : "y", "c" + (i % 3))};
  }

  private static FacetConfigFlat config(String dim, FacetsConfig.DrillDownTermsIndexing ddti) {
    return new FacetConfigFlat(dim, ddti);
  }

  /** Tiny helper so each test reads as one line. */
  private static final class FacetConfigFlat {
    final FacetsConfig config = new FacetsConfig();

    FacetConfigFlat(String dim, FacetsConfig.DrillDownTermsIndexing ddti) {
      if (ddti != null) {
        config.setDrillDownTermsIndexing(dim, ddti);
      }
    }

    FacetConfigFlat hierarchical(String dim) {
      config.setHierarchical(dim, true);
      return this;
    }
  }

  // ---------------------------------------------------------------- controls (expected to pass)

  public void testControlFlatDefaultIndexing() throws Exception {
    assertValidUpperBound(
        "flat, default (ALL)", runTaxonomy(config("f", null).config, i -> flat(i), "f"));
  }

  public void testControlFlatFullPathOnly() throws Exception {
    assertValidUpperBound(
        "flat, FULL_PATH_ONLY",
        runTaxonomy(
            config("f", FacetsConfig.DrillDownTermsIndexing.FULL_PATH_ONLY).config,
            i -> flat(i),
            "f"));
  }

  public void testControlHierarchicalAllPathsNoDim() throws Exception {
    assertValidUpperBound(
        "hierarchical, ALL_PATHS_NO_DIM",
        runTaxonomy(
            config("h", FacetsConfig.DrillDownTermsIndexing.ALL_PATHS_NO_DIM)
                .hierarchical("h")
                .config,
            i -> hier(i),
            "h"));
  }

  public void testControlHierarchicalFullPathOnlyLeafLevel() throws Exception {
    // children of h/x are leaves "h/x/cN", whose full-path terms ARE indexed
    assertValidUpperBound(
        "hierarchical, FULL_PATH_ONLY, leaf level",
        runTaxonomy(
            config("h", FacetsConfig.DrillDownTermsIndexing.FULL_PATH_ONLY)
                .hierarchical("h")
                .config,
            i -> hier(i),
            "h",
            "x"));
  }

  // ---------------------------------------------------------------- claimed bug (expected to fail)

  public void testFlatNone() throws Exception {
    assertValidUpperBound(
        "flat, NONE",
        runTaxonomy(
            config("f", FacetsConfig.DrillDownTermsIndexing.NONE).config, i -> flat(i), "f"));
  }

  public void testHierarchicalFullPathOnlyNonLeaf() throws Exception {
    assertValidUpperBound(
        "hierarchical, FULL_PATH_ONLY, non-leaf",
        runTaxonomy(
            config("h", FacetsConfig.DrillDownTermsIndexing.FULL_PATH_ONLY)
                .hierarchical("h")
                .config,
            i -> hier(i),
            "h"));
  }

  public void testHierarchicalDimensionAndFullPathNonLeaf() throws Exception {
    assertValidUpperBound(
        "hierarchical, DIMENSION_AND_FULL_PATH, non-leaf",
        runTaxonomy(
            config("h", FacetsConfig.DrillDownTermsIndexing.DIMENSION_AND_FULL_PATH)
                .hierarchical("h")
                .config,
            i -> hier(i),
            "h"));
  }

  public void testHierarchicalFullPathOnlyMixedDepth() throws Exception {
    // half the docs have path m/x (full path, term indexed), half have m/x/z (m/x is an ancestor,
    // term NOT indexed under FULL_PATH_ONLY): docFreq(m/x) = NUM_DOCS/2, true count = NUM_DOCS
    assertValidUpperBound(
        "hierarchical, FULL_PATH_ONLY, mixed depth",
        runTaxonomy(
            config("m", FacetsConfig.DrillDownTermsIndexing.FULL_PATH_ONLY)
                .hierarchical("m")
                .config,
            i ->
                new FacetField[] {
                  (i % 2 == 0) ? new FacetField("m", "x") : new FacetField("m", "x", "z")
                },
            "m"));
  }

  public void testSortedSetFlatNone() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setDrillDownTermsIndexing("s", FacetsConfig.DrillDownTermsIndexing.NONE);
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig());
    for (int i = 0; i < NUM_DOCS; i++) {
      Document doc = new Document();
      doc.add(new SortedSetDocValuesFacetField("s", "v" + (i % 4)));
      doc.add(new org.apache.lucene.document.StringField("id", "" + i, Field.Store.NO));
      writer.addDocument(config.build(doc));
    }
    writer.commit();
    DirectoryReader reader = DirectoryReader.open(writer);
    try {
      IndexSearcher searcher = new IndexSearcher(reader);
      SortedSetDocValuesReaderState state =
          new DefaultSortedSetDocValuesReaderState(reader, config);
      assertValidUpperBound(
          "sorted-set, flat, NONE",
          amortizeAndCompare(
              searcher, config, fc -> new SortedSetDocValuesFacetCounts(state, fc), "s"));
    } finally {
      IOUtils.close(reader, writer, dir);
    }
  }
}
