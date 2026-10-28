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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.miscellaneous.KeywordRepeatFilter;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.util.CharsRef;

/**
 * Independent check (written from the claim only): FixedShingleFilter must insert one filler per
 * empty position between two consecutive tokens of a route, whatever the stream order of tokens
 * stacked at one position. Public API only (FixedShingleFilter, stock filters, test-framework
 * CannedTokenStream).
 */
public class TestFixedShingleRouteGapV1 extends BaseTokenStreamTestCase {

  private static List<String> shingles(TokenStream ts) throws IOException {
    List<String> out = new ArrayList<>();
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    ts.reset();
    while (ts.incrementToken()) {
      out.add(term.toString());
    }
    ts.end();
    ts.close();
    return out;
  }

  private static List<String> sorted(List<String> l) {
    List<String> c = new ArrayList<>(l);
    Collections.sort(c);
    return c;
  }

  private static Analyzer chain(boolean preserveOriginal, int size) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tok = new WhitespaceTokenizer();
        TokenStream ts = new StopFilter(tok, new CharArraySet(Arrays.asList("the"), false));
        ts = new ASCIIFoldingFilter(ts, preserveOriginal);
        ts = new FixedShingleFilter(ts, size);
        return new TokenStreamComponents(tok, ts);
      }
    };
  }

  private static List<String> analyze(Analyzer a, String text) throws IOException {
    try (a) {
      return shingles(a.tokenStream("f", text));
    }
  }

  // ---------------------------------------------------------------------------------------
  // Shape 1: a hole, then two tokens stacked at the same position.
  // Stream: x@0, (empty position 1), y@2, z@2 (z stacked on y with increment 0).
  // ---------------------------------------------------------------------------------------

  /** Control: the hole alone (one token at position 2) gives fillers, as the javadoc says. */
  public void testControlHoleWithoutStack() throws IOException {
    TokenStream ts = new CannedTokenStream(new Token("x", 0, 1), new Token("z", 2, 4, 5));
    assertEquals(List.of("x _"), shingles(new FixedShingleFilter(ts, 2)));
    ts = new CannedTokenStream(new Token("x", 0, 1), new Token("z", 2, 4, 5));
    assertEquals(List.of("x _ z"), shingles(new FixedShingleFilter(ts, 3)));
  }

  /** z is two positions after x: no bigram "x z" may be produced. */
  public void testStackedAfterHoleBigram() throws IOException {
    TokenStream ts =
        new CannedTokenStream(
            new Token("x", 0, 1), new Token("y", 2, 4, 5), new Token("z", 0, 4, 5));
    List<String> got = shingles(new FixedShingleFilter(ts, 2));
    assertFalse("x and z are not adjacent (z at position 2), got " + got, got.contains("x z"));
  }

  /** The trigram over the hole must exist for both stacked alternatives. */
  public void testStackedAfterHoleTrigram() throws IOException {
    TokenStream ts =
        new CannedTokenStream(
            new Token("x", 0, 1), new Token("y", 2, 4, 5), new Token("z", 0, 4, 5));
    List<String> got = shingles(new FixedShingleFilter(ts, 3));
    assertTrue("missing 'x _ y', got " + got, got.contains("x _ y"));
    assertTrue("missing 'x _ z', got " + got, got.contains("x _ z"));
  }

  /**
   * Same token graph, only the stream order of the two stacked tokens differs. The set of shingles
   * (with y and z swapped) must be the same.
   */
  public void testStackOrderDoesNotMatter() throws IOException {
    TokenStream ts1 =
        new CannedTokenStream(
            new Token("x", 0, 1), new Token("y", 2, 4, 5), new Token("z", 0, 4, 5));
    TokenStream ts2 =
        new CannedTokenStream(
            new Token("x", 0, 1), new Token("z", 2, 4, 5), new Token("y", 0, 4, 5));
    assertEquals(
        sorted(shingles(new FixedShingleFilter(ts1, 2))),
        sorted(shingles(new FixedShingleFilter(ts2, 2))));
  }

  /** Real chain: whitespace, stop words {the}, ASCII folding keeping the original. */
  public void testStopThenFoldingPreserveOriginal() throws IOException {
    String text = "coffee at the café";
    // control: without preserveOriginal the hole is kept
    List<String> ctrl = analyze(chain(false, 2), text);
    assertEquals(List.of("coffee at", "at _"), ctrl);
    // control: no stop word, so no hole: both alternatives directly follow "at"
    List<String> noHole = analyze(chain(true, 2), "coffee at café");
    assertTrue(noHole.toString(), noHole.contains("at cafe") && noHole.contains("at café"));

    List<String> bi = analyze(chain(true, 2), text);
    assertFalse("'at café' is not a bigram of '" + text + "': " + bi, bi.contains("at café"));
    List<String> tri = analyze(chain(true, 3), text);
    assertFalse(
        "'coffee at café' skips 'the': " + tri, tri.contains("coffee at café"));
    assertTrue("missing 'at _ café': " + tri, tri.contains("at _ café"));
  }

  /**
   * Real chain: single-word synonyms (position length 1, so a following StopFilter keeps the graph
   * valid), then stop words. The input of FixedShingleFilter is checked first, so that a failure
   * can only come from FixedShingleFilter. (With StopFilter BEFORE SynonymGraphFilter the hole is
   * already gone from the synonym filter's output, so that order is not used here.)
   */
  public void testSingleWordSynonymThenStop() throws IOException {
    SynonymMap.Builder b = new SynonymMap.Builder(true);
    b.add(new CharsRef("cafe"), new CharsRef("bistro"), true);
    final SynonymMap map = b.build();
    // input of the shingle filter: bistro and cafe both at position 3, two after "at"
    try (Analyzer in = synonymThenStop(map, 0)) {
      assertTokenStreamContents(
          in.tokenStream("f", "coffee at the cafe"),
          new String[] {"coffee", "at", "bistro", "cafe"},
          null,
          null,
          new int[] {1, 1, 2, 0});
    }
    List<String> got = analyze(synonymThenStop(map, 2), "coffee at the cafe");
    assertFalse("'at cafe' skips 'the': " + got, got.contains("at cafe"));
    assertFalse("'at bistro' skips 'the': " + got, got.contains("at bistro"));
  }

  private static Analyzer synonymThenStop(SynonymMap map, int shingleSize) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tok = new WhitespaceTokenizer();
        TokenStream ts = new SynonymGraphFilter(tok, map, false);
        ts = new StopFilter(ts, new CharArraySet(Arrays.asList("the"), false));
        if (shingleSize > 0) {
          ts = new FixedShingleFilter(ts, shingleSize);
        }
        return new TokenStreamComponents(tok, ts);
      }
    };
  }

  /** Real chain: stop words, KeywordRepeatFilter, a stemmer (stem stacked on the original). */
  public void testStopKeywordRepeatStemmer() throws IOException {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tok = new WhitespaceTokenizer();
            TokenStream ts = new StopFilter(tok, new CharArraySet(Arrays.asList("the"), false));
            ts = new PorterStemFilter(new KeywordRepeatFilter(ts));
            ts = new FixedShingleFilter(ts, 2);
            return new TokenStreamComponents(tok, ts);
          }
        };
    List<String> got = analyze(a, "coffee at the cafes");
    assertFalse("'at cafe' skips 'the': " + got, got.contains("at cafe"));
  }

  // ---------------------------------------------------------------------------------------
  // Shape 2: a token of position length 2 whose last covered position has no token start.
  // Stream: L@0 (posLen 2), s@0 (posLen 1), (empty position 1), n@2.
  // L ends exactly where n starts, so the route L -> n has no gap.
  // ---------------------------------------------------------------------------------------

  /** Control: position 1 holds a token, then L -> n gives "L n". */
  public void testControlMultiPositionTokenNoHole() throws IOException {
    TokenStream ts =
        new CannedTokenStream(
            new Token("L", 1, 0, 5, 2),
            new Token("s", 0, 0, 3, 1),
            new Token("m", 1, 3, 5, 1),
            new Token("n", 1, 6, 10, 1));
    List<String> got = shingles(new FixedShingleFilter(ts, 2));
    assertTrue(got.toString(), got.contains("L n"));
  }

  /** Removing the token INSIDE L's span must not change what follows L. */
  public void testMultiPositionTokenOverHole() throws IOException {
    TokenStream ts =
        new CannedTokenStream(
            new Token("L", 1, 0, 5, 2), new Token("s", 0, 0, 3, 1), new Token("n", 2, 6, 10, 1));
    List<String> got = shingles(new FixedShingleFilter(ts, 2));
    assertTrue("L (positions 0-1) is directly followed by n (position 2): " + got, got.contains("L n"));
    assertFalse("no filler belongs between L and n: " + got, got.contains("L _"));
  }
}
