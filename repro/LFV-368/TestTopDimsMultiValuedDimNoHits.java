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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.sortedset.ConcurrentSortedSetDocValuesFacetCounts;
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
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check of the Facets#getTopDims contract: "Results should be the same as calling
 * getAllDims and then only using the first topNDims."
 *
 * <p>Setup: two flat dims "a" and "b", both multi-valued, neither requiring dim counts. One doc
 * has b=b1 (content=hit), one doc has a=a1 (content=miss). The query content:hit only matches
 * the b doc, so dim "a" has no hits and getAllDims omits it.
 */
public class TestTopDimsMultiValuedDimNoHits extends LuceneTestCase {

  private static FacetsConfig newConfig(boolean requireDimCount) {
    FacetsConfig config = new FacetsConfig();
    config.setMultiValued("a", true);
    config.setMultiValued("b", true);
    if (requireDimCount) {
      config.setRequireDimCount("a", true);
      config.setRequireDimCount("b", true);
    }
    return config;
  }

  private static void checkTopDims(Facets facets, int topNDims) throws Exception {
    List<FacetResult> all = facets.getAllDims(10);
    // Sanity: getAllDims lists only the dim with hits.
    assertEquals("getAllDims(10) = " + all, 1, all.size());
    assertEquals("b", all.get(0).dim);
    List<FacetResult> expected = all.subList(0, Math.min(topNDims, all.size()));
    List<FacetResult> actual = facets.getTopDims(topNDims, 10);
    assertFalse("getTopDims(" + topNDims + ", 10) contains null: " + actual, actual.contains(null));
    // FacetResult#equals ignores dim and path, so compare the dim names separately.
    assertEquals(
        "getTopDims(" + topNDims + ", 10) dims vs getAllDims(10) prefix dims",
        dims(expected),
        dims(actual));
    assertEquals(
        "getTopDims(" + topNDims + ", 10) vs getAllDims(10) prefix", expected, actual);
  }

  private static List<String> dims(List<FacetResult> results) {
    List<String> out = new ArrayList<>();
    for (FacetResult r : results) {
      out.add(r == null ? null : r.dim);
    }
    return out;
  }

  // ---------------- taxonomy ----------------

  private void runTaxonomy(boolean requireDimCount, int topNDims) throws Exception {
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      FacetsConfig config = newConfig(requireDimCount);
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
          DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
        // b first, then a: in the taxonomy, a becomes the youngest child of the root and is
        // visited first by getTopDims; this keeps the top-1 case off the replace-top branch.
        Document d1 = new Document();
        d1.add(new StringField("content", "hit", Field.Store.NO));
        d1.add(new FacetField("b", "b1"));
        w.addDocument(config.build(tw, d1));
        Document d2 = new Document();
        d2.add(new StringField("content", "miss", Field.Store.NO));
        d2.add(new FacetField("a", "a1"));
        w.addDocument(config.build(tw, d2));
        w.commit();
        tw.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir);
          TaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
        IndexSearcher s = new IndexSearcher(r);
        FacetsCollector fc =
            s.search(new TermQuery(new Term("content", "hit")), new FacetsCollectorManager());
        Facets facets = new FastTaxonomyFacetCounts(tr, config, fc);
        checkTopDims(facets, topNDims);
      }
    }
  }

  public void testTaxonomyTop10() throws Exception {
    runTaxonomy(false, 10);
  }

  public void testTaxonomyTop1() throws Exception {
    runTaxonomy(false, 1);
  }

  // ---------------- sorted-set doc values ----------------

  private void runSortedSet(boolean requireDimCount, int topNDims) throws Exception {
    runSortedSet(requireDimCount, topNDims, false);
  }

  private void runSortedSet(boolean requireDimCount, int topNDims, boolean concurrent)
      throws Exception {
    try (Directory dir = newDirectory()) {
      FacetsConfig config = newConfig(requireDimCount);
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document d1 = new Document();
        d1.add(new StringField("content", "hit", Field.Store.NO));
        d1.add(new SortedSetDocValuesFacetField("b", "b1"));
        w.addDocument(config.build(d1));
        Document d2 = new Document();
        d2.add(new StringField("content", "miss", Field.Store.NO));
        d2.add(new SortedSetDocValuesFacetField("a", "a1"));
        w.addDocument(config.build(d2));
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(r, config);
        FacetsCollector fc =
            s.search(new TermQuery(new Term("content", "hit")), new FacetsCollectorManager());
        if (concurrent) {
          ExecutorService exec = Executors.newFixedThreadPool(2);
          try {
            checkTopDims(new ConcurrentSortedSetDocValuesFacetCounts(state, fc, exec), topNDims);
          } finally {
            exec.shutdown();
            assertTrue(exec.awaitTermination(30, TimeUnit.SECONDS));
          }
        } else {
          checkTopDims(new SortedSetDocValuesFacetCounts(state, fc), topNDims);
        }
      }
    }
  }

  public void testConcurrentSortedSetTop10() throws Exception {
    runSortedSet(false, 10, true);
  }

  public void testSortedSetTop10() throws Exception {
    runSortedSet(false, 10);
  }

  public void testSortedSetTop1() throws Exception {
    runSortedSet(false, 1);
  }

  // ---------------- controls (expected to pass) ----------------

  /** Same data, dims require dim counts: getTopDims must agree with getAllDims. */
  public void testControlRequireDimCount() throws Exception {
    runTaxonomy(true, 10);
    runTaxonomy(true, 1);
    runSortedSet(true, 10);
    runSortedSet(true, 1);
    runSortedSet(true, 10, true);
  }
}
