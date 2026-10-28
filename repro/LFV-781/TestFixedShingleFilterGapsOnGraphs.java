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
package org.apache.lucene.analysis.shingle;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.miscellaneous.KeywordRepeatFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;

/**
 * A filler "_" stands for each empty position between two tokens of a route through the token
 * graph. Tokens stacked at one position (synonyms, preserved originals, stems) are at the same
 * distance from the token before them, so every route to them must cross the same gap.
 */
public class TestFixedShingleFilterGapsOnGraphs extends BaseTokenStreamTestCase {

  /** The distinct shingles of a stream (sets, so that duplicate shingles do not matter). */
  private static Set<String> shingles(TokenStream ts) throws IOException {
    Set<String> out = new TreeSet<>();
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    ts.reset();
    while (ts.incrementToken()) {
      out.add(term.toString());
    }
    ts.end();
    ts.close();
    return out;
  }

  private static Set<String> set(String... shingles) {
    return new TreeSet<>(Arrays.asList(shingles));
  }

  private static Set<String> analyze(String text, boolean stemmer, int size) throws IOException {
    try (Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new WhitespaceTokenizer();
            TokenStream ts = new StopFilter(tokenizer, new CharArraySet(List.of("the"), false));
            if (stemmer) {
              ts = new PorterStemFilter(new KeywordRepeatFilter(ts));
            } else {
              ts = new ASCIIFoldingFilter(ts, true);
            }
            return new TokenStreamComponents(tokenizer, new FixedShingleFilter(ts, size));
          }
        }) {
      return shingles(analyzer.tokenStream("f", text));
    }
  }

  /** a, an empty position, then b and c stacked: a -> c crosses the gap like a -> b. */
  public void testStackedTokensAfterGap() throws IOException {
    Token[] abc = {new Token("a", 1, 0, 1), new Token("b", 2, 4, 5), new Token("c", 0, 4, 5)};
    assertEquals(set("a _"), shingles(new FixedShingleFilter(new CannedTokenStream(abc), 2)));
    assertEquals(
        set("a _ b", "a _ c"), shingles(new FixedShingleFilter(new CannedTokenStream(abc), 3)));
  }

  /** The same token graph with the two stacked tokens in the other stream order. */
  public void testStackOrderDoesNotChangeShingles() throws IOException {
    for (int size = 2; size <= 3; size++) {
      Set<String> bc =
          shingles(
              new FixedShingleFilter(
                  new CannedTokenStream(
                      new Token("a", 1, 0, 1), new Token("b", 2, 4, 5), new Token("c", 0, 4, 5)),
                  size));
      Set<String> cb =
          shingles(
              new FixedShingleFilter(
                  new CannedTokenStream(
                      new Token("a", 1, 0, 1), new Token("c", 2, 4, 5), new Token("b", 0, 4, 5)),
                  size));
      assertEquals("shingle size " + size, bc, cb);
    }
  }

  /** StopFilter, then ASCIIFoldingFilter keeping the original "café" stacked on "cafe". */
  public void testStopFilterThenFoldingPreserveOriginal() throws IOException {
    assertEquals(set("coffee at", "at _"), analyze("coffee at the café", false, 2));
    assertEquals(
        set("coffee at _", "at _ cafe", "at _ café"), analyze("coffee at the café", false, 3));
  }

  /** StopFilter, then KeywordRepeatFilter and a stemmer: "dog" is stacked on "dogs". */
  public void testStopFilterThenKeywordRepeatAndStemmer() throws IOException {
    assertEquals(set("walk _"), analyze("walk the dogs", true, 2));
  }

  /** login spans positions 0-1 (log, then a removed word), page is at position 2. */
  public void testMultiPositionTokenOverGap() throws IOException {
    TokenStream ts =
        new CannedTokenStream(
            new Token("login", 1, 0, 6, 2), new Token("log", 0, 0, 3), new Token("page", 2, 7, 11));
    assertEquals(set("login page", "log _"), shingles(new FixedShingleFilter(ts, 2)));
  }

  /** Controls (pass before and after a fix): a gap without a stack, a stack without a gap. */
  public void testControls() throws IOException {
    assertEquals(set("coffee at", "at cafe", "at café"), analyze("coffee at café", false, 2));
    assertEquals(set("coffee at", "at _"), analyze("coffee at the cafe", false, 2));
    assertEquals(
        set("a _ b"),
        shingles(
            new FixedShingleFilter(
                new CannedTokenStream(new Token("a", 1, 0, 1), new Token("b", 2, 4, 5)), 3)));
  }
}
