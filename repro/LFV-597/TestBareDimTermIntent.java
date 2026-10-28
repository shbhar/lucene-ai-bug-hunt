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
package org.apache.lucene.facet.sortedset;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Intended-behaviour probe. Questions: does the facet module otherwise support dimension
 * names that contain U+001F/U+001E (so the flat multiValued+requireDimCount path is the odd one
 * out), is the trigger reachable through the documented "dimension not known beforehand" route
 * (getDefaultDimConfig), and what exactly does FacetsConfig write for the bare dimension term in
 * each configuration.
 */
public class TestBareDimTermIntent extends LuceneTestCase {

  static final String DIM = "size\u001Fcm"; // U+001F inside the dimension name
  static final String[][] DOCS = {{"x", "y"}, {"x"}, {"z"}};
  static final String EXPECTED_DIMCOUNT = "value=3 childCount=3 x=2 y=1 z=1";
  static final String EXPECTED_NO_DIMCOUNT = "value=-1 childCount=3 x=2 y=1 z=1";

  private static String counts(FacetResult r) {
    if (r == null) {
      return "null";
    }
    StringBuilder sb = new StringBuilder("value=" + r.value + " childCount=" + r.childCount);
    for (LabelAndValue lv : r.labelValues) {
      sb.append(' ').append(lv.label).append('=').append(lv.value);
    }
    return sb.toString();
  }

  private static String esc(String s) {
    return s.replace("\u001F", "<US>").replace("\u001E", "<RS>");
  }

  private interface Body {
    void run(DirectoryReader reader, Facets facets) throws Exception;
  }

  private void withSsdv(FacetsConfig config, String dim, Body body) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String[] labels : DOCS) {
          Document doc = new Document();
          for (String label : labels) {
            doc.add(new SortedSetDocValuesFacetField(dim, label));
          }
          w.addDocument(config.build(doc));
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        FacetsCollector fc = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        SortedSetDocValuesReaderState state =
            new DefaultSortedSetDocValuesReaderState(reader, config);
        body.run(reader, new SortedSetDocValuesFacetCounts(state, fc));
      }
    }
  }

  /** All terms of the $facets SSDV field, in ord order, reserved chars made visible. */
  private static List<String> ssdvTerms(DirectoryReader reader) throws Exception {
    LeafReader leaf = reader.leaves().get(0).reader();
    SortedSetDocValues dv = leaf.getSortedSetDocValues(FacetsConfig.DEFAULT_INDEX_FIELD_NAME);
    List<String> out = new ArrayList<>();
    for (long ord = 0; ord < dv.getValueCount(); ord++) {
      out.add(esc(dv.lookupOrd(ord).utf8ToString()));
    }
    return out;
  }

  /**
   * Control: a hierarchical dimension also indexes a bare dimension term, but through
   * pathToString; the same reserved-char dimension name then counts correctly.
   */
  public void testHierarchicalDimCountWithReservedDimControl() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical(DIM, true);
    config.setMultiValued(DIM, true);
    config.setRequireDimCount(DIM, true);
    withSsdv(
        config,
        DIM,
        (reader, facets) -> {
          List<String> terms = ssdvTerms(reader);
          assertTrue(
              "hierarchical bare dim term is escaped: " + terms,
              terms.contains(esc(FacetsConfig.pathToString(new String[] {DIM}))));
          assertEquals(EXPECTED_DIMCOUNT, counts(facets.getTopChildren(10, DIM)));
          assertEquals(1, facets.getAllDims(10).size());
        });
  }

  /** Control: flat multiValued without requireDimCount (no bare dim term) is correct. */
  public void testFlatMultiNoDimCountWithReservedDimControl() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setMultiValued(DIM, true);
    withSsdv(
        config,
        DIM,
        (reader, facets) -> {
          assertEquals(EXPECTED_NO_DIMCOUNT, counts(facets.getTopChildren(10, DIM)));
          assertEquals(1, facets.getAllDims(10).size());
        });
  }

  /** Control: flat multiValued + requireDimCount with a plain dimension name is correct. */
  public void testFlatDimCountPlainDimControl() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setMultiValued("size", true);
    config.setRequireDimCount("size", true);
    withSsdv(
        config,
        "size",
        (reader, facets) -> {
          assertEquals(EXPECTED_DIMCOUNT, counts(facets.getTopChildren(10, "size")));
          assertEquals(1, facets.getAllDims(10).size());
        });
  }

  /** The defect: the flat multiValued + requireDimCount bare dim term is not pathToString. */
  public void testFlatDimCountBareTermIsEscaped() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setMultiValued(DIM, true);
    config.setRequireDimCount(DIM, true);
    withSsdv(
        config,
        DIM,
        (reader, facets) -> {
          List<String> terms = ssdvTerms(reader);
          assertTrue(
              "flat requireDimCount bare dim term should be pathToString(dim); terms=" + terms,
              terms.contains(esc(FacetsConfig.pathToString(new String[] {DIM}))));
        });
  }

  /** The defect, end to end. */
  public void testFlatDimCountWithReservedDim() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setMultiValued(DIM, true);
    config.setRequireDimCount(DIM, true);
    withSsdv(
        config,
        DIM,
        (reader, facets) -> {
          List<String> dims = new ArrayList<>();
          for (FacetResult r : facets.getAllDims(10)) {
            dims.add(esc(r.dim) + "{" + counts(r) + "}");
          }
          assertEquals(
              "terms=" + ssdvTerms(reader) + " allDims=" + dims,
              EXPECTED_DIMCOUNT,
              counts(facets.getTopChildren(10, DIM)));
          assertEquals("allDims=" + dims, 1, dims.size());
        });
  }

  /**
   * Reachability through the documented route for dimensions "not known beforehand": a
   * FacetsConfig subclass whose default DimConfig is multiValued + requireDimCount, with dimension
   * names that come from the data.
   */
  public void testDataDrivenDimsViaDefaultDimConfig() throws Exception {
    FacetsConfig.DimConfig dflt = new FacetsConfig.DimConfig();
    dflt.multiValued = true;
    dflt.requireDimCount = true;
    FacetsConfig config =
        new FacetsConfig() {
          @Override
          protected DimConfig getDefaultDimConfig() {
            return dflt;
          }
        };
    assertFalse(config.isDimConfigured(DIM));
    withSsdv(
        config,
        DIM,
        (reader, facets) -> {
          assertEquals(EXPECTED_DIMCOUNT, counts(facets.getTopChildren(10, DIM)));
        });
  }

  /** Oracle: taxonomy faceting, same dimension name and config. */
  public void testTaxonomyOracle() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setMultiValued(DIM, true);
    config.setRequireDimCount(DIM, true);
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
          DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
        for (String[] labels : DOCS) {
          Document doc = new Document();
          for (String label : labels) {
            doc.add(new FacetField(DIM, label));
          }
          w.addDocument(config.build(tw, doc));
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir);
          TaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        FacetsCollector fc = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        Facets facets = new FastTaxonomyFacetCounts(tr, config, fc);
        assertEquals(EXPECTED_DIMCOUNT, counts(facets.getTopChildren(10, DIM)));
        assertEquals(1, facets.getAllDims(10).size());
      }
    }
  }
}
