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
package org.apache.lucene.analysis.miscellaneous;

import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.GENERATE_WORD_PARTS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.SPLIT_ON_NUMERICS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.STEM_ENGLISH_POSSESSIVE;

import java.io.IOException;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.util.QueryBuilder;

/**
 * A token that WordDelimiterGraphFilter swallows (a punctuation-only token such as "--", or a token
 * whose parts the flags all switch off) leaves a hole, like a stop word removed by StopFilter. When
 * that token is the last one of the stream, end() must report the hole as the final position
 * increment, as FilteringTokenFilter does for a trailing stop word.
 */
public class TestWordDelimiterGraphFilterTrailingHole extends BaseTokenStreamTestCase {

  /** The default flags of WordDelimiterGraphFilterFactory. */
  private static final int DEFAULT_FLAGS =
      GENERATE_WORD_PARTS
          | GENERATE_NUMBER_PARTS
          | SPLIT_ON_CASE_CHANGE
          | SPLIT_ON_NUMERICS
          | STEM_ENGLISH_POSSESSIVE;

  private static final CharArraySet STOP = new CharArraySet(List.of("the"), false);

  private static Analyzer analyzer(boolean stopFilter, boolean wdgf, int flags) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new MockTokenizer(MockTokenizer.WHITESPACE, false);
        TokenStream stream = tokenizer;
        if (stopFilter) {
          stream = new StopFilter(stream, STOP);
        }
        if (wdgf) {
          stream = new WordDelimiterGraphFilter(stream, flags, null);
        }
        return new TokenStreamComponents(tokenizer, stream);
      }
    };
  }

  private static void assertPositions(
      Analyzer a, String text, String[] terms, int[] posIncs, int finalPosInc) throws IOException {
    assertTokenStreamContents(
        a.tokenStream("f", text),
        terms,
        null,
        null,
        null,
        posIncs,
        null,
        text.length(),
        finalPosInc,
        null,
        false,
        null);
  }

  /** Controls: a swallowed token in the middle leaves a hole; StopFilter reports a trailing one. */
  public void testControls() throws Exception {
    try (Analyzer wdgf = analyzer(false, true, DEFAULT_FLAGS);
        Analyzer stop = analyzer(true, false, DEFAULT_FLAGS)) {
      assertPositions(wdgf, "foo -- bar", new String[] {"foo", "bar"}, new int[] {1, 2}, 0);
      assertPositions(stop, "foo the", new String[] {"foo"}, new int[] {1}, 1);
    }
  }

  /** A trailing punctuation-only token: its hole must be the final position increment. */
  public void testTrailingPunctuationOnlyToken() throws Exception {
    try (Analyzer a = analyzer(false, true, DEFAULT_FLAGS)) {
      assertPositions(a, "foo --", new String[] {"foo"}, new int[] {1}, 1);
    }
  }

  /** A trailing token whose parts the flags all switch off is swallowed the same way. */
  public void testTrailingTokenWithoutGeneratedParts() throws Exception {
    try (Analyzer a = analyzer(false, true, GENERATE_WORD_PARTS)) {
      // control: in the middle, "12-34" leaves a hole
      assertPositions(a, "foo 12-34 bar", new String[] {"foo", "bar"}, new int[] {1, 2}, 0);
      assertPositions(a, "foo 12-34", new String[] {"foo"}, new int[] {1}, 1);
    }
  }

  /** The hole of a stop word that a trailing swallowed token carries must not be lost either. */
  public void testTrailingTokenCarriesStopWordHole() throws Exception {
    try (Analyzer a = analyzer(true, true, DEFAULT_FLAGS)) {
      // controls: in the middle both holes reach "bar"; a trailing stop word alone is reported
      assertPositions(a, "foo the -- bar", new String[] {"foo", "bar"}, new int[] {1, 3}, 0);
      assertPositions(a, "foo the", new String[] {"foo"}, new int[] {1}, 1);
      assertPositions(a, "foo the --", new String[] {"foo"}, new int[] {1}, 2);
    }
  }

  private static int count(Analyzer a, Query query, String... values) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(a))) {
        Document doc = new Document();
        for (String value : values) {
          doc.add(new TextField("body", value, Field.Store.NO));
        }
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        return newSearcher(r).count(query);
      }
    }
  }

  /**
   * With the default position increment gap of 0, the next value of a multi-valued field must start
   * after the hole, so a phrase across the two values behaves like the same text in one value, and
   * like the same values with StopFilter.
   */
  public void testPhraseAcrossValues() throws Exception {
    try (Analyzer stop = analyzer(true, false, DEFAULT_FLAGS);
        Analyzer wdgf = analyzer(false, true, DEFAULT_FLAGS)) {
      Query adjacent = new PhraseQuery("body", "foo", "bar");
      Query stopPhrase = new QueryBuilder(stop).createPhraseQuery("body", "foo the bar");
      Query wdgfPhrase = new QueryBuilder(wdgf).createPhraseQuery("body", "foo -- bar");
      // controls: StopFilter on the values "foo the" | "bar", and the single value "foo -- bar"
      assertEquals(stopPhrase.toString(), 1, count(stop, stopPhrase, "foo the", "bar"));
      assertEquals(adjacent.toString(), 0, count(stop, adjacent, "foo the", "bar"));
      assertEquals(wdgfPhrase.toString(), 1, count(wdgf, wdgfPhrase, "foo -- bar"));
      assertEquals(adjacent.toString(), 0, count(wdgf, adjacent, "foo -- bar"));
      // the same text split into the values "foo --" | "bar": both phrases must count as above
      assertEquals(
          wdgfPhrase + " and " + adjacent + " on the values \"foo --\" | \"bar\"",
          List.of(1, 0),
          List.of(
              count(wdgf, wdgfPhrase, "foo --", "bar"), count(wdgf, adjacent, "foo --", "bar")));
    }
  }
}
