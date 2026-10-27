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
package org.apache.lucene.search.matchhighlight;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The matchhighlight passage window is measured in UTF-16 units, and where no word boundary
 * shrinks it (a leading word longer than the window, or a match longer than the window) the raw
 * window edge is kept even when it falls between the two halves of a supplementary character.
 */
public class TestMatchHighlightWindowSurrogatePair extends LuceneTestCase {

  // MATHEMATICAL BOLD CAPITAL B, O, L, D (U+1D401, U+1D40E, U+1D40B, U+1D403): letters, two UTF-16
  // units each, one word for the word break iterator; often used as "bold" text in posts.
  private static final String BOLD = "\uD835\uDC01\uD835\uDC0E\uD835\uDC0B\uD835\uDC03";

  private static int unpairedSurrogate(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
        i++;
      } else if (Character.isSurrogate(c)) {
        return i;
      }
    }
    return -1;
  }

  private static String esc(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      sb.append(c < 0x20 || c > 0x7e ? String.format(Locale.ROOT, "\\u%04X", (int) c) : String.valueOf(c));
    }
    return sb.toString();
  }

  private static void assertWellFormed(List<String> snippets) {
    assertNotNull(snippets);
    assertFalse(snippets.isEmpty());
    for (String s : snippets) {
      int i = unpairedSurrogate(s);
      assertEquals("snippet has an unpaired surrogate at index " + i + ": '" + esc(s) + "'", -1, i);
    }
  }

  private List<String> highlight(
      MatchHighlighter.FieldValueHighlighter fvh, Query query, String value) throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    ft.freeze();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new Field("body", value, ft));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        TopDocs topDocs = searcher.search(query, 10);
        assertEquals(1, topDocs.scoreDocs.length);
        MatchHighlighter highlighter =
            new MatchHighlighter(searcher, analyzer).appendFieldHighlighter(fvh);
        return highlighter.highlight(topDocs, query).findFirst().orElseThrow().fields.get("body");
      }
    }
  }

  /** FieldValueHighlighters.maxLeadingCharacters(5): the window [0, 5) ends inside the third letter. */
  public void testMaxLeadingCharacters() throws Exception {
    List<String> snippets =
        highlight(
            FieldValueHighlighters.maxLeadingCharacters(5, "...", Set.of("body")),
            new MatchAllDocsQuery(),
            BOLD + " text");
    assertWellFormed(snippets); // actual: [MATH B, MATH O, U+D835, "..."]
  }

  /** FieldValueHighlighters.highlighted(5, ...): the 8-unit match is cut to a 5-unit marker. */
  public void testHighlightedMatchLongerThanWindow() throws Exception {
    List<String> snippets =
        highlight(
            FieldValueHighlighters.highlighted(
                5, 1, new PassageFormatter("...", ">", "<"), f -> f.equals("body")),
            new TermQuery(new Term("body", BOLD)),
            BOLD + " text");
    assertWellFormed(snippets); // actual: [">" MATH B, MATH O, U+D835 "<..."]
  }
}
