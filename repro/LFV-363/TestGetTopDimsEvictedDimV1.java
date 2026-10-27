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
package org.apache.lucene.facet.taxonomy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check of the Facets#getTopDims contract for taxonomy facets: "Results should be the
 * same as calling getAllDims and then only using the first topNDims."
 *
 * <p>Only public API is used. Every dimension in every test has at least one hit, so dimensions
 * without hits play no part.
 *
 * <p>Taxonomy dimensions are visited youngest first (children of the root, then siblings), so a
 * dimension created earlier is visited later. The failing tests make an earlier-created dimension
 * win against a later-created one that already sits in the top-dims queue; the controls do not.
 */
public class TestGetTopDimsEvictedDimV1 extends LuceneTestCase {

  /** One document: pairs of (dim, label). */
  private static String[] doc(String... dimLabelPairs) {
    return dimLabelPairs;
  }

  private interface FacetsFactory {
    Facets create(TaxonomyReader taxoReader, FacetsConfig config, FacetsCollector fc)
        throws IOException;
  }

  private interface FieldFactory {
    void add(Document d, String dim, String label);
  }

  /** Strict rendering: FacetResult#equals ignores dim and path, toString does not. */
  private static List<String> render(List<FacetResult> results) {
    List<String> out = new ArrayList<>();
    for (FacetResult r : results) {
      out.add(r == null ? "null" : r.toString());
    }
    return out;
  }

