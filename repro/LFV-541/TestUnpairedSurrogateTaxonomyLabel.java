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
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent verifier test. Only public API is used (DirectoryTaxonomyWriter/Reader, FacetLabel,
 * FacetField, FacetsConfig, FastTaxonomyFacetCounts); the package is only for grouping.
 *
 * <p>"\uD83D" is the high surrogate of U+1F600 without its low surrogate, i.e. what a value cut
 * inside an emoji looks like.
 */
public class TestUnpairedSurrogateTaxonomyLabel extends LuceneTestCase {

  private static final String ILL_FORMED = "x\uD83D"; // unpaired high surrogate
  private static final String WELL_FORMED_PAIR = "x\uD83D\uDE00"; // control: complete pair

  /** Adds enough distinct new categories that the writer's default cache-fill threshold passes. */
  private static void addFillers(DirectoryTaxonomyWriter tw, String dim, int n) throws Exception {
    for (int i = 0; i < n; i++) {
      tw.addCategory(new FacetLabel(dim, "v" + i));
    }
  }

  private int ordinalAcrossSessions(String value, boolean fillBeforeReAdd) throws Exception {
    try (Directory dir = newDirectory()) {
      int first;
      try (DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir)) {
        first = tw.addCategory(new FacetLabel("dim", value));
        tw.commit();
      }
      int second;
      try (DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir)) {
        if (fillBeforeReAdd) {
          addFillers(tw, "other", 20);
        }
        second = tw.addCategory(new FacetLabel("dim", value));
        tw.commit();
      }
      return second - first; // 0 when the existing ordinal was returned
    }
  }

  /** Control: a well-formed label keeps its ordinal across sessions, also after the cache fill. */
  public void testControlWellFormedLabelKeepsOrdinal() throws Exception {
    assertEquals(0, ordinalAcrossSessions(WELL_FORMED_PAIR, true));
    assertEquals(0, ordinalAcrossSessions(WELL_FORMED_PAIR, false));
  }

  /** Control: before the cache is filled, the term lookup finds the existing ordinal. */
  public void testControlIllFormedLabelBeforeCacheFill() throws Exception {
    assertEquals(0, ordinalAcrossSessions(ILL_FORMED, false));
  }

  /**
   * TaxonomyWriter.addCategory: "If the category was already present in the taxonomy, its existing
   * ordinal is returned." Same label, second writer session, default writer and cache, after the
   * cache has been filled from the index.
   */
  public void testIllFormedLabelKeepsOrdinalAfterCacheFill() throws Exception {
    try (Directory dir = newDirectory()) {
      FacetLabel label = new FacetLabel("dim", ILL_FORMED);
      int first;
      try (DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir)) {
        first = tw.addCategory(label);
        tw.commit();
      }
      int second;
      try (DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir)) {
        addFillers(tw, "other", 20);
        second = tw.addCategory(label);
        tw.commit();
      }
      try (DirectoryTaxonomyReader tr = new DirectoryTaxonomyReader(dir)) {
        int sameLabelOrds = 0;
        for (int ord = 1; ord < tr.getSize(); ord++) {
          FacetLabel p = tr.getPath(ord);
          if (p.length == 2 && p.components[0].equals("dim")) {
            sameLabelOrds++;
          }
        }
        assertEquals(
            "ordinal of the same label in session 2 (first="
                + first
                + ", second="
                + second
                + ", reader.getOrdinal="
                + tr.getOrdinal(label)
                + ", dim children in taxonomy="
                + sameLabelOrds
                + ")",
            first,
            second);
      }
    }
  }

  /**
   * Public faceting path: the same facet value indexed in two writer sessions must be counted as one
   * facet entry with count 2.
   */
  public void testFacetCountsForSameValueAcrossSessions() throws Exception {
    try (Directory indexDir = newDirectory();
        Directory taxoDir = newDirectory()) {
      FacetsConfig config = new FacetsConfig();
      for (int session = 0; session < 2; session++) {
        try (IndexWriter iw = new IndexWriter(indexDir, new IndexWriterConfig());
            DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
          if (session == 1) {
            for (int i = 0; i < 20; i++) {
              Document filler = new Document();
              filler.add(new FacetField("other", "v" + i));
              iw.addDocument(config.build(tw, filler));
            }
          }
          Document doc = new Document();
          doc.add(new FacetField("dim", ILL_FORMED));
          iw.addDocument(config.build(tw, doc));
          tw.commit();
          iw.commit();
        }
      }
      try (DirectoryReader r = DirectoryReader.open(indexDir);
          TaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
        IndexSearcher s = newSearcher(r);
        FacetsCollector fc = s.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        Facets facets = new FastTaxonomyFacetCounts(tr, config, fc);
        FacetResult res = facets.getTopChildren(10, "dim");
        assertNotNull(res);
        assertEquals("dim facet result: " + res, 1, res.childCount);
        assertEquals("dim facet result: " + res, 2, res.labelValues[0].value.intValue());
      }
    }
  }

  /**
   * Single fresh session: the ordinal the writer returns for the well-formed label "x\uFFFD" must
   * be the ordinal a reader reports for that label (TaxonomyReader.getOrdinal).
   */
  public void testWriterAndReaderAgreeOnWellFormedReplacementCharLabel() throws Exception {
    try (Directory dir = newDirectory()) {
      FacetLabel ill = new FacetLabel("dim", ILL_FORMED);
      FacetLabel replacement = new FacetLabel("dim", "x\uFFFD");
      int ordIll;
      int ordReplacement;
      try (DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir)) {
        ordIll = tw.addCategory(ill);
        ordReplacement = tw.addCategory(replacement);
        tw.commit();
      }
      try (DirectoryTaxonomyReader tr = new DirectoryTaxonomyReader(dir)) {
        assertEquals(
            "writer ords: ill="
                + ordIll
                + " replacement="
                + ordReplacement
                + "; reader getPath(ill)="
                + tr.getPath(ordIll)
                + " getPath(replacement)="
                + tr.getPath(ordReplacement)
                + " size="
                + tr.getSize(),
            ordReplacement,
            tr.getOrdinal(replacement));
      }
    }
  }
}
