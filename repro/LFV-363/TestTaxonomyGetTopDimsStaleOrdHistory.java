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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Version-portable history test (compiles against 9.2.0 .. main) for the stale-dimOrd bug in
 * taxonomy Facets#getTopDims. Contract (Facets#getTopDims javadoc): "Results should be the same as
 * calling getAllDims and then only using the first topNDims."
 *
 * <p>Taxonomy dims are visited youngest first, so a dim created earlier is visited later. Each
 * "evict" case makes an earlier-created dim win against a later-created dim already in the
 * size-1 queue. Results are compared by toString, because FacetResult#equals ignores dim and path.
 *
 * <p>Cases are split by the code path that decides whether the winner's children are looked up by
 * name (correct) or recomputed from the queue entry's dimOrd (stale after eviction):
 *
 * <ul>
 *   <li>Int path (IntTaxonomyFacets in 9.2-9.10, TaxonomyFacets later): FastTaxonomyFacetCounts.
 *   <li>Float path (FloatTaxonomyFacets in 9.2-9.10, TaxonomyFacets later):
 *       TaxonomyFacetFloatAssociations, with a value source or with float associations.
 * </ul>
 */
public class TestTaxonomyGetTopDimsStaleOrdHistory extends LuceneTestCase {

  private interface FacetsFactory {
    Facets create(TaxonomyReader taxoReader, FacetsConfig config, FacetsCollector fc)
        throws IOException;
  }

  private interface FieldFactory {
    void add(Document d, String dim, String label);
  }

  private static final FieldFactory COUNT_FIELDS =
      (d, dim, label) -> d.add(new FacetField(dim, label));

  private static final FacetsFactory COUNTS = FastTaxonomyFacetCounts::new;

  /** Float path over the default "$facets" field: every hit contributes 1.0. */
  private static final FacetsFactory VALUE_SOURCE =
      (tr, cfg, fc) ->
          new TaxonomyFacetFloatAssociations(
              tr, cfg, fc, AssociationAggregationFunction.SUM, DoubleValuesSource.constant(1.0));

