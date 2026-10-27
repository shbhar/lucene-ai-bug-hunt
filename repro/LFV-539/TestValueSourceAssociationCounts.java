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
package org.apache.lucene.facet.v1check;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.Field;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.taxonomy.AssociationAggregationFunction;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.TaxonomyFacetFloatAssociations;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check (public API only): for a hierarchical, single-valued dimension, the count of
 * each {@link LabelAndValue} returned by {@link TaxonomyFacetFloatAssociations} with a {@link
 * DoubleValuesSource} must be the number of matching documents under that label ("Number of
 * occurrences for this label"), i.e. the same count {@link FastTaxonomyFacetCounts} returns for
 * the same hits.
 */
public class TestValueSourceAssociationCounts extends LuceneTestCase {

  private static final String DIM = "cat";

  private static final class Fixture implements AutoCloseable {
    final Directory indexDir;
    final Directory taxoDir;
    final DirectoryReader reader;
    final TaxonomyReader taxoReader;
    final FacetsConfig config;

    Fixture(Directory indexDir, Directory taxoDir, DirectoryReader reader, TaxonomyReader taxoReader,
        FacetsConfig config) {
      this.indexDir = indexDir;
      this.taxoDir = taxoDir;
      this.reader = reader;
      this.taxoReader = taxoReader;
      this.config = config;
    }

    @Override
    public void close() throws Exception {
      reader.close();
      taxoReader.close();
      indexDir.close();
      taxoDir.close();
    }
  }

  /** Each doc: a path under DIM and a long weight. */
  private Fixture build(List<String[]> paths, List<Long> weights, int extraFlatLabels)
      throws Exception {
    Directory indexDir = newDirectory();
    Directory taxoDir = newDirectory();
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical(DIM, true); // single-valued (default) hierarchical dim
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    IndexWriter writer = new IndexWriter(indexDir, new IndexWriterConfig());
    for (int i = 0; i < paths.size(); i++) {
      Document doc = new Document();
      doc.add(new FacetField(DIM, paths.get(i)));
      doc.add(new NumericDocValuesField("w", weights.get(i)));
      doc.add(new StringField("q", "hit", Field.Store.NO));
      writer.addDocument(config.build(taxoWriter, doc));
    }
    // Optional padding docs in a different, flat dim (used to force the sparse counting path):
    for (int i = 0; i < extraFlatLabels; i++) {
      Document doc = new Document();
      doc.add(new FacetField("pad", "p" + i));
      doc.add(new NumericDocValuesField("w", 1));
      doc.add(new StringField("q", "miss", Field.Store.NO));
      writer.addDocument(config.build(taxoWriter, doc));
    }
    writer.close();
    taxoWriter.close();
    DirectoryReader reader = DirectoryReader.open(indexDir);
    TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir);
    return new Fixture(indexDir, taxoDir, reader, taxoReader, config);
  }

  private static LabelAndValue find(FacetResult r, String label) {
    assertNotNull("null FacetResult", r);
    for (LabelAndValue lv : r.labelValues) {
      if (lv.label.equals(label)) {
        return lv;
      }
    }
    fail("label " + label + " missing from " + r);
    return null;
  }

  /** Minimal case: two docs under cat/a (cat/a/x weight 7, cat/a/y weight 2). */
  public void testTwoLeavesUnderOneLabel() throws Exception {
    List<String[]> paths = List.of(new String[] {"a", "x"}, new String[] {"a", "y"});
    try (Fixture f = build(paths, List.of(7L, 2L), 0)) {
      IndexSearcher searcher = new IndexSearcher(f.reader);
      FacetsCollector fc = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());

      Facets counts = new FastTaxonomyFacetCounts(f.taxoReader, f.config, fc);
      Facets sums =
          new TaxonomyFacetFloatAssociations(
              f.taxoReader,
              f.config,
              fc,
              AssociationAggregationFunction.SUM,
              DoubleValuesSource.fromLongField("w"));

      // Reference: FastTaxonomyFacetCounts says 2 docs under cat/a.
      assertEquals(2, find(counts.getTopChildren(10, DIM), "a").count);

      LabelAndValue top = find(sums.getTopChildren(10, DIM), "a");
      assertEquals("value of cat/a (sum of weights)", 9.0f, top.value.floatValue(), 0f);
      LabelAndValue all = find(sums.getAllChildren(DIM), "a");
      assertEquals("value of cat/a via getAllChildren", 9.0f, all.value.floatValue(), 0f);

      // Leaf counts (no roll-up below them):
      assertEquals(1, find(sums.getTopChildren(10, DIM, "a"), "x").count);
      assertEquals(1, find(sums.getTopChildren(10, DIM, "a"), "y").count);

      assertEquals("getTopChildren count of cat/a", 2, top.count);
      assertEquals("getAllChildren count of cat/a", 2, all.count);
    }
  }

  /** One doc at depth 3: cat/a/b/x. Every label on the path has exactly one occurrence. */
  public void testSingleDocDepthThree() throws Exception {
    List<String[]> paths = List.<String[]>of(new String[] {"a", "b", "x"});
    try (Fixture f = build(paths, List.of(5L), 0)) {
      IndexSearcher searcher = new IndexSearcher(f.reader);
      FacetsCollector fc = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
      Facets sums =
          new TaxonomyFacetFloatAssociations(
              f.taxoReader,
              f.config,
              fc,
              AssociationAggregationFunction.SUM,
              DoubleValuesSource.fromLongField("w"));
      LabelAndValue a = find(sums.getTopChildren(10, DIM), "a");
      LabelAndValue b = find(sums.getTopChildren(10, DIM, "a"), "b");
      LabelAndValue x = find(sums.getTopChildren(10, DIM, "a", "b"), "x");
      String msg = "counts a/b/x = " + a.count + "/" + b.count + "/" + x.count;
      assertEquals(msg, 5f, a.value.floatValue(), 0f);
      assertEquals(msg, 5f, b.value.floatValue(), 0f);
      assertEquals(msg, 5f, x.value.floatValue(), 0f);
      assertEquals(msg, 1, x.count);
      assertEquals(msg, 1, b.count);
      assertEquals(msg, 1, a.count);
    }
  }

  /**
   * Randomized differential check (seeded): every label at every level, count compared with a
   * brute-force count and with FastTaxonomyFacetCounts; values with a brute-force sum. With
   * {@code sparse} the taxonomy is padded beyond 1024 ordinals and the query hits < 10% of docs,
   * which selects the sparse (hash map) counting path.
   */
  private void runDifferential(boolean sparse) throws Exception {
    Random r = new Random(random().nextLong());
    String[] alphabet = {"a", "b", "c"};
    int numDocs = 3 + r.nextInt(6);
    List<String[]> paths = new ArrayList<>();
    List<Long> weights = new ArrayList<>();
    for (int i = 0; i < numDocs; i++) {
      int depth = 1 + r.nextInt(3);
      String[] p = new String[depth];
      for (int j = 0; j < depth; j++) {
        p[j] = alphabet[r.nextInt(alphabet.length)];
      }
      paths.add(p);
      weights.add((long) (1 + r.nextInt(9)));
    }
    // brute force: count and sum for every proper prefix (label) of every path
    Map<String, Integer> bfCount = new HashMap<>();
    Map<String, Long> bfSum = new HashMap<>();
    for (int i = 0; i < numDocs; i++) {
      String[] p = paths.get(i);
      StringBuilder key = new StringBuilder();
      for (int j = 0; j < p.length; j++) {
        if (j > 0) key.append('/');
        key.append(p[j]);
        bfCount.merge(key.toString(), 1, Integer::sum);
        bfSum.merge(key.toString(), weights.get(i), Long::sum);
      }
    }
    try (Fixture f = build(paths, weights, sparse ? 30 * numDocs + 1100 : 0)) {
      IndexSearcher searcher = new IndexSearcher(f.reader);
      Query q = sparse ? new TermQuery(new Term("q", "hit")) : new MatchAllDocsQuery();
      FacetsCollector fc = searcher.search(q, new FacetsCollectorManager());
      Facets counts = new FastTaxonomyFacetCounts(f.taxoReader, f.config, fc);
      Facets sums =
          new TaxonomyFacetFloatAssociations(
              f.taxoReader,
              f.config,
              fc,
              AssociationAggregationFunction.SUM,
              DoubleValuesSource.fromLongField("w"));
      List<String> mismatches = new ArrayList<>();
      int checked = 0;
      for (Map.Entry<String, Integer> e : bfCount.entrySet()) {
        String[] full = e.getKey().split("/");
        String[] parent = new String[full.length - 1];
        System.arraycopy(full, 0, parent, 0, parent.length);
        String label = full[full.length - 1];
        LabelAndValue ref = find(counts.getTopChildren(100, DIM, parent), label);
        LabelAndValue got = find(sums.getAllChildren(DIM, parent), label);
        LabelAndValue gotTop = find(sums.getTopChildren(100, DIM, parent), label);
        assertEquals("FastTaxonomyFacetCounts vs brute force at " + e.getKey(), (int) e.getValue(),
            ref.count);
        assertEquals("value at " + e.getKey(), (float) (long) bfSum.get(e.getKey()),
            got.value.floatValue(), 0f);
        checked++;
        if (got.count != e.getValue() || gotTop.count != e.getValue()) {
          mismatches.add(
              e.getKey()
                  + " expected="
                  + e.getValue()
                  + " getAllChildren="
                  + got.count
                  + " getTopChildren="
                  + gotTop.count);
        }
      }
      assertTrue("no labels checked", checked > 0);
      assertTrue(
          "sparse=" + sparse + " paths=" + describe(paths) + " count mismatches: " + mismatches,
          mismatches.isEmpty());
    }
  }

  private static String describe(List<String[]> paths) {
    StringBuilder sb = new StringBuilder("[");
    for (String[] p : paths) {
      sb.append(String.join("/", p)).append(' ');
    }
    return sb.append(']').toString();
  }

  public void testDifferentialDense() throws Exception {
    // a fixed number of trials; with depth >= 2 paths present any trial exposes the bug
    for (int i = 0; i < 20; i++) {
      runDifferential(false);
    }
  }

  public void testDifferentialSparse() throws Exception {
    runDifferential(true);
    runDifferential(true);
    runDifferential(true);
  }
}
