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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SortedSetDocValues faceting builds its per-dimension structures by walking the field's (global)
 * ordinals in term order and assumes that this order lists every dimension as one contiguous run
 * and, for hierarchical dimensions, every path directly before its children. Encoded paths are
 * {@code dim U+001F label U+001F label ...}, so the assumption fails as soon as one component is
 * a proper prefix of a sibling component whose next character sorts below U+001F (U+0000..U+001E,
 * e.g. TAB, or the escape character U+001E that FacetsConfig itself inserts for a label that
 * contains U+001E or U+001F).
 */
public class TestSsdvFacetLabelBelowDelimiter extends LuceneTestCase {

  private static String render(FacetResult r) {
    if (r == null) {
      return "null";
    }
    StringBuilder sb = new StringBuilder("value=" + r.value + " children={");
    for (LabelAndValue lv : r.labelValues) {
      sb.append(lv.label.replace("\t", "\\t").replace("\u001F", "\\u001F")).append('=');
      sb.append(lv.value).append(' ');
    }
    return sb.append('}').toString();
  }

  /** Hierarchical dimension: paths a/x and a<TAB>. The child x belongs to a, not to a<TAB>. */
  public void testHierarchicalChildrenOfLabelFollowedBySiblingWithTab() throws IOException {
    checkHierarchical("a\t");
  }

  /** Same with a label that contains the path delimiter itself, which FacetsConfig escapes. */
  public void testHierarchicalChildrenOfLabelFollowedBySiblingWithEscapedDelimiter()
      throws IOException {
    checkHierarchical("a\u001F");
  }

  private void checkHierarchical(String sibling) throws IOException {
    try (Directory dir = newDirectory()) {
      FacetsConfig config = new FacetsConfig();
      config.setHierarchical("dim", true);
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document d1 = new Document();
        d1.add(new SortedSetDocValuesFacetField("dim", "a", "x"));
        w.addDocument(config.build(d1));
        Document d2 = new Document();
        d2.add(new SortedSetDocValuesFacetField("dim", sibling));
        w.addDocument(config.build(d2));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(r, config);
        Facets facets = new SortedSetDocValuesFacetCounts(state);
        // control: both labels are listed at the top level, and a/x is counted
        assertEquals(1, facets.getSpecificValue("dim", "a", "x"));
        assertEquals(1, facets.getSpecificValue("dim", sibling));
        // control: drill-down on a/x (indexed terms, not ordinals) finds the document
        DrillDownQuery ddq = new DrillDownQuery(config);
        ddq.add("dim", "a", "x");
        assertEquals(1, new IndexSearcher(r).count(ddq));
        FacetResult underA = facets.getTopChildren(10, "dim", "a");
        FacetResult underSibling = facets.getTopChildren(10, "dim", sibling);
        assertEquals(
            "children of [a] (getTopChildren); children of the sibling were " + render(underSibling),
            "value=1 children={x=1 }",
            render(underA));
        assertEquals(
            "children of [a] (getAllChildren)",
            "value=1 children={x=1 }",
            render(facets.getAllChildren("dim", "a")));
        assertEquals(
            "the sibling has no children (getAllChildren)",
            "value=1 children={}",
            render(facets.getAllChildren("dim", sibling)));
      }
    }
  }

  /** Control: a sibling label whose next character (space, U+0020) sorts above U+001F is fine. */
  public void testControlSiblingContinuingAboveDelimiter() throws IOException {
    checkHierarchical("a b");
  }

  /** Control: taxonomy faceting gives the expected answer for the same labels and config. */
  public void testControlTaxonomyFacetsSameLabels() throws IOException {
    for (String sibling : new String[] {"a\t", "a\u001F"}) {
      try (Directory dir = newDirectory();
          Directory taxoDir = newDirectory()) {
        FacetsConfig config = new FacetsConfig();
        config.setHierarchical("dim", true);
        try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
            DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
          Document d1 = new Document();
          d1.add(new FacetField("dim", "a", "x"));
          w.addDocument(config.build(tw, d1));
          Document d2 = new Document();
          d2.add(new FacetField("dim", sibling));
          w.addDocument(config.build(tw, d2));
        }
        try (DirectoryReader r = DirectoryReader.open(dir);
            DirectoryTaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
          FacetsCollector fc =
              new IndexSearcher(r).search(new MatchAllDocsQuery(), new FacetsCollectorManager());
          Facets facets = new FastTaxonomyFacetCounts(tr, config, fc);
          assertEquals("value=1 children={x=1 }", render(facets.getTopChildren(10, "dim", "a")));
          assertNull(facets.getTopChildren(10, "dim", sibling));
        }
      }
    }
  }

  /**
   * Two hierarchical dimensions "d" and "d<TAB>": every term of "d<TAB>" sorts between the bare
   * dimension term "d" and the terms "d U+001F ...", so the run of dimension "d" is split.
   */
  public void testDimensionNameFollowedByLongerDimensionNameWithTab() throws IOException {
    try (Directory dir = newDirectory()) {
      FacetsConfig config = new FacetsConfig();
      config.setHierarchical("d", true);
      config.setMultiValued("d", true);
      config.setHierarchical("d\t", true);
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document d1 = new Document();
        d1.add(new SortedSetDocValuesFacetField("d", "a"));
        d1.add(new SortedSetDocValuesFacetField("d", "b"));
        w.addDocument(config.build(d1));
        Document d2 = new Document();
        d2.add(new SortedSetDocValuesFacetField("d\t", "c"));
        w.addDocument(config.build(d2));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(r, config);
        Facets facets = new SortedSetDocValuesFacetCounts(state);
        assertEquals(1, facets.getSpecificValue("d", "a"));
        assertEquals("value=1 children={c=1 }", render(facets.getTopChildren(10, "d\t")));
        assertEquals(
            "dimension d (getTopChildren)",
            "value=1 children={a=1 b=1 }",
            render(facets.getTopChildren(10, "d")));
        assertEquals("number of dimensions (getAllDims)", 2, facets.getAllDims(10).size());
      }
    }
  }

  /**
   * Flat (non-hierarchical) multi-valued dimension "d" with requireDimCount, next to a dimension
   * "d<TAB>": the dimension run of "d" is split and one of its labels is lost.
   */
  public void testFlatDimCountDimensionFollowedByLongerDimensionNameWithTab() throws IOException {
    try (Directory dir = newDirectory()) {
      FacetsConfig config = new FacetsConfig();
      config.setMultiValued("d", true);
      config.setRequireDimCount("d", true);
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document d1 = new Document();
        d1.add(new SortedSetDocValuesFacetField("d", "a"));
        d1.add(new SortedSetDocValuesFacetField("d", "b"));
        w.addDocument(config.build(d1));
        Document d2 = new Document();
        d2.add(new SortedSetDocValuesFacetField("d\t", "c"));
        w.addDocument(config.build(d2));
        Document d3 = new Document();
        d3.add(new SortedSetDocValuesFacetField("d", "b"));
        w.addDocument(config.build(d3));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(r, config);
        Facets facets = new SortedSetDocValuesFacetCounts(state);
        // two documents carry dimension d: value (dim count) 2, a in one, b in both
        assertEquals(
            "dimension d (getTopChildren)",
            "value=2 children={b=2 a=1 }",
            render(facets.getTopChildren(10, "d")));
      }
    }
  }
}