  private static List<String> render(List<FacetResult> results) {
    List<String> out = new ArrayList<>();
    for (FacetResult r : results) {
      out.add(r == null ? "null" : r.toString());
    }
    return out;
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
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
      IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig());
      for (String[] pairs : docs) {
        Document d = new Document();
        d.add(new StringField("match", "yes", Field.Store.NO));
        for (int i = 0; i < pairs.length; i += 2) {
          fields.add(d, pairs[i], pairs[i + 1]);
        }
        writer.addDocument(config.build(taxoWriter, d));
      }
      taxoWriter.close();
      writer.close();
      try (DirectoryReader reader = DirectoryReader.open(dir);
          TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        FacetsCollector fc =
            searcher.search(new TermQuery(new Term("match", "yes")), new FacetsCollectorManager());
        Facets facets = factory.create(taxoReader, config, fc);
        List<String> all = render(facets.getAllDims(10));
        List<String> top = render(facets.getTopDims(topNDims, 10));
        List<FacetResult> childrenResults = new ArrayList<>();
        for (FacetResult r : facets.getAllDims(10)) {
          childrenResults.add(facets.getTopChildren(10, r.dim));
        }
        List<String> children = render(childrenResults);
        System.out.println("[" + label + "] getTopDims=" + top + " getAllDims=" + all);
        // Oracle sanity: getAllDims sees every dim and agrees with getTopChildren per dim.
        assertEquals(label + ": number of dims with hits", expectedDims, all.size());
        assertEquals(label + ": getAllDims vs getTopChildren", all, children);
        // The contract under test.
        assertEquals(
            label
                + ": getTopDims("
                + topNDims
                + ", 10) must equal getAllDims(10).subList(0, "
                + topNDims
                + ")",
            all.subList(0, Math.min(topNDims, all.size())),
            top);
      }
    }
  }

  private static String[] doc(String... dimLabelPairs) {
    return dimLabelPairs;
  }

  private static FacetsConfig multiValued(boolean requireDimCount, String... dims) {
    FacetsConfig config = new FacetsConfig();
    for (String dim : dims) {
      config.setMultiValued(dim, true);
      config.setRequireDimCount(dim, requireDimCount);
    }
    return config;
  }

  private static FacetsConfig hierarchical(String... dims) {
    FacetsConfig config = new FacetsConfig();
    for (String dim : dims) {
      config.setHierarchical(dim, true);
    }
    return config;
  }

  // ---------------- Int path ----------------

  /** Multi-valued + requireDimCount, x (created first, visited last) has more hits than y. */
  public void testIntMultiValuedRequireDimCount() throws Exception {
    check(
        "int mv+requireDimCount",
        multiValued(true, "x", "y"),
        COUNT_FIELDS,
        COUNTS,
        1,
        2,
        doc("x", "x1"),
        doc("x", "x2"),
        doc("y", "y1"));
  }

  /** Multi-valued without dim counts: both dims are -1, a wins on name and is visited last. */
  public void testIntMultiValuedNoDimCount() throws Exception {
    check(
        "int mv-noDimCount",
        multiValued(false, "a", "b"),
        COUNT_FIELDS,
        COUNTS,
        1,
        2,
        doc("a", "a1"),
        doc("b", "b1"),
        doc("b", "b2"));
  }

  /** Hierarchical single-valued dims, x (created first, visited last) has more hits than y. */
  public void testIntHierarchicalSingleValued() throws Exception {
    check(
        "int hierarchical",
        hierarchical("x", "y"),
        COUNT_FIELDS,
        COUNTS,
        1,
        2,
        doc("x", "x1"),
        doc("x", "x2"),
        doc("y", "y1"));
  }

  // ---------------- Float path ----------------

  /** Value source (float path), multi-valued + requireDimCount, x beats y. */
  public void testFloatValueSourceMultiValuedRequireDimCount() throws Exception {
    check(
        "float valueSource mv+requireDimCount",
        multiValued(true, "x", "y"),
        COUNT_FIELDS,
        VALUE_SOURCE,
        1,
        2,
        doc("x", "x1"),
        doc("x", "x2"),
        doc("y", "y1"));
  }

  /** Value source (float path), hierarchical single-valued, x beats y. */
  public void testFloatValueSourceHierarchical() throws Exception {
    check(
        "float valueSource hierarchical",
        hierarchical("x", "y"),
        COUNT_FIELDS,
        VALUE_SOURCE,
        1,
        2,
        doc("x", "x1"),
        doc("x", "x2"),
        doc("y", "y1"));
  }

  /** Float associations, multi-valued without dim counts: -1 tie, a wins on name. */
  public void testFloatAssociationsMultiValuedNoDimCount() throws Exception {
    FacetsConfig config = new FacetsConfig();
    for (String dim : new String[] {"a", "b"}) {
      config.setMultiValued(dim, true);
      config.setIndexFieldName(dim, "$facets.float");
    }
    FieldFactory assoc =
        (d, dim, label) ->
            d.add(new FloatAssociationFacetField(dim.equals("a") ? 5f : 7f, dim, label));
    FacetsFactory factory =
        (tr, cfg, fc) ->
            new TaxonomyFacetFloatAssociations(
                "$facets.float", tr, cfg, fc, AssociationAggregationFunction.SUM);
    check(
        "float assoc mv-noDimCount", config, assoc, factory, 1, 2, doc("a", "a1"), doc("b", "b1"));
  }

  // ---------------- controls (expected to pass on every version) ----------------

  /** Control: flat single-valued dims are always looked up by name. */
  public void testControlSingleValuedFlat() throws Exception {
    check(
        "control flat single-valued",
        new FacetsConfig(),
        COUNT_FIELDS,
        COUNTS,
        1,
        2,
        doc("x", "x1"),
        doc("x", "x2"),
        doc("y", "y1"));
  }

  /** Control: winner created last (visited first), so nothing is evicted. */
  public void testControlWinnerVisitedFirst() throws Exception {
    check(
        "control winner visited first",
        multiValued(true, "x", "y"),
        COUNT_FIELDS,
        COUNTS,
        1,
        2,
        doc("y", "y1"),
        doc("x", "x1"),
        doc("x", "x2"));
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
