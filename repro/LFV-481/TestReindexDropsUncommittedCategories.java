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
package org.apache.lucene.facet.taxonomy.directory;

import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * ReindexingEnrichedDirectoryTaxonomyWriter#reindexWithNewOrdinalData promises that "the ordinals
 * themselves will be preserved, so the caller does not need to update references to them in the
 * main index". It records the paths to re-add from a reader opened on the taxonomy Directory, which
 * only sees the last commit, so categories added since that commit are dropped and their ordinals
 * are later handed out to other categories.
 *
 * <p>Public API only.
 */
public class TestReindexDropsUncommittedCategories extends LuceneTestCase {

  /** Categories added after the last taxonomy commit lose their ordinals. */
  public void testUncommittedCategoryOrdinalIsNotPreserved() throws Exception {
    try (Directory indexDir = newDirectory();
        Directory taxoDir = newDirectory()) {
      ReindexingEnrichedDirectoryTaxonomyWriter taxoWriter =
          new ReindexingEnrichedDirectoryTaxonomyWriter(taxoDir, null);
      IndexWriter writer = new IndexWriter(indexDir, new IndexWriterConfig(new MockAnalyzer(random())));
      FacetsConfig config = new FacetsConfig();

      // doc 1: Author/Alice, then commit taxonomy first and the index second (documented order)
      Document doc = new Document();
      doc.add(new FacetField("Author", "Alice"));
      writer.addDocument(config.build(taxoWriter, doc));
      taxoWriter.commit();
      writer.commit();

      // doc 2: Author/Bob; both indexes are open and in use, nothing committed yet
      doc = new Document();
      doc.add(new FacetField("Author", "Bob"));
      writer.addDocument(config.build(taxoWriter, doc));
      int bobOrd = taxoWriter.getSize() - 1;
      int sizeBefore = taxoWriter.getSize(); // root, Author, Author/Alice, Author/Bob

      // update the per-ordinal data; ordinals are documented to be preserved
      taxoWriter.reindexWithNewOrdinalData((label, d) -> {});

      // A category added after the reindex must not take over an existing ordinal.
      int carolOrd = taxoWriter.addCategory(new FacetLabel("Author", "Carol"));
      taxoWriter.commit();
      writer.commit();

      try (DirectoryReader reader = DirectoryReader.open(indexDir);
          DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir)) {
        IndexSearcher searcher = newSearcher(reader);
        Facets facets =
            new FastTaxonomyFacetCounts(
                taxoReader,
                config,
                searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager()));
        FacetResult result = facets.getTopChildren(10, "Author");
        // label order of tied counts is not part of the contract: compare sorted
        java.util.List<String> sorted = new java.util.ArrayList<>();
        for (LabelAndValue lv : result.labelValues) {
          sorted.add(lv.label + "=" + lv.value);
        }
        java.util.Collections.sort(sorted);
        String labels = String.join(" ", sorted);
        String msg =
            "sizeBefore="
                + sizeBefore
                + " bobOrd="
                + bobOrd
                + " carolOrd="
                + carolOrd
                + " taxoReader.getOrdinal(Author/Bob)="
                + taxoReader.getOrdinal(new FacetLabel("Author", "Bob"))
                + " counts: "
                + labels;
        assertEquals(msg, bobOrd, taxoReader.getOrdinal(new FacetLabel("Author", "Bob")));
        assertTrue(msg, carolOrd >= sizeBefore);
        assertEquals(msg, "Alice=1 Bob=1", labels);
      } finally {
        writer.close();
        taxoWriter.close();
      }
    }
  }

  /** The same root cause when the taxonomy was never committed: the reindex cannot even start. */
  public void testReindexBeforeFirstCommit() throws Exception {
    try (Directory taxoDir = newDirectory()) {
      ReindexingEnrichedDirectoryTaxonomyWriter taxoWriter =
          new ReindexingEnrichedDirectoryTaxonomyWriter(taxoDir, null);
      try {
        int ord = taxoWriter.addCategory(new FacetLabel("Author", "Alice"));
        taxoWriter.reindexWithNewOrdinalData((label, d) -> {}); // throws IndexNotFoundException
        assertEquals(ord, taxoWriter.getSize() - 1);
      } finally {
        taxoWriter.close();
      }
    }
  }
}
