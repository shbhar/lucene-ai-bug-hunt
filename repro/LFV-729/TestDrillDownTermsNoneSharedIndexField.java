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
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetsConfig.DrillDownTermsIndexing;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * "rating" uses DrillDownTermsIndexing.NONE, "author" keeps the default (ALL), and both stay in the
 * default index field $facets. One document has only "rating", another has "author": both should be
 * indexed and counted, and "author" should still drill down.
 */
public class TestDrillDownTermsNoneSharedIndexField extends LuceneTestCase {

  private static FacetsConfig noneRatingConfig() {
    FacetsConfig config = new FacetsConfig();
    config.setDrillDownTermsIndexing("rating", DrillDownTermsIndexing.NONE);
    return config;
  }

  private static String value(String dim) {
    return dim.equals("rating") ? "5" : "Bob";
  }

  private static Document taxoDoc(String dim) {
    Document doc = new Document();
    doc.add(new FacetField(dim, value(dim)));
    return doc;
  }

  private static Document sortedSetDoc(String dim) {
    Document doc = new Document();
    doc.add(new SortedSetDocValuesFacetField(dim, value(dim)));
    return doc;
  }

  /** Indexes one taxonomy document per dimension, in order, then checks counts and drill-down. */
  private void indexAndCheckTaxonomy(FacetsConfig config, String... dims) throws IOException {
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
          DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
        for (String dim : dims) {
          w.addDocument(config.build(tw, taxoDoc(dim)));
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir);
          TaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
        assertEquals(dims.length, r.numDocs());
        IndexSearcher s = newSearcher(r);
        FacetsCollector fc = s.search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
        for (String dim : dims) {
          String field = config.getDimConfig(dim).indexFieldName;
          Facets facets = new FastTaxonomyFacetCounts(field, tr, config, fc);
          assertEquals(1, facets.getSpecificValue(dim, value(dim)).intValue());
          checkDrillDown(config, s, dim);
        }
      }
    }
  }

  private static void checkDrillDown(FacetsConfig config, IndexSearcher s, String dim)
      throws IOException {
    if (config.getDimConfig(dim).drillDownTermsIndexing != DrillDownTermsIndexing.NONE) {
      DrillDownQuery q = new DrillDownQuery(config);
      q.add(dim, value(dim));
      assertEquals(1, s.count(q));
    }
  }

  public void testTaxonomyNoneOnlyDocThenAuthorDoc() throws Exception {
    indexAndCheckTaxonomy(noneRatingConfig(), "rating", "author");
  }

  public void testTaxonomyAuthorDocThenNoneOnlyDoc() throws Exception {
    indexAndCheckTaxonomy(noneRatingConfig(), "author", "rating");
  }

  public void testSortedSetNoneOnlyDocThenAuthorDoc() throws Exception {
    FacetsConfig config = noneRatingConfig();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        w.addDocument(config.build(sortedSetDoc("rating")));
        w.addDocument(config.build(sortedSetDoc("author")));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(2, r.numDocs());
        IndexSearcher s = newSearcher(r);
        FacetsCollector fc = s.search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
        Facets facets =
            new SortedSetDocValuesFacetCounts(
                new DefaultSortedSetDocValuesReaderState(r, config), fc);
        assertEquals(1, facets.getSpecificValue("rating", "5").intValue());
        assertEquals(1, facets.getSpecificValue("author", "Bob").intValue());
        checkDrillDown(config, s, "author");
      }
    }
  }

  /** A committed "rating"-only document must not lock out "author" documents in a new writer. */
  public void testNoneOnlyDocCommittedThenAuthorDocInNewWriter() throws Exception {
    FacetsConfig config = noneRatingConfig();
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory();
        DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        w.addDocument(config.build(tw, taxoDoc("rating")));
        w.commit();
      }
      try (IndexWriter w =
          new IndexWriter(dir, newIndexWriterConfig().setOpenMode(OpenMode.APPEND))) {
        w.addDocument(config.build(tw, taxoDoc("author")));
        assertEquals(2, w.getDocStats().numDocs);
      }
    }
  }

  /** Control (today's workaround): "rating" in its own index field. */
  public void testControlNoneDimInOwnIndexField() throws Exception {
    FacetsConfig config = noneRatingConfig();
    config.setIndexFieldName("rating", "$rating");
    indexAndCheckTaxonomy(config, "rating", "author");
  }

  /** Control: no dimension uses NONE. */
  public void testControlNoNoneDim() throws Exception {
    indexAndCheckTaxonomy(new FacetsConfig(), "rating", "author");
  }

  /** Control: every dimension uses NONE, so no document has drill-down terms. */
  public void testControlEveryDimNone() throws Exception {
    FacetsConfig config = noneRatingConfig();
    config.setDrillDownTermsIndexing("author", DrillDownTermsIndexing.NONE);
    indexAndCheckTaxonomy(config, "rating", "author");
  }
}
