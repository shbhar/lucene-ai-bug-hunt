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
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.FlattenGraphFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.CharsRefBuilder;

/**
 * FixedShingleFilter fills gaps with "_" ("handles position increments &gt; 1 by inserting filler
 * tokens") and outputs stacked shingles for stacked input tokens. It decides whether a gap follows
 * the previous token of a route from the next token's own position increment, which is relative to
 * the previous token in the stream, not to the previous token on the route.
 */
public class TestFixedShingleFilterRouteGaps extends BaseTokenStreamTestCase {

  /** Returns "position:term" for every emitted shingle. */
  private static List<String> shingles(TokenStream ts) throws IOException {
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    PositionIncrementAttribute inc = ts.addAttribute(PositionIncrementAttribute.class);
    List<String> out = new ArrayList<>();
    int pos = -1;
    ts.reset();
    while (ts.incrementToken()) {
      pos += inc.getPositionIncrement();
      out.add(pos + ":" + term);
    }
    ts.end();
    ts.close();
    return out;
  }

  /**
   * 'a', a hole (a removed stop word), then 'b' and 'c' stacked at the same position: both routes
   * from 'a' cross the same hole, so both must start with "a _".
   */
  public void testStackedTokensAfterHole() throws IOException {
    List<String> size2 =
        shingles(
            new FixedShingleFilter(
                new CannedTokenStream(
                    new Token("a", 1, 0, 1), new Token("b", 2, 6, 7), new Token("c", 0, 6, 7)),
                2));
    // the route a -> c crosses the hole like the route a -> b
    assertFalse("shingle joins a and c across the hole: " + size2, size2.contains("0:a c"));

    List<String> size3 =
        shingles(
            new FixedShingleFilter(
                new CannedTokenStream(
                    new Token("a", 1, 0, 1), new Token("b", 2, 6, 7), new Token("c", 0, 6, 7)),
                3));
    assertTrue("route a -> b: " + size3, size3.contains("0:a _ b"));
    assertTrue("route a -> c must give the stacked shingle 'a _ c': " + size3, size3.contains("0:a _ c"));
  }

  /**
   * A common chain: stop words removed, then ASCII folding that keeps the original token stacked on
   * the folded one. "coffee at the café": the folded "cafe" and the original "café" are stacked at
   * the same position after the hole left by "the".
   */
  public void testFoldingPreserveOriginalAfterStopWord() throws IOException {
    for (int size : new int[] {2, 3}) {
      Analyzer analyzer =
          new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
              Tokenizer tokenizer = new WhitespaceTokenizer();
              TokenStream ts = new StopFilter(tokenizer, new CharArraySet(List.of("the"), false));
              ts = new ASCIIFoldingFilter(ts, true);
              return new TokenStreamComponents(tokenizer, new FixedShingleFilter(ts, size));
            }
          };
      List<String> out = shingles(analyzer.tokenStream("f", "coffee at the café"));
      analyzer.close();
      if (size == 2) {
        assertTrue(out.toString(), out.contains("1:at _"));
        // "café" is two positions after "at", exactly like "cafe"
        assertFalse("'at café' is not a bigram of this text: " + out, out.contains("1:at café"));
      } else {
        assertTrue(out.toString(), out.contains("1:at _ cafe"));
        assertTrue("stacked shingle 'at _ café' missing: " + out, out.contains("1:at _ café"));
        assertFalse(
            "'coffee at café' is not a trigram of this text: " + out,
            out.contains("0:coffee at café"));
      }
    }
  }

  /** Minimal form of the next test: 'login' spans positions 0-1, 'page' is at position 2. */
  public void testMultiPositionTokenFollowedByTokenCanned() throws IOException {
    List<String> out =
        shingles(
            new FixedShingleFilter(
                new CannedTokenStream(
                    new Token("login", 1, 0, 6, 2),
                    new Token("log", 0, 0, 3),
                    new Token("page", 2, 7, 11)),
                2));
    assertTrue(out.toString(), out.contains("0:log _"));
    assertTrue("'login' is directly followed by 'page': " + out, out.contains("0:login page"));
  }

  /**
   * A multi-position token whose other path ends with a hole: synonym "log in" -> "login" (a token
   * spanning two positions), graph flattened, then the stop word "in" removed. "page" starts where
   * "login" ends, so the route login -> page has no gap.
   */
  public void testMultiPositionTokenFollowedByToken() throws IOException {
    SynonymMap.Builder builder = new SynonymMap.Builder(true);
    builder.add(
        SynonymMap.Builder.join(new String[] {"log", "in"}, new CharsRefBuilder()),
        new CharsRef("login"),
        true);
    SynonymMap map = builder.build();
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new WhitespaceTokenizer();
            TokenStream ts = new SynonymGraphFilter(tokenizer, map, true);
            ts = new FlattenGraphFilter(ts);
            ts = new StopFilter(ts, new CharArraySet(List.of("in"), false));
            return new TokenStreamComponents(tokenizer, new FixedShingleFilter(ts, 2));
          }
        };
    List<String> out = shingles(analyzer.tokenStream("f", "log in page"));
    analyzer.close();
    assertTrue(out.toString(), out.contains("0:log _"));
    assertTrue("'login' is directly followed by 'page': " + out, out.contains("0:login page"));
  }
}
