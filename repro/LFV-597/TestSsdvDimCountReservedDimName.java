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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check (public API only): a flat SortedSetDocValues facet dimension configured with
 * setMultiValued(dim, true) and setRequireDimCount(dim, true) whose name contains one of the
 * FacetsConfig reserved characters U+001F (delimiter) or U+001E (escape).
 *
 * <p>Three docs with labels {x, y}, {x}, {z}. Expected (independent of SSDV): dim count 3 (docs
 * having the dimension; checked by drill-down), x=2, y=1, z=1, and one dimension in getAllDims.
 * Taxonomy facets over the same docs and config serve as a second oracle.
 */
public class TestSsdvDimCountReservedDimName extends LuceneTestCase {

  private static final String[][] DOCS = {{"x", "y"}, {"x"}, {"z"}};
  private static final String EXPECTED = "value=3 childCount=3 [x=2, y=1, z=1]";

  // ---- controls: expected to pass ----

  public void testControlPlainNameDimCount() throws Exception {
    check("color", true);
  }

  public void testControlReservedNameWithoutDimCount() throws Exception {
    // Same reserved name, but no requireDimCount: the bare dimension term is not indexed.
    for (String dim : new String[] {"a\u001Fb", "a\u001Eb", "a\u001E"}) {
      FacetsConfig config = new FacetsConfig();
      config.setMultiValued(dim, true);
      try (Directory dir = newDirectory()) {
        indexSsdv(dir, config, dim);
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(r, config);
          Facets f = new SortedSetDocValuesFacetCounts(state);
          FacetResult res = f.getTopChildren(10, dim);
          assertNotNull("dim " + esc(dim) + " missing", res);
          assertEquals("childCount=3 [x=2, y=1, z=1]", "childCount=" + res.childCount + " " + labels(res));
          assertEquals("getAllDims for " + esc(dim), "[" + esc(dim) + "]", dims(f.getAllDims(10)));
        }
      }
    }
  }

  // ---- the claim ----

  public void testDimNameWithDelimiter() throws Exception {
    check("a\u001Fb", true);
  }

  public void testDimNameWithEscape() throws Exception {
    check("a\u001Eb", true);
  }

  public void testDimNameEndingWithEscape() throws Exception {
    check("a\u001E", true);
  }

  // ---- helpers ----

  private void check(String dim, boolean requireDimCount) throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setMultiValued(dim, true);
    config.setRequireDimCount(dim, requireDimCount);

    // Oracle 1: taxonomy facets over the same docs/config.
    String taxo;
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
          DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
        for (String[] labels : DOCS) {
          Document doc = new Document();
          for (String l : labels) {
            doc.add(new FacetField(dim, l));
          }
          w.addDocument(config.build(tw, doc));
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir);
          TaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
        Facets f = new FastTaxonomyFacetCounts(FacetsConfig.DEFAULT_INDEX_FIELD_NAME, r, tr, config);
        taxo = describe(f.getTopChildren(10, dim));
      }
    }
    assertEquals("taxonomy oracle for " + esc(dim), EXPECTED, taxo);

    try (Directory dir = newDirectory()) {
      indexSsdv(dir, config, dim);
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        // Oracle 2: drill-down on the dimension (terms written by pathToString) counts 3 docs.
        DrillDownQuery ddq = new DrillDownQuery(config);
        ddq.add(dim);
        assertEquals("drill-down count for " + esc(dim), 3, new IndexSearcher(r).count(ddq));

        SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(r, config);
        Facets f = new SortedSetDocValuesFacetCounts(state);
        FacetResult res = f.getTopChildren(10, dim);
        String ssdv = describe(res);
        String all = dims(f.getAllDims(10));
        System.out.println(
            "dim=" + esc(dim) + " taxonomy: " + taxo + " | ssdv: " + ssdv + " | ssdv getAllDims: " + all);
        assertEquals("SSDV getTopChildren for " + esc(dim), EXPECTED, ssdv);
        assertEquals("SSDV getAllDims for " + esc(dim), "[" + esc(dim) + "]", all);
      }
    }
  }

  private static void indexSsdv(Directory dir, FacetsConfig config, String dim) throws Exception {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String[] labels : DOCS) {
        Document doc = new Document();
        for (String l : labels) {
          doc.add(new SortedSetDocValuesFacetField(dim, l));
        }
        w.addDocument(config.build(doc));
      }
    }
  }

  private static String describe(FacetResult res) {
    if (res == null) {
      return "null";
    }
    return "value=" + res.value + " childCount=" + res.childCount + " " + labels(res);
  }

  private static String labels(FacetResult res) {
    List<String> out = new ArrayList<>();
    for (LabelAndValue lv : res.labelValues) {
      out.add(esc(lv.label) + "=" + lv.value);
    }
    out.sort(null);
    return out.toString();
  }

  private static String dims(List<FacetResult> results) {
    List<String> out = new ArrayList<>();
    for (FacetResult res : results) {
      out.add(esc(res.dim));
    }
    out.sort(null);
    return out.toString();
  }

  private static String esc(String s) {
    return s.replace("\u001F", "<1F>").replace("\u001E", "<1E>");
  }
}
