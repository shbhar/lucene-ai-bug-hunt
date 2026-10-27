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
package org.apache.lucene.facet.v1check;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.facet.taxonomy.directory.ReindexingEnrichedDirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: an addCategory() call whose ordinal document is rejected by the taxonomy's
 * IndexWriter (non-aborting IllegalArgumentException) must not change the ordinals that later
 * addCategory() calls return relative to what a taxonomy reader reports. Public API only.
 */
public class TestRejectedOrdinalDocV1 extends LuceneTestCase {

  /** One byte longer than the longest term IndexWriter accepts. */
  private static final String IMMENSE = "x".repeat(IndexWriter.MAX_TERM_LENGTH + 1);

  private static BiConsumer<FacetLabel, Document> immenseTermFor(FacetLabel rejected) {
    return (label, doc) -> {
      if (label.equals(rejected)) {
        doc.add(new StringField("note", IMMENSE, Field.Store.NO));
      }
    };
  }

  /** Writer subclass using the protected enrichment hook, with a vector-dimension mismatch. */
  private static final class VectorTaxoWriter extends DirectoryTaxonomyWriter {
    private final FacetLabel rejected;

    VectorTaxoWriter(Directory dir, FacetLabel rejected) throws IOException {
      super(dir);
      this.rejected = rejected;
    }

    @Override
    protected void enrichOrdinalDocument(Document d, FacetLabel categoryPath) {
      float[] v = categoryPath.equals(rejected) ? new float[] {1f, 2f, 3f} : new float[] {1f, 2f};
      d.add(new KnnFloatVectorField("emb", v));
    }
  }

  private static String describe(DirectoryTaxonomyReader tr, int ord) {
    try {
      return String.valueOf(tr.getPath(ord));
    } catch (RuntimeException e) {
      return e.getClass().getSimpleName() + ": " + e.getMessage();
    } catch (IOException e) {
      return e.toString();
    }
  }

  private static void assertReaderAgrees(Directory taxoDir, String label, int writerOrd)
      throws IOException {
    try (DirectoryTaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
      int readerOrd = tr.getOrdinal(new FacetLabel(label));
      String msg =
          "addCategory(["
              + label
              + "]) returned "
              + writerOrd
              + "; reader: getOrdinal(["
              + label
              + "])="
              + readerOrd
              + ", getPath("
              + writerOrd
              + ")="
              + describe(tr, writerOrd)
              + ", getSize()="
              + tr.getSize()
              + ", maxDoc="
              + tr.getInternalIndexReader().maxDoc();
      assertEquals(msg, writerOrd, readerOrd);
      assertEquals(msg, new FacetLabel(label), tr.getPath(writerOrd));
    }
  }

  /** Control: the same flow without any rejected category keeps writer and reader in agreement. */
  public void testControlNoRejection() throws Exception {
    try (Directory dir = newDirectory()) {
      int ordC;
      try (ReindexingEnrichedDirectoryTaxonomyWriter tw =
          new ReindexingEnrichedDirectoryTaxonomyWriter(
              dir, immenseTermFor(new FacetLabel("never-added")))) {
        tw.addCategory(new FacetLabel("a"));
        tw.addCategory(new FacetLabel("b"));
        ordC = tw.addCategory(new FacetLabel("c"));
        tw.commit();
      }
      assertReaderAgrees(dir, "c", ordC);
    }
  }

  /** Immense enrichment term (ReindexingEnrichedDirectoryTaxonomyWriter, public API). */
  public void testImmenseTermEnrichmentShiftsLaterOrdinals() throws Exception {
    try (Directory dir = newDirectory()) {
      FacetLabel bad = new FacetLabel("b");
      int ordA, ordC;
      try (ReindexingEnrichedDirectoryTaxonomyWriter tw =
          new ReindexingEnrichedDirectoryTaxonomyWriter(dir, immenseTermFor(bad))) {
        ordA = tw.addCategory(new FacetLabel("a"));
        expectThrows(IllegalArgumentException.class, () -> tw.addCategory(bad));
        ordC = tw.addCategory(new FacetLabel("c"));
        tw.commit();
      }
      assertReaderAgrees(dir, "a", ordA); // before the rejection: fine
      assertReaderAgrees(dir, "c", ordC); // after the rejection
    }
  }

