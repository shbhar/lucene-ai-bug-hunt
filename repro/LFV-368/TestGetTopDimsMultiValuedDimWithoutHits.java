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
import org.apache.lucene.facet.taxonomy.AssociationAggregationFunction;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.IntAssociationFacetField;
import org.apache.lucene.facet.taxonomy.TaxonomyFacetIntAssociations;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.NamedThreadFactory;

/**
 * Facets#getTopDims "should be the same as calling getAllDims and then only using the first
 * topNDims". Two dimensions, "a" and "b", are multi-valued and leave requireDimCount at its default
 * (false). One document has b=b1, the other a=a1, and the query matches only the first one, so
 * getAllDims(10) is [b]. getTopDims must then return [b] for any topNDims.
 */
public class TestGetTopDimsMultiValuedDimWithoutHits extends LuceneTestCase {

  public void testTaxonomyCountsTop10() throws Exception {
    checkTaxonomy(newConfig(true, false), false, 10);
  }

  public void testTaxonomyCountsTop1() throws Exception {
    checkTaxonomy(newConfig(true, false), false, 1);
  }

  /** 1,100 more categories and documents: the taxonomy facets count into a sparse hash table. */
  public void testTaxonomyCountsTop10SparseCounts() throws Exception {
    checkTaxonomy(newConfig(true, false), false, 10, 1100);
  }

  /** Association dims cannot set requireDimCount (FacetsConfig rejects it). */
  public void testTaxonomyIntAssociationsTop10() throws Exception {
    checkTaxonomy(newConfig(true, false), true, 10);
  }

  public void testSortedSetTop10() throws Exception {
    checkSortedSet(newConfig(true, false), false, 10);
  }

  public void testSortedSetTop1() throws Exception {
    checkSortedSet(newConfig(true, false), false, 1);
  }

  public void testConcurrentSortedSetTop10() throws Exception {
    checkSortedSet(newConfig(true, false), true, 10);
  }

  /** Controls: requireDimCount=true and single-valued dims give [b] already. */
  public void testControls() throws Exception {
    for (FacetsConfig config : List.of(newConfig(true, true), newConfig(false, false))) {
      for (int topNDims : new int[] {10, 1}) {
        checkTaxonomy(config, false, topNDims);
        checkSortedSet(config, false, topNDims);
        checkSortedSet(config, true, topNDims);
      }
    }
  }

  private static FacetsConfig newConfig(boolean multiValued, boolean requireDimCount) {
    FacetsConfig config = new FacetsConfig();
    for (String dim : new String[] {"a", "b"}) {
      config.setMultiValued(dim, multiValued);
      config.setRequireDimCount(dim, requireDimCount);
    }
    return config;
  }

  private static Document doc(String id, IndexableField facetField) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.NO));
    doc.add(facetField);
    return doc;
  }

  private static FacetsCollector hitsOfDocB(DirectoryReader reader) throws IOException {
    return new IndexSearcher(reader)
        .search(new TermQuery(new Term("id", "hit")), new FacetsCollectorManager());
  }

  private void checkTaxonomy(FacetsConfig config, boolean associations, int topNDims)
      throws IOException {
    checkTaxonomy(config, associations, topNDims, 0);
  }

  private void checkTaxonomy(
      FacetsConfig config, boolean associations, int topNDims, int paddingDocs) throws IOException {
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
          DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
        // b is added first, so a is the newest child of the taxonomy root and getTopDims visits it
        // first.
        w.addDocument(
            config.build(
                tw,
                doc(
                    "hit",
                    associations
                        ? new IntAssociationFacetField(7, "b", "b1")
                        : new FacetField("b", "b1"))));
        w.addDocument(
            config.build(
                tw,
                doc(
                    "miss",
                    associations
                        ? new IntAssociationFacetField(5, "a", "a1")
                        : new FacetField("a", "a1"))));
        for (int i = 0; i < paddingDocs; i++) {
          w.addDocument(config.build(tw, doc("padding", new FacetField("c", "c" + i))));
        }
        w.commit();
        tw.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir);
          TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir)) {
        FacetsCollector fc = hitsOfDocB(reader);
        Facets facets =
            associations
                ? new TaxonomyFacetIntAssociations(
                    FacetsConfig.DEFAULT_INDEX_FIELD_NAME,
                    taxoReader,
                    config,
                    fc,
                    AssociationAggregationFunction.SUM)
                : new FastTaxonomyFacetCounts(taxoReader, config, fc);
        assertTopDimsArePrefixOfAllDims(facets, topNDims);
      }
    }
  }

  private void checkSortedSet(FacetsConfig config, boolean concurrent, int topNDims)
      throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(config.build(doc("hit", new SortedSetDocValuesFacetField("b", "b1"))));
        w.addDocument(config.build(doc("miss", new SortedSetDocValuesFacetField("a", "a1"))));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        SortedSetDocValuesReaderState state =
            new DefaultSortedSetDocValuesReaderState(reader, config);
        FacetsCollector fc = hitsOfDocB(reader);
        if (concurrent) {
          ExecutorService executor =
              Executors.newFixedThreadPool(2, new NamedThreadFactory("getTopDims"));
          try {
            assertTopDimsArePrefixOfAllDims(
                new ConcurrentSortedSetDocValuesFacetCounts(state, fc, executor), topNDims);
          } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
          }
        } else {
          assertTopDimsArePrefixOfAllDims(new SortedSetDocValuesFacetCounts(state, fc), topNDims);
        }
      }
    }
  }

  private static void assertTopDimsArePrefixOfAllDims(Facets facets, int topNDims)
      throws IOException {
    List<FacetResult> allDims = facets.getAllDims(10);
    assertEquals("dims of getAllDims(10)", List.of("b"), dims(allDims));
    List<FacetResult> expected = allDims.subList(0, Math.min(topNDims, allDims.size()));
    List<FacetResult> topDims = facets.getTopDims(topNDims, 10);
    // FacetResult#equals ignores dim and path, so compare the dimension names first:
    assertEquals("dims of getTopDims(" + topNDims + ", 10)", dims(expected), dims(topDims));
    assertEquals(expected, topDims);
  }

  private static List<String> dims(List<FacetResult> results) {
    List<String> dims = new ArrayList<>();
    for (FacetResult result : results) {
      dims.add(result == null ? null : result.dim);
    }
    return dims;
  }
}
