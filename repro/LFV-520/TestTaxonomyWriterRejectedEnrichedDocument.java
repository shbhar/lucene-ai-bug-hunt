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

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * An application adds its own fields to the taxonomy's ordinal documents (the enrichment hook of
 * {@link ReindexingEnrichedDirectoryTaxonomyWriter}). IndexWriter rejects one of these documents
 * with a non-aborting exception; the application skips that category and keeps going. Categories
 * added afterwards must still get ordinals that a taxonomy reader and a reopened writer agree with.
 *
 * <p>A fix may instead close the writer after such a rejection; the tests then open a new writer,
 * as an application would after an {@link AlreadyClosedException}.
 */
public class TestTaxonomyWriterRejectedEnrichedDocument extends LuceneTestCase {

  /** One byte longer than the longest term IndexWriter accepts. */
  private static final String TOO_LONG = "x".repeat(IndexWriter.MAX_TERM_LENGTH + 1);

  /** Adds a "description" term to every category; the one for "rejected" or "bad" is too long. */
  private static final BiConsumer<FacetLabel, Document> DESCRIPTION =
      (label, doc) -> {
        if (label.length > 0) {
          String last = label.components[label.length - 1];
          boolean bad = last.equals("rejected") || last.equals("bad");
          doc.add(new StringField("description", bad ? TOO_LONG : "ok", Field.Store.NO));
        }
      };

  /** Adds a 2-dimensional vector to every category, and a 3-dimensional one for "rejected". */
  private static final BiConsumer<FacetLabel, Document> EMBEDDING =
      (label, doc) -> {
        if (label.length > 0) {
          boolean bad = label.components[0].equals("rejected");
          doc.add(new KnnFloatVectorField("embedding", bad ? new float[3] : new float[2]));
        }
      };

  private static DirectoryTaxonomyWriter openWriter(
      Directory dir, BiConsumer<FacetLabel, Document> appender) throws IOException {
    return new ReindexingEnrichedDirectoryTaxonomyWriter(dir, appender);
  }

  /** Returns the writer if it is still open, else a new writer on the same directory. */
  private static DirectoryTaxonomyWriter openIfClosed(
      DirectoryTaxonomyWriter tw, Directory dir, BiConsumer<FacetLabel, Document> appender)
      throws IOException {
    try {
      tw.getSize();
      return tw;
    } catch (AlreadyClosedException _) {
      return openWriter(dir, appender);
    }
  }

  /** Adds "a" (committed), then "rejected", which IndexWriter refuses, then "b". */
  private static int addAfterRejection(Directory dir, BiConsumer<FacetLabel, Document> appender)
      throws IOException {
    DirectoryTaxonomyWriter tw = openWriter(dir, appender);
    tw.addCategory(new FacetLabel("a"));
    tw.commit();
    expectThrows(IllegalArgumentException.class, () -> tw.addCategory(new FacetLabel("rejected")));
    DirectoryTaxonomyWriter writer = openIfClosed(tw, dir, appender);
    int b = writer.addCategory(new FacetLabel("b"));
    writer.close();
    return b;
  }

  private static void assertReaderAgrees(Directory dir, String label, int ordinal)
      throws IOException {
    try (DirectoryTaxonomyReader reader = new DirectoryTaxonomyReader(dir)) {
      FacetLabel expected = new FacetLabel(label);
      String atOrdinal;
      try {
        atOrdinal = String.valueOf(reader.getPath(ordinal));
      } catch (RuntimeException e) {
        atOrdinal = e.getClass().getSimpleName();
      }
      String msg =
          "addCategory("
              + expected
              + ") returned "
              + ordinal
              + "; reader: ordinal "
              + ordinal
              + " is "
              + atOrdinal
              + ", size "
              + reader.getSize()
              + ", maxDoc "
              + reader.getInternalIndexReader().maxDoc()
              + "; reader's ordinal for "
              + expected;
      assertEquals(msg, ordinal, reader.getOrdinal(expected));
      assertEquals(msg, expected, reader.getPath(ordinal));
    }
  }

