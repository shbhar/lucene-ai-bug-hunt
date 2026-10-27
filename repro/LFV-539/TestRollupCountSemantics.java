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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
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
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * Intended-behaviour probes: what LabelAndValue#count does for the value-source association path
 * on (a) a single-valued hierarchical dim (rolled up at search time), (b) the same paths on a
 * multi-valued hierarchical dim (ancestors indexed, no search-time roll-up), (c) a flat dim, and
 * what FastTaxonomyFacetCounts reports for the same hits. Each probe asserts the doc count.
 */
public class TestRollupCountSemantics extends LuceneTestCase {

  /** Returns {assocCount(label under path), fastCount(label under path), assocValue*1000}. */
  private int[] run(boolean multiValued, String[][] docs, long[] weights, String label, String... path)
      throws Exception {
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir);
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("d", true);
    config.setMultiValued("d", multiValued);
    for (int i = 0; i < docs.length; i++) {
      Document doc = new Document();
      doc.add(new FacetField("d", docs[i]));
      doc.add(new NumericDocValuesField("w", weights[i]));
      w.addDocument(config.build(tw, doc));
    }
    DirectoryReader r = DirectoryReader.open(w);
    TaxonomyReader tr = new DirectoryTaxonomyReader(tw);
    try {
      IndexSearcher searcher = newSearcher(r);
      FacetsCollector fc = searcher.search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
      Facets counts = new FastTaxonomyFacetCounts(tr, config, fc);
      Facets sums =
          new TaxonomyFacetFloatAssociations(
              tr, config, fc, AssociationAggregationFunction.SUM, DoubleValuesSource.fromLongField("w"));
      FacetResult cr = counts.getTopChildren(10, "d", path);
      FacetResult sr = sums.getTopChildren(10, "d", path);
      int ac = -1, fcnt = -1, av = -1;
      for (var lv : sr.labelValues) {
        if (lv.label.equals(label)) {
          ac = lv.count;
          av = Math.round(lv.value.floatValue() * 1000);
        }
      }
      for (var lv : cr.labelValues) {
        if (lv.label.equals(label)) {
          fcnt = lv.count;
        }
      }
      System.out.println(
          "PROBE multiValued="
              + multiValued
              + " path="
              + String.join("/", path)
              + " label="
              + label
              + " assocCount="
              + ac
              + " fastCount="
              + fcnt
              + " assocValue="
              + (av / 1000f));
      return new int[] {ac, fcnt, av};
    } finally {
      IOUtils.close(r, tr, w, tw, dir, taxoDir);
    }
  }

  private static final String[][] TWO = {{"a", "x"}, {"a", "y"}};
  private static final long[] TWO_W = {7, 2};
  private static final String[][] CHAIN = {{"a", "b", "x"}};
  private static final long[] CHAIN_W = {5};

  public void testSingleValuedTwoLeaves() throws Exception {
    int[] r = run(false, TWO, TWO_W, "a");
    assertEquals("fast count", 2, r[1]);
    assertEquals("assoc value", 9000, r[2]);
    assertEquals("assoc count (single-valued, rolled up)", 2, r[0]);
  }

  public void testMultiValuedTwoLeavesControl() throws Exception {
    int[] r = run(true, TWO, TWO_W, "a");
    assertEquals("fast count", 2, r[1]);
    assertEquals("assoc value", 9000, r[2]);
    assertEquals("assoc count (multi-valued, ancestors indexed)", 2, r[0]);
  }

  public void testSingleValuedChainOneDocDepth1() throws Exception {
    int[] r = run(false, CHAIN, CHAIN_W, "a");
    assertEquals("fast count", 1, r[1]);
    assertEquals("assoc value", 5000, r[2]);
    assertEquals("assoc count of d/a for ONE doc", 1, r[0]);
  }

  public void testSingleValuedChainOneDocDepth2() throws Exception {
    int[] r = run(false, CHAIN, CHAIN_W, "b", "a");
    assertEquals("fast count", 1, r[1]);
    assertEquals("assoc value", 5000, r[2]);
    assertEquals("assoc count of d/a/b for ONE doc", 1, r[0]);
  }

  public void testSingleValuedLeafControl() throws Exception {
    int[] r = run(false, TWO, TWO_W, "x", "a");
    assertEquals("fast count", 1, r[1]);
    assertEquals("assoc count of leaf d/a/x", 1, r[0]);
  }
}
