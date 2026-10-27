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
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * UnifiedHighlighter truncates the stored value to maxLength UTF-16 units with no check for a
 * surrogate pair, so a supplementary character (here the emoji U+1F600) that
 * straddles the cut leaves an unpaired high surrogate at the end of the returned snippet.
 */
public class TestUHMaxLengthSurrogatePair extends LuceneTestCase {

  private static final String EMOJI = "\uD83D\uDE00"; // U+1F600 (an emoji), two UTF-16 units

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
      sb.append(c < 0x20 || c > 0x7e ? String.format(java.util.Locale.ROOT, "\\u%04X", (int) c) : String.valueOf(c));
    }
    return sb.toString();
  }

  private static FieldType offsetsInPostings() {
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    ft.freeze();
    return ft;
  }

  private String highlight(FieldType ft, int maxLength, String queryTerm, String... values)
      throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        Document doc = new Document();
        for (String v : values) {
          doc.add(new Field("body", v, ft));
        }
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        Query query = new TermQuery(new Term("body", queryTerm));
        TopDocs topDocs = searcher.search(query, 10);
        assertEquals(1, topDocs.scoreDocs.length);
        UnifiedHighlighter.Builder b = UnifiedHighlighter.builder(searcher, analyzer);
        if (maxLength >= 0) {
          b.withMaxLength(maxLength);
        }
        String[] snippets = b.build().highlight("body", query, topDocs);
        assertEquals(1, snippets.length);
        assertNotNull(snippets[0]);
        return snippets[0];
      }
    }
  }

  private static void assertWellFormed(String snippet) {
    int i = unpairedSurrogate(snippet);
    assertEquals(
        "snippet must not contain an unpaired surrogate, but has one at index "
            + i
            + ": '"
            + esc(snippet.length() > 60 ? "..." + snippet.substring(snippet.length() - 40) : snippet)
            + "'",
        -1,
        i);
  }

  /** maxLength = 7 cuts "Hello EMOJI world" between the two halves of the emoji. */
  public void testFirstValueCutInsideSurrogatePair() throws Exception {
    String snippet = highlight(offsetsInPostings(), 7, "hello", "Hello " + EMOJI + " world");
    assertTrue(snippet, snippet.startsWith("<b>Hello</b>"));
    assertWellFormed(snippet); // actual: "<b>Hello</b> U+D83D"
  }

  /** The same cut in a later value of a multi-valued field (the lengthBudget - 1 branch). */
  public void testLaterValueCutInsideSurrogatePair() throws Exception {
    // content = "Hello" + U+0000 + first 7 units of "world EMOJI again" = "HelloU+0000world U+D83D"
    String snippet = highlight(offsetsInPostings(), 13, "world", "Hello", "world " + EMOJI + " again");
    assertTrue(snippet, snippet.contains("<b>world</b>"));
    assertWellFormed(snippet); // actual: "<b>world</b> U+D83D"
  }

  /** The default maxLength (10000) with the emoji's first half at index 9999. */
  public void testDefaultMaxLength() throws Exception {
    StringBuilder sb = new StringBuilder();
    while (sb.length() < 9998) {
      sb.append("x ");
    }
    sb.setLength(9998);
    sb.append('y'); // 9999 chars
    String value = sb + EMOJI + " end";
    assertEquals(UnifiedHighlighter.DEFAULT_MAX_LENGTH - 1, value.indexOf(EMOJI));
    String snippet = highlight(offsetsInPostings(), -1, "y", value);
    assertTrue(snippet.contains("<b>y</b>"));
    assertWellFormed(snippet); // actual: ends with "<b>y</b>U+D83D"
  }

  /**
   * A plain stored TextField (no offsets indexed), which makes the highlighter re-analyze the
   * truncated content (OffsetSource.ANALYSIS, the default for such a field).
   */
  public void testReanalyzedFieldCutInsideSurrogatePair() throws Exception {
    String snippet = highlight(TextField.TYPE_STORED, 7, "hello", "Hello " + EMOJI + " world");
    assertWellFormed(snippet); // actual: "Hello U+D83D" (and no highlight at all, see the report)
  }

  /**
   * Everything at its default (a plain stored TextField, so OffsetSource.ANALYSIS, and the default
   * maxLength 10000): the truncated content ends in an unpaired high surrogate, and the match just
   * before the cut is not highlighted.
   */
  public void testDefaultsReanalyzedLongValue() throws Exception {
    StringBuilder sb = new StringBuilder();
    while (sb.length() < 9990) {
      sb.append("x ");
    }
    sb.setLength(9990);
    sb.append("needle xx"); // 9999 chars; "needle" starts at index 9990
    String value = sb + EMOJI + " end";
    assertEquals(UnifiedHighlighter.DEFAULT_MAX_LENGTH - 1, value.indexOf(EMOJI));
    String snippet = highlight(TextField.TYPE_STORED, -1, "needle", value);
    assertTrue(
        "the match lies inside the first 10000 chars but is not highlighted: '..."
            + esc(snippet.substring(Math.max(0, snippet.length() - 30)))
            + "'",
        snippet.contains("<b>needle</b>"));
    assertWellFormed(snippet);
  }
}
