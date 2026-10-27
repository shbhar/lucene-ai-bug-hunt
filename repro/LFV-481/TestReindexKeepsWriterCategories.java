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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.ReindexingEnrichedDirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * ReindexingEnrichedDirectoryTaxonomyWriter#reindexWithNewOrdinalData javadoc: "Delete the existing
 * taxonomy index and recreate it using new ordinal data. The ordinals themselves will be preserved,
 * so the caller does not need to update references to them in the main index."
 *
 * <p>Ordinals returned by addCategory (directly, or through FacetsConfig.build) are referenced by
 * the main index whether or not the taxonomy was committed in between. Public API only.
 */
public class TestReindexKeepsWriterCategories extends LuceneTestCase {

  private static List<FacetLabel> writerView(ReindexingEnrichedDirectoryTaxonomyWriter tw)
      throws Exception {
    // the writer's own (NRT) view: every category the writer has handed out an ordinal for
    List<FacetLabel> paths = new ArrayList<>();
    try (DirectoryTaxonomyReader r = new DirectoryTaxonomyReader(tw)) {
      for (int ord = 0; ord < r.getSize(); ord++) {
        paths.add(r.getPath(ord));
      }
    }
    return paths;
  }

  /** Ordinal -> label mapping over every ordinal the writer handed out is unchanged by reindex. */
  public void testOrdinalsHandedOutSinceLastCommitArePreserved() throws Exception {
    Directory taxoDir = newDirectory();
    ReindexingEnrichedDirectoryTaxonomyWriter tw =
        new ReindexingEnrichedDirectoryTaxonomyWriter(taxoDir, null);
    try {
      int x = tw.addCategory(new FacetLabel("Color", "Red"));
      tw.commit(); // last commit: root, Color, Color/Red
      int y = tw.addCategory(new FacetLabel("Color", "Green")); // handed out, not committed yet
      assertEquals(x + 1, y);

      List<FacetLabel> before = writerView(tw);
      assertEquals(4, before.size());
      assertEquals(new FacetLabel("Color", "Green"), before.get(y));

      tw.reindexWithNewOrdinalData((label, doc) -> doc.add(new StoredField("v", 1)));

      // the writer must still know Color/Green under its old ordinal ...
      assertEquals("size after reindex", before.size(), tw.getSize());
      // ... and the committed taxonomy (reindex commits) must map every old ordinal to its old label
      try (DirectoryTaxonomyReader committed = new DirectoryTaxonomyReader(taxoDir)) {
        List<String> diffs = new ArrayList<>();
        for (int ord = 0; ord < before.size(); ord++) {
          int now = committed.getOrdinal(before.get(ord));
          if (now != ord) {
            diffs.add(before.get(ord) + ": ord " + ord + " -> " + now);
          }
        }
        assertTrue("ordinals not preserved by reindex: " + diffs, diffs.isEmpty());
      }
    } finally {
      IOUtils.close(tw, taxoDir);
    }
  }

  /**
   * End-to-end: a main-index document that references a category added after the last taxonomy
   * commit must still count under that category after reindex (no update of the main index).
   */
  public void testMainIndexFacetCountsSurviveReindex() throws Exception {
    Directory indexDir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter iw = new IndexWriter(indexDir, new IndexWriterConfig());
    ReindexingEnrichedDirectoryTaxonomyWriter tw =
        new ReindexingEnrichedDirectoryTaxonomyWriter(taxoDir, null);
    FacetsConfig config = new FacetsConfig();
    try {
      Document d1 = new Document();
      d1.add(new FacetField("Color", "Red"));
      iw.addDocument(config.build(tw, d1));
      tw.commit(); // taxonomy first, as the facet docs recommend
      iw.commit();

      Document d2 = new Document();
      d2.add(new FacetField("Color", "Green"));
      iw.addDocument(config.build(tw, d2)); // Color/Green gets a fresh ordinal, taxonomy uncommitted

      tw.reindexWithNewOrdinalData((label, doc) -> doc.add(new StoredField("v", 2)));

      // a category added after reindex must get a NEW ordinal, not reuse one the main index holds
      Document d3 = new Document();
      d3.add(new FacetField("Color", "Blue"));
      iw.addDocument(config.build(tw, d3));
      tw.commit();
      iw.commit();

      try (DirectoryReader r = DirectoryReader.open(indexDir);
          DirectoryTaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
        IndexSearcher s = newSearcher(r);
        FacetsCollector fc = s.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        Facets facets = new FastTaxonomyFacetCounts(tr, config, fc);
        String counts =
            "Red="
                + facets.getSpecificValue("Color", "Red")
                + " Green="
                + facets.getSpecificValue("Color", "Green")
                + " Blue="
                + facets.getSpecificValue("Color", "Blue")
                + " ord(Green)="
                + tr.getOrdinal(new FacetLabel("Color", "Green"))
                + " ord(Blue)="
                + tr.getOrdinal(new FacetLabel("Color", "Blue"));
        assertEquals(counts, 1, facets.getSpecificValue("Color", "Red").intValue());
        assertEquals(counts, 1, facets.getSpecificValue("Color", "Green").intValue());
        assertEquals(counts, 1, facets.getSpecificValue("Color", "Blue").intValue());
      }
    } finally {
      IOUtils.close(iw, tw, indexDir, taxoDir);
    }
  }

  /** Secondary: a writer whose taxonomy was never committed (only uncommitted categories). */
  public void testReindexOnNeverCommittedTaxonomy() throws Exception {
    Directory taxoDir = newDirectory();
    ReindexingEnrichedDirectoryTaxonomyWriter tw =
        new ReindexingEnrichedDirectoryTaxonomyWriter(taxoDir, null);
    try {
      int g = tw.addCategory(new FacetLabel("Color", "Green"));
      tw.reindexWithNewOrdinalData((label, doc) -> {});
      try (DirectoryTaxonomyReader committed = new DirectoryTaxonomyReader(taxoDir)) {
        assertEquals(g, committed.getOrdinal(new FacetLabel("Color", "Green")));
      }
    } finally {
      IOUtils.close(tw, taxoDir);
    }
  }

  /** Control: the documented case (commit right before reindex) must pass on every version. */
  public void testControlCommittedBeforeReindex() throws Exception {
    Directory taxoDir = newDirectory();
    ReindexingEnrichedDirectoryTaxonomyWriter tw =
        new ReindexingEnrichedDirectoryTaxonomyWriter(taxoDir, null);
    try {
      tw.addCategory(new FacetLabel("Color", "Red"));
      tw.addCategory(new FacetLabel("Color", "Green"));
      tw.commit();
      List<FacetLabel> before = writerView(tw);
      tw.reindexWithNewOrdinalData((label, doc) -> doc.add(new StoredField("v", 3)));
      assertEquals(before.size(), tw.getSize());
      try (DirectoryTaxonomyReader committed = new DirectoryTaxonomyReader(taxoDir)) {
        for (int ord = 0; ord < before.size(); ord++) {
          assertEquals(ord, committed.getOrdinal(before.get(ord)));
        }
      }
    } finally {
      IOUtils.close(tw, taxoDir);
    }
  }
}
