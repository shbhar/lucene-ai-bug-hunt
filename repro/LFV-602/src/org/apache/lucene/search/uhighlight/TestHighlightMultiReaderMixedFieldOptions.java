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
package org.apache.lucene.search.uhighlight;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.matchhighlight.MatchRegionRetriever;
import org.apache.lucene.search.matchhighlight.OffsetRange;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A MultiReader over two indexes is searchable when the same field was indexed with different
 * options in each index (search is per segment). Highlighting the hits of that MultiReader fails:
 * UnifiedHighlighter's default offset-source choice and MatchRegionRetriever's default strategies call
 * FieldInfos.getMergedFieldInfos, which applies the single-index field-consistency check to the
 * whole composite reader and throws IllegalArgumentException, even when only norms differ, or when
 * only an unrelated field differs.
 */
public class TestHighlightMultiReaderMixedFieldOptions extends LuceneTestCase {

  private static final Analyzer ANALYZER = new StandardAnalyzer();

  private static FieldType bodyType(boolean norms, boolean offsets) {
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setOmitNorms(norms == false);
    if (offsets) {
      ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    }
    ft.freeze();
    return ft;
  }

  /** One single-document index: body text plus an optional extra field. */
  private static Directory index(FieldType bodyType, String body, Field extra) throws IOException {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(ANALYZER))) {
      Document doc = new Document();
      doc.add(new Field("body", body, bodyType));
      if (extra != null) {
        doc.add(extra);
      }
      w.addDocument(doc);
    }
    return dir;
  }

  /** Highlights body:alpha over MultiReader(index1, index2); both documents contain alpha. */
  private static String[] highlightAlpha(Directory d1, Directory d2) throws IOException {
    try (DirectoryReader r1 = DirectoryReader.open(d1);
        DirectoryReader r2 = DirectoryReader.open(d2);
        MultiReader mr = new MultiReader(new IndexReader[] {r1, r2}, false)) {
      IndexSearcher searcher = new IndexSearcher(mr);
      Query q = new TermQuery(new Term("body", "alpha"));
      TopDocs td = searcher.search(q, 10);
      assertEquals("search over the MultiReader works", 2, td.totalHits.value());
      UnifiedHighlighter uh = UnifiedHighlighter.builder(searcher, ANALYZER).build();
      return uh.highlight("body", q, td);
    }
  }

  private static void assertBothHighlighted(String[] snippets) {
    assertEquals(2, snippets.length);
    for (String s : snippets) {
      assertNotNull(s);
      assertTrue(s, s.contains("<b>alpha</b>"));
    }
  }

  /** Control: same options (offsets in the postings) in both indexes. */
  public void testControlConsistentOptions() throws IOException {
    try (Directory d1 = index(bodyType(true, true), "alpha beta", null);
        Directory d2 = index(bodyType(true, true), "alpha gamma", null)) {
      assertBothHighlighted(highlightAlpha(d1, d2));
    }
  }

  /**
   * Both indexes index body with offsets in the postings (what highlight(field, query, topDocs)
   * asks for); they differ only in whether body has norms.
   */
  public void testNormsDifferOnly() throws IOException {
    try (Directory d1 = index(bodyType(true, true), "alpha beta", null);
        Directory d2 = index(bodyType(false, true), "alpha gamma", null)) {
      String[] snippets;
      try {
        snippets = highlightAlpha(d1, d2);
      } catch (IllegalArgumentException e) {
        throw new AssertionError("highlighting a searchable MultiReader threw: " + e.getMessage(), e);
      }
      assertBothHighlighted(snippets);
    }
  }

  /**
   * One index stores offsets in the postings (the highlighter's fast path), the other does not: the
   * highlighter's documented fallback for a field without offsets is re-analysis (ANALYSIS).
   */
  public void testOffsetsInOneIndexOnly() throws IOException {
    try (Directory d1 = index(bodyType(true, true), "alpha beta", null);
        Directory d2 = index(bodyType(true, false), "alpha gamma", null)) {
      String[] snippets;
      try {
        snippets = highlightAlpha(d1, d2);
      } catch (IllegalArgumentException e) {
        throw new AssertionError("highlighting a searchable MultiReader threw: " + e.getMessage(), e);
      }
      assertBothHighlighted(snippets);
    }
  }

  /** body (with offsets) is identical; only an unrelated field (tag) differs between the indexes. */
  public void testUnrelatedFieldDiffers() throws IOException {
    try (Directory d1 =
            index(bodyType(true, true), "alpha beta", new StringField("tag", "x", Field.Store.NO));
        Directory d2 =
            index(
                bodyType(true, true),
                "alpha gamma",
                new SortedDocValuesField("tag", new BytesRef("x")))) {
      String[] snippets;
      try {
        snippets = highlightAlpha(d1, d2);
      } catch (IllegalArgumentException e) {
        throw new AssertionError("highlighting a searchable MultiReader threw: " + e.getMessage(), e);
      }
      assertBothHighlighted(snippets);
    }
  }

  /** MatchRegionRetriever (the matchhighlight module) over the same MultiReader (norms differ only). */
  public void testMatchRegionRetrieverNormsDifferOnly() throws IOException {
    try (Directory d1 = index(bodyType(true, false), "alpha beta", null);
        Directory d2 = index(bodyType(false, false), "alpha gamma", null);
        DirectoryReader r1 = DirectoryReader.open(d1);
        DirectoryReader r2 = DirectoryReader.open(d2);
        MultiReader mr = new MultiReader(new IndexReader[] {r1, r2}, false)) {
      IndexSearcher searcher = new IndexSearcher(mr);
      Query q = new TermQuery(new Term("body", "alpha"));
      TopDocs td = searcher.search(q, 10);
      assertEquals(2, td.totalHits.value());
      MatchRegionRetriever retriever;
      try {
        retriever = new MatchRegionRetriever(searcher, searcher.rewrite(q), ANALYZER, _ -> false, _ -> true);
      } catch (IllegalArgumentException e) {
        throw new AssertionError(
            "MatchRegionRetriever on a searchable MultiReader threw: " + e.getMessage(), e);
      }
      Map<Integer, List<OffsetRange>> regions = new TreeMap<>();
      retriever.highlightDocuments(
          td, (docId, leaf, leafDocId, values, hits) -> regions.put(docId, hits.get("body")));
      assertEquals(regions.toString(), 2, regions.size());
      for (List<OffsetRange> r : regions.values()) {
        assertEquals(regions.toString(), 1, r.size());
        assertEquals(0, r.get(0).from);
        assertEquals(5, r.get(0).to);
      }
    }
  }
}
