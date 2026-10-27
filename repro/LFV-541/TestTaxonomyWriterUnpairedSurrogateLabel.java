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
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.TaxonomyMergeUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A facet label that contains an unpaired surrogate, for example a value cut in the middle of an
 * emoji, is stored in the taxonomy index as UTF-8, where the surrogate becomes U+FFFD (see {@link
 * IndexWriter#addDocument}). The writer cache compares the label's Java string, so once
 * DirectoryTaxonomyWriter has filled its cache from the index, the label no longer finds its own
 * category and is added again under a new ordinal.
 *
 * <p>A writer that rejects such a label with an IllegalArgumentException also passes these tests.
 */
public class TestTaxonomyWriterUnpairedSurrogateLabel extends LuceneTestCase {

  /** "x" and the first half (high surrogate) of the emoji U+1F600: a value cut inside the emoji. */
  private static final String CUT = "x\uD83D";

  /** DirectoryTaxonomyWriter reads the whole taxonomy into its cache after this many misses. */
  private static final int MISSES_UNTIL_FILL = 11;

  /** Returns the label's ordinal, or -1 if the writer rejects the label. */
  private static int add(DirectoryTaxonomyWriter tw, String... path) throws IOException {
    try {
      return tw.addCategory(new FacetLabel(path));
    } catch (IllegalArgumentException _) {
      return -1;
    }
  }

  /**
   * Adds dim/value in a first writer session and again in a second one; with {@code fillFirst}, the
   * second session fills its cache from the index before it adds the value again.
   */
  private static int[] addInTwoSessions(Directory dir, String value, boolean fillFirst)
      throws IOException {
    int first;
    int second;
    try (DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir)) {
      first = add(tw, "dim", value);
      for (int i = 0; i < MISSES_UNTIL_FILL; i++) {
        add(tw, "dim", "v" + i);
      }
    }
    try (DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir)) {
      if (fillFirst) {
        for (int i = 0; i < MISSES_UNTIL_FILL; i++) {
          add(tw, "dim", "v" + i); // existing labels, but the new writer's cache is empty: misses
        }
      }
      second = add(tw, "dim", value);
    }
    return new int[] {first, second};
  }

  /**
   * TaxonomyWriter#addCategory: "If the category was already present in the taxonomy, its existing
   * ordinal is returned."
   */
  public void testSameLabelKeepsItsOrdinalAcrossWriterSessions() throws Exception {
    try (Directory dir = newDirectory()) {
      int[] ords = addInTwoSessions(dir, CUT, true);
      try (DirectoryTaxonomyReader reader = new DirectoryTaxonomyReader(dir)) {
        assertEquals(
            "ordinal of the cut value in session 1 vs session 2 (taxonomy size "
                + reader.getSize()
                + ")",
            ords[0],
            ords[1]);
      }
    }
  }

  /** The ordinal the writer returns for a label must be the one a taxonomy reader finds for it. */
  public void testWriterAndReaderAgreeInOneSession() throws Exception {
    try (Directory dir = newDirectory()) {
      String[] values = {CUT, "x\uFFFD"};
      int[] ords = new int[values.length];
      try (DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir)) {
        for (int i = 0; i < values.length; i++) {
          ords[i] = add(tw, "dim", values[i]);
        }
      }
      try (DirectoryTaxonomyReader reader = new DirectoryTaxonomyReader(dir)) {
        for (int i = 0; i < values.length; i++) {
          if (ords[i] >= 0) {
            int readerOrd = reader.getOrdinal(new FacetLabel("dim", values[i]));
            assertEquals(
                "value #"
                    + i
                    + ": addCategory returned "
                    + ords[i]
                    + ", the reader finds "
                    + readerOrd,
                ords[i],
                readerOrd);
          }
        }
      }
    }
  }

  /** Through FacetField: one value indexed in two writer sessions is one facet entry, count 2. */
  public void testFacetCountsForOneValueAcrossWriterSessions() throws Exception {
    FacetsConfig config = new FacetsConfig();
    try (Directory indexDir = newDirectory();
        Directory taxoDir = newDirectory()) {
      int added = 0;
      for (int session = 0; session < 2; session++) {
        try (IndexWriter iw = new IndexWriter(indexDir, newIndexWriterConfig());
            DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
          for (int i = 0; i < MISSES_UNTIL_FILL; i++) {
            index(iw, tw, config, "v" + i);
          }
          added += index(iw, tw, config, CUT);
          tw.commit(); // taxonomy first, then the index
          iw.commit();
        }
      }
      FacetResult result = dimCounts(indexDir, taxoDir, config);
      int[] value = entriesAndCount(result);
      assertEquals("facet entries for the value; got " + result, added > 0 ? 1 : 0, value[0]);
      assertEquals(added, value[1]);
    }
  }

  /** TaxonomyMergeUtils keeps the facet value of every document. */
  public void testTaxonomyMergeKeepsEveryDocumentsValue() throws Exception {
    FacetsConfig config = new FacetsConfig();
    try (Directory srcIndex = newDirectory();
        Directory srcTaxo = newDirectory();
        Directory dstIndex = newDirectory();
        Directory dstTaxo = newDirectory()) {
      int added = 0;
      try (IndexWriter iw = new IndexWriter(srcIndex, newIndexWriterConfig());
          DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(srcTaxo)) {
        // the emoji's first half, then its second half: the index stores both as "x" + U+FFFD
        added += index(iw, tw, config, "x\uD83D");
        added += index(iw, tw, config, "x\uDE00");
        tw.commit();
        iw.commit();
      }
      try (IndexWriter iw = new IndexWriter(dstIndex, newIndexWriterConfig());
          DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dstTaxo)) {
        TaxonomyMergeUtils.merge(
            srcIndex, srcTaxo, new DirectoryTaxonomyWriter.MemoryOrdinalMap(), iw, tw, config);
        tw.commit();
        iw.commit();
      }
      FacetResult merged = dimCounts(dstIndex, dstTaxo, config);
      assertEquals(
          "documents counted under the value after the merge; got " + merged,
          added,
          entriesAndCount(merged)[1]);
    }
  }

  /** Control: a well-formed emoji keeps its ordinal across the same two sessions. */
  public void testControlWellFormedEmojiKeepsItsOrdinal() throws Exception {
    try (Directory dir = newDirectory()) {
      int[] ords = addInTwoSessions(dir, "x\uD83D\uDE00", true);
      assertEquals(ords[0], ords[1]);
    }
  }

  /** Control: the cut value keeps its ordinal if it is added again before the cache fill. */
  public void testControlCutValueKeepsItsOrdinalBeforeCacheFill() throws Exception {
    try (Directory dir = newDirectory()) {
      int[] ords = addInTwoSessions(dir, CUT, false);
      assertEquals(ords[0], ords[1]);
    }
  }

  /** Indexes a document with FacetField("dim", value); returns 1, or 0 if the label is rejected. */
  private static int index(
      IndexWriter iw, DirectoryTaxonomyWriter tw, FacetsConfig config, String value)
      throws IOException {
    Document doc = new Document();
    doc.add(new FacetField("dim", value));
    try {
      iw.addDocument(config.build(tw, doc));
      return 1;
    } catch (IllegalArgumentException _) {
      return 0;
    }
  }

  private static FacetResult dimCounts(Directory indexDir, Directory taxoDir, FacetsConfig config)
      throws IOException {
    try (DirectoryReader reader = DirectoryReader.open(indexDir);
        DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir)) {
      FacetsCollector fc =
          new IndexSearcher(reader)
              .search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
      return new FastTaxonomyFacetCounts(taxoReader, config, fc).getTopChildren(100, "dim");
    }
  }

  /** {number of facet entries, total count} for the value, whose label starts with "x". */
  private static int[] entriesAndCount(FacetResult result) {
    int entries = 0;
    int count = 0;
    if (result != null) {
      for (LabelAndValue lv : result.labelValues) {
        if (lv.label.startsWith("x")) {
          entries++;
          count += lv.value.intValue();
        }
      }
    }
    return new int[] {entries, count};
  }
}
