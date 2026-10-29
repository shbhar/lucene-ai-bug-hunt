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
import java.nio.charset.StandardCharsets;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: UnifiedHighlighter truncates stored content to maxLength UTF-16 units. When
 * the unit just before the cut is the high half of a supplementary character, the content it
 * highlights (and so the snippet) ends with an unpaired high surrogate, although the stored value
 * was well-formed. Only public API is used (plus the protected getOffsetSource, reached from the
 * same package, to make sure each test exercises the intended offset source).
 */
public class TestMaxLengthCutsSurrogatePair extends LuceneTestCase {

  private static final String FIELD = "body";
  // U+1F600 GRINNING FACE = 😀 (two UTF-16 units)
  private static final String EMOJI = new String(Character.toChars(0x1F600));

  /** Index of the first unpaired surrogate in s, or -1 if s is well-formed UTF-16. */
  static int firstUnpairedSurrogate(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
          i++;
          continue;
        }
        return i;
      }
      if (Character.isLowSurrogate(c)) {
        return i;
      }
    }
    return -1;
  }

  static String esc(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < 0x20 || c > 0x7e) {
        sb.append(String.format("\\u%04X", (int) c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  static void assertWellFormed(String what, String snippet) {
    assertNotNull(what + ": snippet is null", snippet);
    int bad = firstUnpairedSurrogate(snippet);
    String roundTrip = new String(snippet.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    assertEquals(
        what
            + ": snippet must not contain an unpaired surrogate; snippet='"
            + esc(snippet)
            + "' (UTF-8 round trip='"
            + esc(roundTrip)
            + "')",
        -1,
        bad);
  }

  private interface FieldMaker {
    void add(Document doc, String value);
  }

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  private void index(Analyzer analyzer, FieldMaker maker, String... values) throws IOException {
    dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig(analyzer);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      Document doc = new Document();
      doc.add(new StringField("id", "1", Field.Store.NO));
      for (String v : values) {
        maker.add(doc, v);
      }
      w.addDocument(doc);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  private void close() throws IOException {
    reader.close();
    dir.close();
  }

  private String highlight(Analyzer analyzer, Integer maxLength, String term,
      UnifiedHighlighter.OffsetSource expectedSource) throws IOException {
    UnifiedHighlighter.Builder b = UnifiedHighlighter.builder(searcher, analyzer);
    if (maxLength != null) {
      b.withMaxLength(maxLength);
    }
    UnifiedHighlighter uh = b.build();
    if (maxLength == null) {
      assertEquals(UnifiedHighlighter.DEFAULT_MAX_LENGTH, uh.getMaxLength());
    }
    assertEquals(expectedSource, uh.getOffsetSource(FIELD));
    Query q = new TermQuery(new Term(FIELD, term));
    TopDocs td = searcher.search(q, 10);
    assertEquals(1, td.scoreDocs.length);
    String[] snippets = uh.highlight(FIELD, q, td);
    assertEquals(1, snippets.length);
    return snippets[0];
  }

  private static FieldType postingsOffsets() {
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    ft.freeze();
    return ft;
  }

  private static FieldType termVectorsOnly() {
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    ft.setStoreTermVectors(true);
    ft.setStoreTermVectorPositions(true);
    ft.setStoreTermVectorOffsets(true);
    ft.freeze();
    return ft;
  }

  private Analyzer mock() {
    return new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
  }

  /** POSTINGS offsets, single value, cut between the two halves of the emoji. */
  public void testPostingsFirstValue() throws IOException {
    Analyzer a = mock();
    FieldType ft = postingsOffsets();
    String value = "Hello " + EMOJI + " world"; // emoji at UTF-16 index 6..7
    index(a, (d, v) -> d.add(new Field(FIELD, v, ft)), value);
    try {
      UnifiedHighlighter.OffsetSource src = UnifiedHighlighter.OffsetSource.POSTINGS;
      // controls: cut before or after the emoji -> well-formed snippets with the match
      String s6 = highlight(a, 6, "hello", src);
      assertWellFormed("maxLength=6 (control)", s6);
      assertTrue(s6, s6.contains("<b>Hello</b>"));
      String s8 = highlight(a, 8, "hello", src);
      assertWellFormed("maxLength=8 (control)", s8);
      assertTrue(s8, s8.contains("<b>Hello</b>"));
      // cut inside the pair
      String s7 = highlight(a, 7, "hello", src);
      assertWellFormed("POSTINGS maxLength=7", s7);
    } finally {
      close();
      a.close();
    }
  }

  /** TERM_VECTORS offsets (no offsets in postings), single value. */
  public void testTermVectorsFirstValue() throws IOException {
    Analyzer a = mock();
    FieldType ft = termVectorsOnly();
    String value = "Hello " + EMOJI + " world";
    index(a, (d, v) -> d.add(new Field(FIELD, v, ft)), value);
    try {
      String s7 = highlight(a, 7, "hello", UnifiedHighlighter.OffsetSource.TERM_VECTORS);
      assertTrue(s7, s7.contains("<b>Hello</b>"));
      assertWellFormed("TERM_VECTORS maxLength=7", s7);
    } finally {
      close();
      a.close();
    }
  }

  /** Multi-valued field: the cut falls inside a pair of the SECOND value (lengthBudget - 1 path). */
  public void testPostingsSecondValue() throws IOException {
    Analyzer a = mock();
    FieldType ft = postingsOffsets();
    // content = "abc" + sep + first 7 units of "world <emoji> tail" = 3 + 1 + 7 = 11
    index(a, (d, v) -> d.add(new Field(FIELD, v, ft)), "abc", "world " + EMOJI + " tail");
    try {
      String s = highlight(a, 11, "world", UnifiedHighlighter.OffsetSource.POSTINGS);
      assertTrue(s, s.contains("<b>world</b>"));
      assertWellFormed("POSTINGS multi-valued maxLength=11", s);
    } finally {
      close();
      a.close();
    }
  }

  /** Default maxLength (10000): the 10000th UTF-16 unit is the high half of the emoji. */
  public void testDefaultMaxLength() throws IOException {
    Analyzer a = mock();
    FieldType ft = postingsOffsets();
    StringBuilder sb = new StringBuilder();
    while (sb.length() < 9990) {
      sb.append("x ");
    }
    sb.append("needle ");
    while (sb.length() < 9999) {
      sb.append('y');
    }
    assertEquals(9999, sb.length());
    sb.append(EMOJI).append(" tail");
    String value = sb.toString();
    assertTrue(Character.isHighSurrogate(value.charAt(9999)));
    index(a, (d, v) -> d.add(new Field(FIELD, v, ft)), value);
    try {
      String s = highlight(a, null, "needle", UnifiedHighlighter.OffsetSource.POSTINGS);
      assertTrue(s.contains("<b>needle</b>"));
      String tail = esc(s.substring(Math.max(0, s.length() - 30)));
      assertEquals(
          "POSTINGS default maxLength: snippet (length "
              + s.length()
              + ") must not contain an unpaired surrogate; tail='"
              + tail
              + "'",
          -1,
          firstUnpairedSurrogate(s));
    } finally {
      close();
      a.close();
    }
  }

  /**
   * ANALYSIS offset source (plain stored text field), analyzer that tolerates a lone surrogate
   * (WhitespaceAnalyzer, so the JFlex trailing-surrogate issue is not involved).
   */
  public void testAnalysisWhitespace() throws IOException {
    Analyzer a = new WhitespaceAnalyzer();
    index(a, (d, v) -> d.add(new TextField(FIELD, v, Field.Store.YES)), "Hello " + EMOJI + " world");
    try {
      String s = highlight(a, 7, "Hello", UnifiedHighlighter.OffsetSource.ANALYSIS);
      assertTrue(s, s.contains("<b>Hello</b>"));
      assertWellFormed("ANALYSIS (whitespace) maxLength=7", s);
    } finally {
      close();
      a.close();
    }
  }

  /**
   * ANALYSIS offset source with the test framework's MockTokenizer: Lucene's own test tokenizer
   * treats a stream that ends in an unpaired high surrogate as illegal input and throws. With a
   * well-formed stored value, highlighting should not hand the analyzer such a stream.
   */
  public void testAnalysisMockTokenizerSeesIllegalStream() throws IOException {
    Analyzer a = mock();
    index(a, (d, v) -> d.add(new TextField(FIELD, v, Field.Store.YES)), "Hello " + EMOJI + " world");
    try {
      // control: cut after the emoji works
      String s8 = highlight(a, 8, "hello", UnifiedHighlighter.OffsetSource.ANALYSIS);
      assertWellFormed("ANALYSIS (mock) maxLength=8 (control)", s8);
      String s7;
      try {
        s7 = highlight(a, 7, "hello", UnifiedHighlighter.OffsetSource.ANALYSIS);
      } catch (IllegalStateException e) {
        throw new AssertionError(
            "highlighting a well-formed stored value with maxLength=7 fed the analyzer an"
                + " ill-formed stream: "
                + e.getMessage(),
            e);
      }
      assertWellFormed("ANALYSIS (mock) maxLength=7", s7);
    } finally {
      close();
      a.close();
    }
  }
}
