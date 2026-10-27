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
import org.apache.lucene.document.StoredField;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * {@link ReindexingEnrichedDirectoryTaxonomyWriter#reindexWithNewOrdinalData} promises that "the
 * ordinals themselves will be preserved, so the caller does not need to update references to them
 * in the main index". That must also hold for categories added since the last taxonomy commit.
 */
public class TestReindexWithUncommittedCategories extends LuceneTestCase {

  /** The new per-category data: here, the depth of each category. */
  private static void newData(FacetLabel label, Document doc) {
    doc.add(new StoredField("depth", label.length));
  }

  /** The writer itself forgets a category it added after its last commit. */
  public void testWriterKeepsUncommittedCategory() throws Exception {
    try (Directory taxoDir = newDirectory();
        ReindexingEnrichedDirectoryTaxonomyWriter taxoWriter =
            new ReindexingEnrichedDirectoryTaxonomyWriter(taxoDir, null)) {
      taxoWriter.addCategory(new FacetLabel("Author", "Alice")); // root=0, Author=1, Alice=2
      taxoWriter.commit();
      int bob = taxoWriter.addCategory(new FacetLabel("Author", "Bob")); // 3, not committed yet
      assertEquals(4, taxoWriter.getSize());

      taxoWriter.reindexWithNewOrdinalData(TestReindexWithUncommittedCategories::newData);

      assertEquals("getSize() after reindex", 4, taxoWriter.getSize());
      assertEquals(bob, taxoWriter.addCategory(new FacetLabel("Author", "Bob")));
    }
  }

  /** End to end: the main index keeps ordinal 3 for Bob, so the taxonomy must keep it too. */
  public void testFacetCountsAfterReindex() throws Exception {
    try (Directory indexDir = newDirectory();
        Directory taxoDir = newDirectory();
        IndexWriter writer = new IndexWriter(indexDir, newIndexWriterConfig());
        ReindexingEnrichedDirectoryTaxonomyWriter taxoWriter =
            new ReindexingEnrichedDirectoryTaxonomyWriter(taxoDir, null)) {
      FacetsConfig config = new FacetsConfig();

      Document doc = new Document();
      doc.add(new FacetField("Author", "Alice"));
      writer.addDocument(config.build(taxoWriter, doc));
      taxoWriter.commit(); // the taxonomy first, then the main index
      writer.commit();

      doc = new Document();
      doc.add(new FacetField("Author", "Bob")); // Author/Bob gets ordinal 3
      writer.addDocument(config.build(taxoWriter, doc));

      // update the per-category data; the call also commits the taxonomy
      taxoWriter.reindexWithNewOrdinalData(TestReindexWithUncommittedCategories::newData);

      doc = new Document();
      doc.add(new FacetField("Author", "Carol"));
      writer.addDocument(config.build(taxoWriter, doc));
      taxoWriter.commit();
      writer.commit();

      try (DirectoryReader reader = DirectoryReader.open(indexDir);
          DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir)) {
        Facets facets =
            new FastTaxonomyFacetCounts(
                FacetsConfig.DEFAULT_INDEX_FIELD_NAME, reader, taxoReader, config);
        String counts =
            "Alice="
                + facets.getSpecificValue("Author", "Alice")
                + " Bob="
                + facets.getSpecificValue("Author", "Bob")
                + " Carol="
                + facets.getSpecificValue("Author", "Carol");
        assertEquals("Alice=1 Bob=1 Carol=1", counts);
      }
    }
  }

  /** The same cause on a taxonomy that was never committed. */
  public void testReindexBeforeFirstCommit() throws Exception {
    try (Directory taxoDir = newDirectory();
        ReindexingEnrichedDirectoryTaxonomyWriter taxoWriter =
            new ReindexingEnrichedDirectoryTaxonomyWriter(taxoDir, null)) {
      int alice = taxoWriter.addCategory(new FacetLabel("Author", "Alice"));

      taxoWriter.reindexWithNewOrdinalData(TestReindexWithUncommittedCategories::newData);

      assertEquals(3, taxoWriter.getSize());
      assertEquals(alice, taxoWriter.addCategory(new FacetLabel("Author", "Alice")));
    }
  }

  /** Control: with a commit right before the call, every ordinal is kept. */
  public void testCommitBeforeReindexKeepsOrdinals() throws Exception {
    try (Directory taxoDir = newDirectory();
        ReindexingEnrichedDirectoryTaxonomyWriter taxoWriter =
            new ReindexingEnrichedDirectoryTaxonomyWriter(taxoDir, null)) {
      taxoWriter.addCategory(new FacetLabel("Author", "Alice"));
      taxoWriter.commit();
      int bob = taxoWriter.addCategory(new FacetLabel("Author", "Bob"));
      taxoWriter.commit();

      taxoWriter.reindexWithNewOrdinalData(TestReindexWithUncommittedCategories::newData);

      assertEquals(4, taxoWriter.getSize());
      assertEquals(bob, taxoWriter.addCategory(new FacetLabel("Author", "Bob")));
    }
  }
}