  /** TaxonomyWriter.addCategory: an existing category's ordinal is returned. */
  public void testExistingCategoryKeepsItsOrdinalAfterReopen() throws Exception {
    try (Directory dir = newDirectory()) {
      int first = addAfterRejection(dir, DESCRIPTION);
      try (DirectoryTaxonomyWriter reopened = new DirectoryTaxonomyWriter(dir)) {
        assertEquals(
            "ordinal of the existing category [b]: first writer vs reopened writer",
            first,
            reopened.addCategory(new FacetLabel("b")));
      }
    }
  }

  public void testReaderAgreesAfterRejectedTerm() throws Exception {
    try (Directory dir = newDirectory()) {
      int b = addAfterRejection(dir, DESCRIPTION);
      assertReaderAgrees(dir, "b", b);
    }
  }

  public void testReaderAgreesAfterRejectedVector() throws Exception {
    try (Directory dir = newDirectory()) {
      int b = addAfterRejection(dir, EMBEDDING);
      assertReaderAgrees(dir, "b", b);
    }
  }

  /** End to end: facet counts after one document whose category was rejected. */
  public void testFacetCountsAfterRejectedCategory() throws Exception {
    assertEquals(new TreeMap<>(Map.of("x", 1, "y", 2)), countColors(true));
  }

  /** Control: the same documents without the rejected one. */
  public void testControlFacetCountsWithoutRejection() throws Exception {
    assertEquals(new TreeMap<>(Map.of("x", 1, "y", 2)), countColors(false));
  }

  /** Control: an exception thrown by the appender itself, before indexing, is harmless. */
  public void testExceptionInsideAppenderIsHarmless() throws Exception {
    BiConsumer<FacetLabel, Document> validating =
        (label, doc) -> {
          if (label.length > 0 && label.components[0].equals("rejected")) {
            throw new IllegalArgumentException("description too long");
          }
          DESCRIPTION.accept(label, doc);
        };
    try (Directory dir = newDirectory()) {
      int b = addAfterRejection(dir, validating);
      assertReaderAgrees(dir, "b", b);
    }
  }

  private Map<String, Integer> countColors(boolean withRejected) throws IOException {
    try (Directory taxoDir = newDirectory();
        Directory indexDir = newDirectory()) {
      FacetsConfig config = new FacetsConfig();
      DirectoryTaxonomyWriter tw = openWriter(taxoDir, DESCRIPTION);
      try (IndexWriter iw = new IndexWriter(indexDir, newIndexWriterConfig())) {
        iw.addDocument(config.build(tw, facetDoc("color", "x")));
        tw.commit();
        iw.commit();
        if (withRejected) {
          // the description of color/bad is too long: the application skips this document
          DirectoryTaxonomyWriter before = tw;
          expectThrows(
              IllegalArgumentException.class, () -> config.build(before, facetDoc("color", "bad")));
          tw = openIfClosed(tw, taxoDir, DESCRIPTION);
        }
        for (String[] f :
            new String[][] {{"color", "y"}, {"color", "y"}, {"size", "s"}, {"size", "t"}}) {
          iw.addDocument(config.build(tw, facetDoc(f[0], f[1])));
        }
        tw.close();
      }
      try (DirectoryReader reader = DirectoryReader.open(indexDir);
          DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir)) {
        FacetsCollector fc =
            new IndexSearcher(reader)
                .search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
        FacetResult result =
            new FastTaxonomyFacetCounts(taxoReader, config, fc).getTopChildren(10, "color");
        Map<String, Integer> counts = new TreeMap<>();
        for (LabelAndValue lv : result.labelValues) {
          counts.put(lv.label, lv.value.intValue());
        }
        return counts;
      }
    }
  }

  private static Document facetDoc(String dim, String value) {
    Document doc = new Document();
    doc.add(new FacetField(dim, value));
    return doc;
  }
}
