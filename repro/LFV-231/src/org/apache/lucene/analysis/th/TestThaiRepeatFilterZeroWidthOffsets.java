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
package org.apache.lucene.analysis.th;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.io.Reader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.charfilter.HTMLStripCharFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * ThaiRepeatFilter derives the offsets of the repeated token by counting the trailing Maiyamok
 * (U+0E46) characters back from the token's end offset. In ThaiAnalyzer the ThaiCharFilter removes
 * zero-width characters before tokenization, and the removed character that directly follows a
 * Maiyamok is attributed to the end offset of the token. The repeated token then points at the
 * zero-width character and the base token covers the Maiyamok. HTMLStripCharFilter (removed markup)
 * has the same effect.
 */
public class TestThaiRepeatFilterZeroWidthOffsets extends BaseTokenStreamTestCase {

  // "บ่อยๆ" (often, reduplicated) followed by a ZERO WIDTH SPACE word separator and "ครับ"
  private static final String TEXT = "บ่อยๆ\u200Bครับ";

  private static List<int[]> offsets(Analyzer a, String text, List<String> terms)
      throws IOException {
    List<int[]> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
      OffsetAttribute o = ts.addAttribute(OffsetAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        terms.add(t.toString());
        out.add(new int[] {o.startOffset(), o.endOffset()});
      }
      ts.end();
    }
    return out;
  }

  /** Without the zero-width space the offsets are exact (control). */
  public void testControlWithoutZeroWidthSpace() throws IOException {
    assumeTrue("needs the Thai BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
    try (Analyzer a = new ThaiAnalyzer()) {
      assertAnalyzesTo(
          a,
          "บ่อยๆครับ",
          new String[] {"บ่อย", "บ่อย", "ครับ"},
          new int[] {0, 4, 5},
          new int[] {4, 5, 9});
    }
  }

  /** ThaiAnalyzer (default configuration): the repeated token must cover the Maiyamok. */
  public void testZeroWidthSpaceAfterMaiyamok() throws IOException {
    assumeTrue("needs the Thai BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
    try (Analyzer a = new ThaiAnalyzer()) {
      List<String> terms = new ArrayList<>();
      List<int[]> offs = offsets(a, TEXT, terms);
      assertEquals(List.of("บ่อย", "บ่อย", "ครับ"), terms);
      String repeat = TEXT.substring(offs.get(1)[0], offs.get(1)[1]);
      assertTrue(
          "repeated token must cover the Maiyamok: " + describe(TEXT, offs),
          repeat.indexOf(ThaiRepeatFilter.MAIYAMOK) >= 0);
    }
  }

  /**
   * Same root cause with HTMLStripCharFilter: the removed markup after the Maiyamok is attributed
   * to the token's end offset, so the repeated token points into the markup.
   */
  public void testHtmlStripCharFilterBeforeThaiRepeatFilter() throws IOException {
    assumeTrue("needs the Thai BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
    String html = "บ่อยๆ</b>ครับ"; // the markup "</b>" is at [5,9)
    try (Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new ThaiTokenizer();
            return new TokenStreamComponents(t, new ThaiRepeatFilter(t));
          }

          @Override
          protected Reader initReader(String fieldName, Reader reader) {
            return new HTMLStripCharFilter(reader);
          }
        }) {
      List<String> terms = new ArrayList<>();
      List<int[]> offs = offsets(a, html, terms);
      assertEquals(List.of("บ่อย", "บ่อย", "ครับ"), terms);
      String repeat = html.substring(offs.get(1)[0], offs.get(1)[1]);
      assertTrue(
          "repeated token must cover the Maiyamok: " + describe(html, offs),
          repeat.indexOf(ThaiRepeatFilter.MAIYAMOK) >= 0);
    }
  }

  /**
   * User-visible effect with HTML content: the highlighter puts its markup inside the closing tag
   * "</i>" that follows the Maiyamok, because the repeated token's offsets point into the tag.
   */
  public void testHighlightOfHtmlContent() throws IOException {
    assumeTrue("needs the Thai BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
    String html = "<p>ทำงาน<i>บ่อยๆ</i>ครับ</p>";
    try (Analyzer a =
            new Analyzer() {
              @Override
              protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer t = new ThaiTokenizer();
                return new TokenStreamComponents(t, new ThaiRepeatFilter(t));
              }

              @Override
              protected Reader initReader(String fieldName, Reader reader) {
                return new HTMLStripCharFilter(reader);
              }
            };
        Directory dir = newDirectory()) {
      FieldType ft = new FieldType(TextField.TYPE_STORED);
      ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
      ft.freeze();
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new Field("f", html, ft));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(r);
        Query q = new TermQuery(new Term("f", "บ่อย"));
        TopDocs hits = searcher.search(q, 10);
        assertEquals(1, hits.totalHits.value());
        UnifiedHighlighter uh = UnifiedHighlighter.builder(searcher, a).build();
        String snippet = uh.highlight("f", q, hits)[0];
        assertTrue("closing tag </i> broken by the highlight: " + snippet, snippet.contains("</i>"));
      }
    }
  }

  private static String describe(String text, List<int[]> offs) {
    StringBuilder b = new StringBuilder();
    for (int[] o : offs) {
      b.append('[')
          .append(o[0])
          .append(',')
          .append(o[1])
          .append(")=\"")
          .append(text.substring(o[0], o[1]).replace("\u200B", "<ZWSP>"))
          .append("\" ");
    }
    return b.toString().trim();
  }

  /** User-visible effect: a phrase match on the repeated word does not highlight the Maiyamok. */
  public void testHighlightOfRepeatedWord() throws IOException {
    assumeTrue("needs the Thai BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
    try (Analyzer a = new ThaiAnalyzer();
        Directory dir = newDirectory()) {
      FieldType ft = new FieldType(TextField.TYPE_STORED);
      ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
      ft.freeze();
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new Field("f", TEXT, ft));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(r);
        // positions: 0 = base "บ่อย", 1 = repeated "บ่อย" (from the Maiyamok), 2 = "ครับ"
        Query q = new PhraseQuery("f", "บ่อย", "ครับ");
        TopDocs hits = searcher.search(q, 10);
        assertEquals(1, hits.totalHits.value());
        UnifiedHighlighter uh = UnifiedHighlighter.builder(searcher, a).build();
        String snippet = uh.highlight("f", q, hits)[0];
        // the repeated word is written as the Maiyamok, so the Maiyamok must be inside the highlight
        int mai = snippet.indexOf(ThaiRepeatFilter.MAIYAMOK);
        boolean inside =
            mai >= 0 && snippet.lastIndexOf("<b>", mai) > snippet.lastIndexOf("</b>", mai);
        assertTrue("Maiyamok not highlighted: " + snippet.replace("\u200B", "<ZWSP>"), inside);
      }
    }
  }
}
