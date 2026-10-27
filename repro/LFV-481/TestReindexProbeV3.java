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
package org.example.probe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.ReindexingEnrichedDirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Probes for the intended-behaviour review, in a package outside Lucene so only public API can be
 * used. Each test prints one PROBE line and asserts the documented behaviour (ordinals preserved).
 */
public class TestReindexProbeV3 extends LuceneTestCase {

  private static String counts(DirectoryReader reader, DirectoryTaxonomyReader taxoReader)
      throws Exception {
    FacetsConfig config = new FacetsConfig();
    IndexSearcher searcher = new IndexSearcher(reader);
    try {
      Facets facets =
          new FastTaxonomyFacetCounts(
              taxoReader,
              config,
              searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager()));
      FacetResult result = facets.getTopChildren(10, "Author");
      List<String> sorted = new ArrayList<>();
      if (result != null) {
        for (LabelAndValue lv : result.labelValues) {
          sorted.add(lv.label + "=" + lv.value);
        }
      }
      Collections.sort(sorted);
      return String.join(" ", sorted);
    } catch (RuntimeException e) {
      return "EXCEPTION " + e;
    }
  }

  /** The writer's own getSize() shrinks across a call that promises to preserve ordinals. */
  public void testWriterSizeAcrossReindex() throws Exception {
    try (Directory taxoDir = newDirectory()) {
      ReindexingEnrichedDirectoryTaxonomyWriter taxoWriter =
          new ReindexingEnrichedDirectoryTaxonomyWriter(taxoDir, null);
      try {
        taxoWriter.addCategory(new FacetLabel("Author", "Alice"));
        taxoWriter.commit();
        taxoWriter.addCategory(new FacetLabel("Author", "Bob"));
        int before = taxoWriter.getSize();
        taxoWriter.reindexWithNewOrdinalData((label, d) -> {});
        int after = taxoWriter.getSize();
        System.out.println("PROBE writer-size before=" + before + " after=" + after);
        assertEquals("getSize before/after reindex", before, after);
      } finally {
        taxoWriter.close();
      }
    }
  }

  /**
   * NRT application (no commit of either index between adding the document and the reindex). After
   * the reindex, with no new category added, the main index refers to an ordinal the taxonomy no
   * longer has.
   */
  public void testNrtCountsAfterReindexNoNewCategory() throws Exception {
    try (Directory indexDir = newDirectory();
        Directory taxoDir = newDirectory()) {
      ReindexingEnrichedDirectoryTaxonomyWriter taxoWriter =
          new ReindexingEnrichedDirectoryTaxonomyWriter(taxoDir, null);
      IndexWriter writer =
          new IndexWriter(indexDir, new IndexWriterConfig(new MockAnalyzer(random())));
      FacetsConfig config = new FacetsConfig();
      try {
        Document doc = new Document();
        doc.add(new FacetField("Author", "Alice"));
        writer.addDocument(config.build(taxoWriter, doc));
        taxoWriter.commit();
        writer.commit();

        doc = new Document();
        doc.add(new FacetField("Author", "Bob"));
        writer.addDocument(config.build(taxoWriter, doc));

        taxoWriter.reindexWithNewOrdinalData((label, d) -> {});

        String result;
        try (DirectoryReader reader = DirectoryReader.open(writer);
            DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter)) {
          result = "taxoSize=" + taxoReader.getSize() + " counts: " + counts(reader, taxoReader);
        }
        System.out.println("PROBE nrt-no-new-category " + result);
        assertEquals("taxoSize=4 counts: Alice=1 Bob=1", result);
      } finally {
        writer.close();
        taxoWriter.close();
      }
    }
  }

  /** Control: when both indexes are committed before the call, ordinals are preserved. */
  public void testControlCommittedBeforeReindex() throws Exception {
    try (Directory indexDir = newDirectory();
        Directory taxoDir = newDirectory()) {
      ReindexingEnrichedDirectoryTaxonomyWriter taxoWriter =
          new ReindexingEnrichedDirectoryTaxonomyWriter(taxoDir, null);
      IndexWriter writer =
          new IndexWriter(indexDir, new IndexWriterConfig(new MockAnalyzer(random())));
      FacetsConfig config = new FacetsConfig();
      try {
        Document doc = new Document();
        doc.add(new FacetField("Author", "Alice"));
        writer.addDocument(config.build(taxoWriter, doc));
        taxoWriter.commit();
        writer.commit();

        doc = new Document();
        doc.add(new FacetField("Author", "Bob"));
        writer.addDocument(config.build(taxoWriter, doc));
        taxoWriter.commit();
        writer.commit();

        taxoWriter.reindexWithNewOrdinalData((label, d) -> {});
        int carolOrd = taxoWriter.addCategory(new FacetLabel("Author", "Carol"));
        taxoWriter.commit();
        writer.commit();

        String result;
        try (DirectoryReader reader = DirectoryReader.open(indexDir);
            DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir)) {
          result = "carolOrd=" + carolOrd + " counts: " + counts(reader, taxoReader);
        }
        System.out.println("PROBE control-committed " + result);
        assertEquals("carolOrd=4 counts: Alice=1 Bob=1", result);
      } finally {
        writer.close();
        taxoWriter.close();
      }
    }
  }
}
