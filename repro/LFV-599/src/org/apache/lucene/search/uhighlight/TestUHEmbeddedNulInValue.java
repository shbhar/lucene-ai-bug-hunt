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

import java.io.Reader;
import java.util.regex.Pattern;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.pattern.PatternReplaceCharFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * UnifiedHighlighter joins the values of a field with U+0000 and, with the ANALYSIS offset source,
 * re-analyzes the joined text value by value, splitting it at every U+0000. A U+0000 that is part
 * of a single stored value is therefore treated as a value boundary, so the re-analysis no longer
 * "matches what would get indexed" (AnalysisOffsetStrategy.MultiValueTokenStream javadoc).
 */
public class TestUHEmbeddedNulInValue extends LuceneTestCase {

  /** StandardTokenizer with a position increment gap, as typical text field configurations use. */
  private static Analyzer gapAnalyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new StandardTokenizer());
      }

      @Override
      public int getPositionIncrementGap(String fieldName) {
        return 100;
      }
    };
  }

  /** Strips control characters before tokenizing. */
  private static Analyzer stripControlCharsAnalyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new WhitespaceTokenizer());
      }

      @Override
      protected Reader initReader(String fieldName, Reader reader) {
        return new PatternReplaceCharFilter(Pattern.compile("\\p{Cc}"), "", reader);
      }
    };
  }

  /**
   * Indexes one document with a single value in an analysis field ("body", ANALYSIS offset source)
   * and an offsets field ("body_offsets", POSTINGS offset source) and returns both snippets for the
   * term query (term2 == null) or the phrase query term1 term2.
   */
  private String[] highlight(Analyzer analyzer, String value, String term1, String term2)
      throws Exception {
    FieldType withOffsets = new FieldType(TextField.TYPE_STORED);
    withOffsets.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    withOffsets.freeze();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new Field("body", value, TextField.TYPE_STORED));
        doc.add(new Field("body_offsets", value, withOffsets));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        UnifiedHighlighter highlighter = UnifiedHighlighter.builder(searcher, analyzer).build();
        String[] result = new String[2];
        String[] fields = {"body", "body_offsets"};
        for (int i = 0; i < 2; i++) {
          Query q =
              term2 == null
                  ? new TermQuery(new Term(fields[i], term1))
                  : new PhraseQuery(fields[i], term1, term2);
          TopDocs hits = searcher.search(q, 10);
          assertEquals("the document matches " + q, 1, hits.totalHits.value());
          result[i] = highlighter.highlight(fields[i], q, hits)[0];
        }
        return result;
      }
    }
  }

  /**
   * Twin oracle: U+0000 and U+0001 are both control characters that the analyzer treats alike, so
   * both values produce exactly the same postings. The snippets must then be the same, up to the
   * one character.
   */
  private void assertSameAsTwin(Analyzer analyzer, String valueWithNul, String t1, String t2)
      throws Exception {
    String[] withNul = highlight(analyzer, valueWithNul, t1, t2);
    String[] twin = highlight(analyzer, valueWithNul.replace('\u0000', '\u0001'), t1, t2);
    assertTrue(twin[0], twin[0].contains("<b>"));
    assertEquals(twin[0], twin[1]);
    // the more severe symptom first: re-analysis (ANALYSIS offset source) must find the match
    assertTrue("ANALYSIS offset source found no match: " + withNul[0], withNul[0].contains("<b>"));
    assertEquals(
        "ANALYSIS offset source", twin[0].replace('\u0001', '\u0000'), withNul[0]);
    assertEquals(
        "POSTINGS offset source", twin[1].replace('\u0001', '\u0000'), withNul[1]);
  }

  /** Default analyzer setup (no position gap): the snippet is cut at the embedded U+0000. */
  public void testSnippetCutAtEmbeddedNul() throws Exception {
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            return new TokenStreamComponents(new StandardTokenizer());
          }
        };
    assertSameAsTwin(analyzer, "Hello foo\u0000bar world. Second sentence.", "foo", "bar");
    analyzer.close();
  }

  /** Position increment gap 100: the ANALYSIS source loses the phrase match altogether. */
  public void testPhraseAcrossEmbeddedNulWithPositionGap() throws Exception {
    Analyzer analyzer = gapAnalyzer();
    assertSameAsTwin(analyzer, "foo\u0000bar baz", "foo", "bar");
    analyzer.close();
  }

  /** The char filter deletes control characters, so the index holds the single token "foobar". */
  public void testTermSpanningStrippedNul() throws Exception {
    Analyzer analyzer = stripControlCharsAnalyzer();
    assertSameAsTwin(analyzer, "foo\u0000bar baz", "foobar", null);
    analyzer.close();
  }
}