  /** Vector dimension mismatch via the protected enrichOrdinalDocument hook. */
  public void testVectorDimensionEnrichmentShiftsLaterOrdinals() throws Exception {
    try (Directory dir = newDirectory()) {
      FacetLabel bad = new FacetLabel("b");
      int ordC;
      try (VectorTaxoWriter tw = new VectorTaxoWriter(dir, bad)) {
        tw.addCategory(new FacetLabel("a"));
        expectThrows(IllegalArgumentException.class, () -> tw.addCategory(bad));
        ordC = tw.addCategory(new FacetLabel("c"));
        tw.commit();
      }
      assertReaderAgrees(dir, "c", ordC);
    }
  }

  /**
   * TaxonomyWriter.addCategory: "If the category was already present in the taxonomy, its existing
   * ordinal is returned." After reopening the writer, the same category must get the same ordinal
   * it got in the session that created it.
   */
  public void testSameCategoryGetsDifferentOrdinalAfterReopen() throws Exception {
    try (Directory dir = newDirectory()) {
      FacetLabel bad = new FacetLabel("b");
      int ordCSession1;
      try (ReindexingEnrichedDirectoryTaxonomyWriter tw =
          new ReindexingEnrichedDirectoryTaxonomyWriter(dir, immenseTermFor(bad))) {
        tw.addCategory(new FacetLabel("a"));
        expectThrows(IllegalArgumentException.class, () -> tw.addCategory(bad));
        ordCSession1 = tw.addCategory(new FacetLabel("c"));
        tw.commit();
      }
      int ordCSession2;
      try (ReindexingEnrichedDirectoryTaxonomyWriter tw =
          new ReindexingEnrichedDirectoryTaxonomyWriter(dir, immenseTermFor(bad))) {
        ordCSession2 = tw.addCategory(new FacetLabel("c"));
      }
      assertEquals(
          "ordinal of existing category [c]: session 1 vs reopened writer",
          ordCSession1,
          ordCSession2);
    }
  }

  /** End to end: main-index documents faceted on categories added after the rejected one. */
  public void testFacetCountsAfterRejectedCategory() throws Exception {
    try (Directory taxoDir = newDirectory();
        Directory indexDir = newDirectory()) {
      FacetsConfig config = new FacetsConfig();
      FacetLabel bad = new FacetLabel("color", "bad");
      try (ReindexingEnrichedDirectoryTaxonomyWriter tw =
              new ReindexingEnrichedDirectoryTaxonomyWriter(taxoDir, immenseTermFor(bad));
          IndexWriter iw = new IndexWriter(indexDir, new IndexWriterConfig())) {
        Document red = new Document();
        red.add(new FacetField("color", "red"));
        iw.addDocument(config.build(tw, red));

        Document rejected = new Document();
        rejected.add(new FacetField("color", "bad"));
        expectThrows(IllegalArgumentException.class, () -> config.build(tw, rejected));

        for (int i = 0; i < 2; i++) {
          Document blue = new Document();
          blue.add(new FacetField("color", "blue"));
          iw.addDocument(config.build(tw, blue));
        }
        tw.commit();
        iw.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(indexDir);
          DirectoryTaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
        IndexSearcher s = new IndexSearcher(r);
        FacetsCollector fc = s.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        Facets facets = new FastTaxonomyFacetCounts(tr, config, fc);
        FacetResult res = facets.getTopChildren(10, "color");
        Map<String, Number> got = new HashMap<>();
        for (LabelAndValue lv : res.labelValues) {
          got.put(lv.label, lv.value);
        }
        Map<String, Number> expected = new HashMap<>();
        expected.put("red", 1);
        expected.put("blue", 2);
        assertEquals(expected, got);
      }
    }
  }
}