  /**
   * Indexes docs in the given order (taxonomy ordinals are assigned in that order), then returns
   * {getTopDims(topNDims, 10), getAllDims(10), getTopChildren(10, dim) for every dim of getAllDims}
   * rendered as strings.
   */
  private List<List<String>> run(
      FacetsConfig config,
      FieldFactory fields,
      FacetsFactory factory,
      int topNDims,
      String[]... docs)
      throws IOException {
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
      RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
      for (String[] pairs : docs) {
        Document d = new Document();
        d.add(new StringField("match", "yes", Field.Store.NO));
        for (int i = 0; i < pairs.length; i += 2) {
          fields.add(d, pairs[i], pairs[i + 1]);
        }
        writer.addDocument(config.build(taxoWriter, d));
      }
      taxoWriter.close();
      IndexReader reader = writer.getReader();
      writer.close();
      try (TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir)) {
        IndexSearcher searcher = newSearcher(reader);
        FacetsCollector fc =
            searcher.search(new TermQuery(new Term("match", "yes")), new FacetsCollectorManager());
        Facets facets = factory.create(taxoReader, config, fc);
        List<FacetResult> all = facets.getAllDims(10);
        List<FacetResult> top = facets.getTopDims(topNDims, 10);
        List<FacetResult> children = new ArrayList<>();
        for (FacetResult r : all) {
          children.add(facets.getTopChildren(10, r.dim));
        }
        List<List<String>> res = new ArrayList<>();
        res.add(render(top));
        res.add(render(all));
        res.add(render(children));
        return res;
      } finally {
        reader.close();
      }
    }
  }

  private void check(
      String label,
      FacetsConfig config,
      FieldFactory fields,
      FacetsFactory factory,
      int topNDims,
      int expectedDims,
      String[]... docs)
      throws IOException {
    List<List<String>> res = run(config, fields, factory, topNDims, docs);
    List<String> top = res.get(0);
    List<String> all = res.get(1);
    List<String> children = res.get(2);
    if (VERBOSE) {
      System.out.println(label + " getTopDims=" + top + "\ngetAllDims=" + all);
    }
    // Sanity on the oracle: getAllDims sees every dim and agrees with getTopChildren per dim.
    assertEquals(label + ": number of dims with hits", expectedDims, all.size());
    assertEquals(label + ": getAllDims vs getTopChildren", all, children);
    // The contract under test:
    assertEquals(
        label + ": getTopDims(" + topNDims + ", 10) must equal getAllDims(10).subList(0, " + topNDims
            + ")",
        all.subList(0, Math.min(topNDims, all.size())),
        top);
  }

  private static final FieldFactory COUNT_FIELDS = (d, dim, label) -> d.add(new FacetField(dim, label));
  private static final FacetsFactory COUNTS = FastTaxonomyFacetCounts::new;

  private static FacetsConfig multiValued(boolean requireDimCount, String... dims) {
    FacetsConfig config = new FacetsConfig();
    for (String dim : dims) {
      config.setMultiValued(dim, true);
      config.setRequireDimCount(dim, requireDimCount);
    }
    return config;
  }

  /**
   * x is created first (visited last) and has more hits than y. Expected: [x: x1, x2]. On the
   * suspected bug: x's slot keeps y's ordinal, so y's children come back under x's name.
   */
  public void testRequireDimCountLaterVisitedHigherCount() throws Exception {
    check(
        "requireDimCount x>y",
        multiValued(true, "x", "y"),
        COUNT_FIELDS,
        COUNTS,
        1,
        2,
        doc("x", "x1"),
        doc("x", "x2"),
        doc("y", "y1"));
  }

  /** topNDims=2 of 3 dims: the evicted slot is not the only one, and the order is a, b. */
  public void testRequireDimCountTopTwoOfThree() throws Exception {
    check(
        "requireDimCount a>b>c top2",
        multiValued(true, "a", "b", "c"),
        COUNT_FIELDS,
        COUNTS,
        2,
        3,
        doc("a", "a1"),
        doc("a", "a2"),
        doc("a", "a3"),
        doc("b", "b1"),
        doc("b", "b2"),
        doc("c", "c1"));
  }

  /**
   * Multi-valued without dim counts: every dim value is -1, ties broken by dim name. a is created
   * first (visited last) and wins on name.
   */
  public void testNoDimCountNameTieBreak() throws Exception {
    check(
        "noDimCount a vs b",
        multiValued(false, "a", "b"),
        COUNT_FIELDS,
        COUNTS,
        1,
        2,
        doc("a", "a1"),
        doc("b", "b1"),
        doc("b", "b2"));
  }

  /** Same as testNoDimCountNameTieBreak through TaxonomyFacetIntAssociations (SUM). */
  public void testIntAssociationsMultiValued() throws Exception {
    FacetsConfig config = new FacetsConfig();
    for (String dim : new String[] {"a", "b"}) {
      config.setMultiValued(dim, true);
      config.setIndexFieldName(dim, "$facets.int");
    }
    FieldFactory assoc =
        (d, dim, label) -> d.add(new IntAssociationFacetField(dim.equals("a") ? 5 : 7, dim, label));
    FacetsFactory factory =
        (tr, cfg, fc) ->
            new TaxonomyFacetIntAssociations(
                "$facets.int", tr, cfg, fc, AssociationAggregationFunction.SUM);
    check("intAssoc a vs b", config, assoc, factory, 1, 2, doc("a", "a1"), doc("b", "b1"));
  }

  // ---------------- controls (expected to pass on any version) ----------------

  /** Control: same docs, y created first, so the winner x is visited first and nothing is evicted. */
  public void testControlWinnerVisitedFirst() throws Exception {
    check(
        "control y created first",
        multiValued(true, "x", "y"),
        COUNT_FIELDS,
        COUNTS,
        1,
        2,
        doc("y", "y1"),
        doc("x", "x1"),
        doc("x", "x2"));
  }

  /** Control: single-valued dims take the per-name intermediate-results path. */
  public void testControlSingleValued() throws Exception {
    check(
        "control single-valued",
        new FacetsConfig(),
        COUNT_FIELDS,
        COUNTS,
        1,
        2,
        doc("x", "x1"),
        doc("x", "x2"),
        doc("y", "y1"));
  }

  /** Control: topNDims covers every dim, so the queue never evicts. */
  public void testControlTopNDimsCoversAll() throws Exception {
    check(
        "control topN=all",
        multiValued(true, "x", "y"),
        COUNT_FIELDS,
        COUNTS,
        2,
        2,
        doc("x", "x1"),
        doc("x", "x2"),
        doc("y", "y1"));
  }
}
