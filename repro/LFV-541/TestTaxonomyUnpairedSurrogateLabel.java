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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DirectoryTaxonomyWriter identifies categories by their Java strings in its cache, but stores
 * them as UTF-8 terms, where an unpaired surrogate becomes U+FFFD (IndexWriter.addDocument: "the
 * invalid characters are silently replaced with the Unicode replacement character U+FFFD"). When
 * the writer later fills its cache from the index, the cached key no longer equals the label, so the
 * same label gets a second ordinal. TaxonomyWriter.addCategory: "If the category was already present
 * in the taxonomy, its existing ordinal is returned."
 *
 * <p>A label with an unpaired surrogate arises, for example, when a string is truncated in the
 * middle of a surrogate pair (an emoji). A writer that rejects such a label is acceptable here; one
 * that returns two ordinals for it, or ordinals the taxonomy reader maps elsewhere, is not.
 */
public class TestTaxonomyUnpairedSurrogateLabel extends LuceneTestCase {

  private static final String TRUNCATED_EMOJI = "x\uD83D"; // "x" + high surrogate of U+1F600

  private static int addOrRefused(DirectoryTaxonomyWriter tw, FacetLabel label)
      throws java.io.IOException {
    try {
      return tw.addCategory(label);
    } catch (IllegalArgumentException e) {
      return -1;
    }
  }

  /** Default writer and cache; the second session fills its cache after 11 misses (the default). */
  public void testSameLabelGetsSecondOrdinalAfterCacheFill() throws Exception {
    Directory dir = newDirectory();
    FacetLabel label = new FacetLabel("dim", TRUNCATED_EMOJI);
    int first;
    try (DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir)) {
      first = addOrRefused(tw, label);
      for (int i = 0; i < 11; i++) {
        tw.addCategory(new FacetLabel("dim", "v" + i));
      }
    }
    int second;
    try (DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir)) {
      for (int i = 0; i < 11; i++) { // 11 cache misses: the writer reads the taxonomy into its cache
        tw.addCategory(new FacetLabel("dim", "v" + i));
      }
      second = addOrRefused(tw, label);
    }
    if (first >= 0 || second >= 0) {
      try (DirectoryTaxonomyReader reader = new DirectoryTaxonomyReader(dir)) {
        assertEquals(
            "the same category must keep its ordinal across writer sessions (taxonomy size "
                + reader.getSize()
                + ")",
            first,
            second);
        assertEquals(first, reader.getOrdinal(label));
      }
    }
    dir.close();
  }

  /** Labels that the index stores as the same term must not get different ordinals. */
  public void testLabelsStoredAsSameTermGetDistinctOrdinals() throws Exception {
    Directory dir = newDirectory();
    String[] values = {TRUNCATED_EMOJI, "x\uFFFD", "x\uDE00"};
    int[] ords = new int[values.length];
    try (DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir)) {
      for (int i = 0; i < values.length; i++) {
        ords[i] = addOrRefused(tw, new FacetLabel("dim", values[i]));
      }
    }
    try (DirectoryTaxonomyReader reader = new DirectoryTaxonomyReader(dir)) {
      for (int i = 0; i < values.length; i++) {
        if (ords[i] < 0) {
          continue; // refused
        }
        FacetLabel label = new FacetLabel("dim", values[i]);
        assertEquals(
            "addCategory returned "
                + ords[i]
                + " for value #"
                + i
                + "; the taxonomy reader (size "
                + reader.getSize()
                + ") finds that label at ordinal "
                + reader.getOrdinal(label)
                + " and ordinal "
                + ords[i]
                + " has path "
                + reader.getPath(ords[i]),
            reader.getOrdinal(label),
            ords[i]);
      }
    }
    dir.close();
  }

  /**
   * The same through the public faceting API: two documents with the facet value "x" + high
   * surrogate, indexed in two writer sessions. Facet counting must report that value once with
   * count 2, as it does for a well-formed value.
   */
  public void testFacetCountsSplitAcrossWriterSessions() throws Exception {
    Directory indexDir = newDirectory();
    Directory taxoDir = newDirectory();
    FacetsConfig config = new FacetsConfig();
    for (int session = 0; session < 2; session++) {
      try (IndexWriter iw = new IndexWriter(indexDir, newIndexWriterConfig());
          DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
        for (int i = 0; i < 11; i++) { // session 2: 11 cache misses, then the cache is filled
          Document doc = new Document();
          doc.add(new FacetField("dim", "v" + i));
          iw.addDocument(config.build(tw, doc));
        }
        Document doc = new Document();
        doc.add(new FacetField("dim", TRUNCATED_EMOJI));
        iw.addDocument(config.build(tw, doc));
        tw.commit(); // taxonomy first, then the index
        iw.commit();
      }
    }
    try (DirectoryReader reader = DirectoryReader.open(indexDir);
        DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir)) {
      FacetsCollector fc =
          new IndexSearcher(reader).search(new MatchAllDocsQuery(), new FacetsCollectorManager());
      FacetResult result =
          new FastTaxonomyFacetCounts(taxoReader, config, fc).getTopChildren(100, "dim");
      int entries = 0;
      int count = 0;
      for (LabelAndValue lv : result.labelValues) {
        if (lv.label.startsWith("x")) {
          entries++;
          count += lv.value.intValue();
        }
      }
      assertEquals("the value must be one facet entry; got " + result, 1, entries);
      assertEquals(2, count);
    }
    indexDir.close();
    taxoDir.close();
  }
}
