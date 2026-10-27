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
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SortedSetDocValues faceting builds its facet tree from the sorted order of the encoded paths
 * ({@code dim U+001F label U+001F ...}). A label or dimension name that is a sibling's name
 * followed by a character below U+001F (TAB, CR, LF, or the U+001E that FacetsConfig writes when it
 * escapes a label containing U+001E or U+001F) sorts between that sibling and its children.
 */
public class TestSortedSetDocValuesFacetLabelOrder extends LuceneTestCase {

  /** Hierarchical dimension: cat/shoes/red and cat/"shoes TAB" (an untrimmed label). */
  public void testChildrenOfLabelNextToLabelWithTab() throws IOException {
    checkChildrenOfShoes("shoes\t");
  }

  /** Same with a label that contains the path delimiter, which FacetsConfig escapes. */
  public void testChildrenOfLabelNextToLabelWithEscapedDelimiter() throws IOException {
    checkChildrenOfShoes("shoes\u001F");
  }

  /** Control: a sibling that continues with a space (U+0020, above the delimiter) is fine. */
  public void testControlSiblingContinuingWithSpace() throws IOException {
    checkChildrenOfShoes("shoes sale");
  }

  private void checkChildrenOfShoes(String sibling) throws IOException {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("cat", true);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        w.addDocument(config.build(doc(new SortedSetDocValuesFacetField("cat", "shoes", "red"))));
        w.addDocument(config.build(doc(new SortedSetDocValuesFacetField("cat", sibling))));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Facets facets = ssdvFacets(r, config);
        // the indexed counts are right
        assertEquals(1, facets.getSpecificValue("cat", "shoes"));
        assertEquals(1, facets.getSpecificValue("cat", "shoes", "red"));
        assertEquals(1, facets.getSpecificValue("cat", sibling));
        assertEquals(
            "children of cat/shoes; children of the sibling were "
                + render(facets.getTopChildren(10, "cat", sibling)),
            "value=1 {red=1}",
            render(facets.getTopChildren(10, "cat", "shoes")));
        assertEquals(
            "children of cat/shoes (getAllChildren)",
            "value=1 {red=1}",
            render(facets.getAllChildren("cat", "shoes")));
        assertNull("the sibling is a leaf", facets.getTopChildren(10, "cat", sibling));
      }
    }
  }

  /** Control: taxonomy faceting returns the right children for the same labels. */
  public void testControlTaxonomyFacetsSameLabels() throws IOException {
    for (String sibling : new String[] {"shoes\t", "shoes\u001F"}) {
      FacetsConfig config = new FacetsConfig();
      config.setHierarchical("cat", true);
      try (Directory dir = newDirectory();
          Directory taxoDir = newDirectory()) {
        try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
            DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
          w.addDocument(config.build(tw, doc(new FacetField("cat", "shoes", "red"))));
          w.addDocument(config.build(tw, doc(new FacetField("cat", sibling))));
        }
        try (DirectoryReader r = DirectoryReader.open(dir);
            DirectoryTaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
          FacetsCollector fc =
              new IndexSearcher(r).search(new MatchAllDocsQuery(), new FacetsCollectorManager());
          Facets facets = new FastTaxonomyFacetCounts(tr, config, fc);
          assertEquals("value=1 {red=1}", render(facets.getTopChildren(10, "cat", "shoes")));
          assertNull(facets.getTopChildren(10, "cat", sibling));
        }
      }
    }
  }

  /** Two hierarchical dimensions, "color" and "color TAB": the second one splits the first. */
  public void testDimensionNextToDimensionNameWithTab() throws IOException {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("color", true);
    config.setHierarchical("color\t", true);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        w.addDocument(config.build(doc(new SortedSetDocValuesFacetField("color", "red"))));
        w.addDocument(config.build(doc(new SortedSetDocValuesFacetField("color\t", "x"))));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Facets facets = ssdvFacets(r, config);
        assertEquals(1, facets.getSpecificValue("color", "red"));
        assertEquals("value=1 {x=1}", render(facets.getTopChildren(10, "color\t")));
        assertEquals(
            "dimension color", "value=1 {red=1}", render(facets.getTopChildren(10, "color")));
        assertEquals("getAllDims", 2, facets.getAllDims(10).size());
        List<FacetResult> topDims = facets.getTopDims(10, 10);
        assertEquals("getTopDims", 2, topDims.size());
        for (FacetResult result : topDims) {
          assertNotNull("getTopDims returned a null entry: " + topDims, result);
        }
      }
    }
  }

  /** Flat multi-valued dimension "tag" with requireDimCount, next to a dimension "tag TAB". */
  public void testFlatDimCountDimensionNextToDimensionNameWithTab() throws IOException {
    FacetsConfig config = new FacetsConfig();
    config.setMultiValued("tag", true);
    config.setRequireDimCount("tag", true);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        w.addDocument(
            config.build(
                doc(
                    new SortedSetDocValuesFacetField("tag", "a"),
                    new SortedSetDocValuesFacetField("tag", "b"))));
        w.addDocument(config.build(doc(new SortedSetDocValuesFacetField("tag\t", "c"))));
        w.addDocument(config.build(doc(new SortedSetDocValuesFacetField("tag", "b"))));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Facets facets = ssdvFacets(r, config);
        // two documents carry dimension tag: dim count 2, a in one, b in both
        assertEquals(
            "dimension tag", "value=2 {b=2, a=1}", render(facets.getTopChildren(10, "tag")));
      }
    }
  }

  private static Document doc(SortedSetDocValuesFacetField... fields) {
    Document doc = new Document();
    for (SortedSetDocValuesFacetField field : fields) {
      doc.add(field);
    }
    return doc;
  }

  private static Document doc(FacetField field) {
    Document doc = new Document();
    doc.add(field);
    return doc;
  }

  private static Facets ssdvFacets(DirectoryReader r, FacetsConfig config) throws IOException {
    SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(r, config);
    FacetsCollector fc =
        new IndexSearcher(r).search(new MatchAllDocsQuery(), new FacetsCollectorManager());
    return new SortedSetDocValuesFacetCounts(state, fc);
  }

  private static String render(FacetResult result) {
    if (result == null) {
      return "null";
    }
    StringBuilder sb = new StringBuilder("value=").append(result.value).append(" {");
    for (int i = 0; i < result.labelValues.length; i++) {
      LabelAndValue lv = result.labelValues[i];
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(lv.label.replace("\t", "<TAB>").replace("\u001F", "<U+001F>"));
      sb.append('=').append(lv.value);
    }
    return sb.append('}').toString();
  }
}
