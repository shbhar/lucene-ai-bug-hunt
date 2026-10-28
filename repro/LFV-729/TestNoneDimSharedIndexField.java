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
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.IntAssociationFacetField;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * FacetsConfig documents DrillDownTermsIndexing as a per-dimension option and NONE as "Index no
 * drill down terms". By default every dimension writes to the same index field
 * (FacetsConfig.DEFAULT_INDEX_FIELD_NAME). These tests set NONE on one dimension ("color"), keep
 * the default (ALL) on another ("shape"), leave both in the default index field, and expect that
 * documents carrying either dimension can be indexed, counted and (for "shape") drilled down.
 *
 * <p>Public API only (FacetsConfig, facet fields, IndexWriter, IndexSearcher, Facets); the class
 * is in org.apache.lucene.facet only by convention.
 */
public class TestNoneDimSharedIndexField extends LuceneTestCase {

  private static FacetsConfig noneColorConfig() {
    FacetsConfig config = new FacetsConfig();
    config.setDrillDownTermsIndexing("color", FacetsConfig.DrillDownTermsIndexing.NONE);
    // "shape" keeps the default DimConfig: ALL drill-down terms, DEFAULT_INDEX_FIELD_NAME
    return config;
  }

  private static Document ssdv(String dim, String value) {
    Document doc = new Document();
    doc.add(new SortedSetDocValuesFacetField(dim, value));
    return doc;
  }

  private static Document taxo(String dim, String value) {
    Document doc = new Document();
    doc.add(new FacetField(dim, value));
    return doc;
  }

  private static void add(IndexWriter w, Document built, String what) throws IOException {
    try {
      w.addDocument(built);
    } catch (IllegalArgumentException e) {
      throw new AssertionError(
          what + " was rejected by IndexWriter.addDocument: " + e.getMessage(), e);
    }
  }

  /** Sorted-set facets: NONE-only document first, then a default (ALL) document. */
  public void testSortedSetNoneDocThenAllDoc() throws Exception {
    FacetsConfig config = noneColorConfig();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        add(w, config.build(ssdv("color", "red")), "doc with only the NONE dim 'color'");
        add(w, config.build(ssdv("shape", "round")), "doc with only the ALL dim 'shape'");
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(2, r.numDocs());
        IndexSearcher s = newSearcher(r);
        FacetsCollector fc = s.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        Facets facets =
            new SortedSetDocValuesFacetCounts(
                new DefaultSortedSetDocValuesReaderState(r, config), fc);
        assertEquals(1, facets.getSpecificValue("color", "red").intValue());
        assertEquals(1, facets.getSpecificValue("shape", "round").intValue());
        DrillDownQuery q = new DrillDownQuery(config);
        q.add("shape", "round");
        assertEquals(1, s.count(q));
      }
    }
  }

  /** Sorted-set facets: a document with both dims, then a document missing the ALL dim. */
  public void testSortedSetBothDimsDocThenNoneOnlyDoc() throws Exception {
    FacetsConfig config = noneColorConfig();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document both = new Document();
      both.add(new SortedSetDocValuesFacetField("color", "red"));
      both.add(new SortedSetDocValuesFacetField("shape", "round"));
      add(w, config.build(both), "doc with both dims");
      add(w, config.build(ssdv("color", "blue")), "doc with only the NONE dim 'color'");
      assertEquals(2, w.getDocStats().numDocs);
    }
  }

  /** Taxonomy facets: default (ALL) document first, then a NONE-only document. */
  public void testTaxonomyAllDocThenNoneDoc() throws Exception {
    FacetsConfig config = noneColorConfig();
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
          DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
        add(w, config.build(tw, taxo("shape", "round")), "doc with only the ALL dim 'shape'");
        add(w, config.build(tw, taxo("color", "red")), "doc with only the NONE dim 'color'");
      }
      try (DirectoryReader r = DirectoryReader.open(dir);
          TaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
        assertEquals(2, r.numDocs());
        IndexSearcher s = newSearcher(r);
        FacetsCollector fc = s.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        Facets facets = new FastTaxonomyFacetCounts(tr, config, fc);
        assertEquals(1, facets.getSpecificValue("color", "red").intValue());
        assertEquals(1, facets.getSpecificValue("shape", "round").intValue());
      }
    }
  }

  /** After a commit of a NONE-only document, a new writer must still accept an ALL document. */
  public void testSortedSetNoneDocCommittedThenNewWriterAllDoc() throws Exception {
    FacetsConfig config = noneColorConfig();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        add(w, config.build(ssdv("color", "red")), "doc with only the NONE dim 'color'");
        w.commit();
      }
      try (IndexWriter w =
          new IndexWriter(
              dir, new IndexWriterConfig().setOpenMode(IndexWriterConfig.OpenMode.APPEND))) {
        add(
            w,
            config.build(ssdv("shape", "round")),
            "after commit + reopen, doc with only the ALL dim 'shape'");
        assertEquals(2, w.getDocStats().numDocs);
      }
    }
  }

  /** Two int-association dims sharing one index field, one of them NONE. */
  public void testIntAssociationNoneDocThenAllDoc() throws Exception {
    FacetsConfig config = noneColorConfig();
    config.setIndexFieldName("color", "$assoc");
    config.setIndexFieldName("shape", "$assoc");
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
        DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
      Document d1 = new Document();
      d1.add(new IntAssociationFacetField(3, "color", "red"));
      add(w, config.build(tw, d1), "assoc doc with only the NONE dim 'color'");
      Document d2 = new Document();
      d2.add(new IntAssociationFacetField(4, "shape", "round"));
      add(w, config.build(tw, d2), "assoc doc with only the ALL dim 'shape'");
      assertEquals(2, w.getDocStats().numDocs);
    }
  }

  // ---- controls: expected to pass on every version ----

  /** Control: the NONE dim in its own index field; same documents as the first test. */
  public void testControlNoneDimInOwnField() throws Exception {
    FacetsConfig config = noneColorConfig();
    config.setIndexFieldName("color", "$color");
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
        DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
      add(w, config.build(tw, taxo("color", "red")), "NONE dim in own field");
      add(w, config.build(tw, taxo("shape", "round")), "ALL dim in default field");
      add(w, config.build(tw, taxo("color", "blue")), "NONE dim in own field again");
      assertEquals(3, w.getDocStats().numDocs);
    }
  }

  /** Control: both dims at the default (ALL) in the shared field. */
  public void testControlAllDimsDefault() throws Exception {
    FacetsConfig config = new FacetsConfig();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      add(w, config.build(ssdv("color", "red")), "ALL dim 'color'");
      add(w, config.build(ssdv("shape", "round")), "ALL dim 'shape'");
      assertEquals(2, w.getDocStats().numDocs);
    }
  }

  /** Control: every document carries only NONE dims (no document ever has drill-down terms). */
  public void testControlOnlyNoneDocs() throws Exception {
    FacetsConfig config = noneColorConfig();
    config.setDrillDownTermsIndexing("shape", FacetsConfig.DrillDownTermsIndexing.NONE);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      add(w, config.build(ssdv("color", "red")), "NONE dim 'color'");
      add(w, config.build(ssdv("shape", "round")), "NONE dim 'shape'");
      assertEquals(2, w.getDocStats().numDocs);
    }
  }
}
