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

import java.util.Locale;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
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
import org.apache.lucene.search.uhighlight.UnifiedHighlighter.OffsetSource;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The UnifiedHighlighter truncates a stored value to maxLength UTF-16 units. When the cut falls
 * between the two halves of a supplementary character, the snippet must not end in half of it.
 */
public class TestUnifiedHighlighterMaxLengthSurrogates extends LuceneTestCase {
  private static final String EMOJI = "\uD83D\uDE00"; // U+1F600, an emoji: two UTF-16 units
  private static final String VALUE = "hello " + EMOJI + " world"; // the emoji is at units 6 and 7

  private static final FieldType POSTINGS = new FieldType(TextField.TYPE_STORED);
  private static final FieldType TERM_VECTORS = new FieldType(TextField.TYPE_STORED);

  static {
    POSTINGS.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    POSTINGS.freeze();
    TERM_VECTORS.setStoreTermVectors(true);
    TERM_VECTORS.setStoreTermVectorPositions(true);
    TERM_VECTORS.setStoreTermVectorOffsets(true);
    TERM_VECTORS.freeze();
  }

  /** Controls: a cut just before or just after the emoji. */
  public void testCutNextToSupplementaryCharacter() throws Exception {
    assertSnippet("<b>hello</b> ", highlight(POSTINGS, OffsetSource.POSTINGS, 6, "hello", VALUE));
    assertSnippet(
        "<b>hello</b> " + EMOJI, highlight(POSTINGS, OffsetSource.POSTINGS, 8, "hello", VALUE));
  }

  public void testCutInsideSupplementaryCharacterWithOffsetsInPostings() throws Exception {
    assertSnippet("<b>hello</b> ", highlight(POSTINGS, OffsetSource.POSTINGS, 7, "hello", VALUE));
  }

  public void testCutInsideSupplementaryCharacterWithTermVectors() throws Exception {
    assertSnippet(
        "<b>hello</b> ", highlight(TERM_VECTORS, OffsetSource.TERM_VECTORS, 7, "hello", VALUE));
  }

  public void testCutInsideSupplementaryCharacterWithReanalysis() throws Exception {
    assertSnippet(
        "<b>hello</b> ",
        highlight(TextField.TYPE_STORED, OffsetSource.ANALYSIS, 7, "hello", VALUE));
  }

  /** Content = "hello", the value separator, then the first 7 units of the second value. */
  public void testCutInsideSupplementaryCharacterOfLaterValue() throws Exception {
    assertSnippet(
        "<b>world</b> ",
        highlight(
            POSTINGS, OffsetSource.POSTINGS, 13, "world", "hello", "world " + EMOJI + " end"));
  }

  /** The default maxLength (10000), with the first half of the emoji at unit 9999. */
  public void testDefaultMaxLength() throws Exception {
    String value = "x ".repeat(4996) + " " + VALUE; // 9993 units, then VALUE
    assertEquals(UnifiedHighlighter.DEFAULT_MAX_LENGTH - 1, value.indexOf(EMOJI));
    String snippet = highlight(POSTINGS, OffsetSource.POSTINGS, -1, "hello", value);
    assertSnippet("<b>hello</b> ", snippet.substring(snippet.lastIndexOf("<b>")));
  }

  /** Indexes one document whose field "body" has the given values and highlights term in it. */
  private String highlight(
      FieldType type, OffsetSource source, int maxLength, String term, String... values)
      throws Exception {
    try (Analyzer analyzer = new WhitespaceAnalyzer();
        Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        Document doc = new Document();
        for (String value : values) {
          doc.add(new Field("body", value, type));
        }
        writer.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        Query query = new TermQuery(new Term("body", term));
        TopDocs hits = searcher.search(query, 10);
        assertEquals(1, hits.scoreDocs.length);
        UnifiedHighlighter.Builder builder = UnifiedHighlighter.builder(searcher, analyzer);
        if (maxLength > 0) {
          builder.withMaxLength(maxLength);
        }
        UnifiedHighlighter highlighter = builder.build();
        assertEquals(source, highlighter.getOffsetSource("body"));
        return highlighter.highlight("body", query, hits)[0];
      }
    }
  }

  /** Compares with non-ASCII chars written as \\uXXXX, so that a lone half is visible. */
  private static void assertSnippet(String expected, String actual) {
    assertEquals(escape(expected), escape(actual));
  }

  private static String escape(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      sb.append(c < 0x7f ? String.valueOf(c) : String.format(Locale.ROOT, "\\u%04X", (int) c));
    }
    return sb.toString();
  }
}
