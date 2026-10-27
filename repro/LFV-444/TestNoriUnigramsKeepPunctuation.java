/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.analysis.ko;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.Test;

/**
 * KoreanTokenizer: with outputUnknownUnigrams=true, punctuation and whitespace that end up inside
 * an unknown word are emitted as unigram tokens even though discardPunctuation is true (the
 * default). The javadoc of the discardPunctuation parameter says "true if punctuation tokens should
 * be dropped from the output".
 *
 * <p>Plain JUnit 4 and public API only, so that it compiles against 8.x, 9.x, 10.x and main.
 */
public class TestNoriUnigramsKeepPunctuation {

  // Inputs mixing Hangul with symbols, punctuation and runs of whitespace.
  private static final String[] INPUTS = {
    "ab#cd", "50% 할인", "foo  bar", "user@host.org", "C++ 언어", "가격은 €50",
  };

  /** A token with no letter and no digit is a punctuation/whitespace/symbol token. */
  private static boolean isPunctuationOnly(String term) {
    for (int i = 0; i < term.length(); ) {
      int cp = term.codePointAt(i);
      if (Character.isLetterOrDigit(cp)) {
        return false;
      }
      i += Character.charCount(cp);
    }
    return true;
  }

  private static List<String> terms(Tokenizer tokenizer, String text) throws IOException {
    tokenizer.setReader(new StringReader(text));
    return drain(tokenizer);
  }

  private static List<String> drain(TokenStream ts) throws IOException {
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

  private static String show(List<String> terms) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < terms.size(); i++) {
      if (i > 0) sb.append(", ");
      sb.append('\'');
      for (char c : terms.get(i).toCharArray()) {
        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
        else sb.append(c);
      }
      sb.append('\'');
    }
    return sb.append(']').toString();
  }

  private static void assertNoPunctuationTokens(String what, String text, List<String> terms) {
    for (String t : terms) {
      assertTrue(
          what + ": input '" + text + "' produced punctuation/whitespace token in " + show(terms),
          isPunctuationOnly(t) == false);
    }
  }

  private static KoreanTokenizer unigramTokenizer() {
    // 4-arg constructor: discardPunctuation defaults to true (exists since 7.4)
    return new KoreanTokenizer(
        TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
        null,
        KoreanTokenizer.DecompoundMode.DISCARD,
        true);
  }

  /** Control: without unigrams the default tokenizer drops punctuation and whitespace. */
  @Test
  public void testControlNoUnigramsDropsPunctuation() throws IOException {
    for (String text : INPUTS) {
      KoreanTokenizer tok =
          new KoreanTokenizer(
              TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
              null,
              KoreanTokenizer.DecompoundMode.DISCARD,
              false);
      List<String> terms = terms(tok, text);
      assertNoPunctuationTokens("outputUnknownUnigrams=false", text, terms);
    }
  }

  /** Control: unigram mode is really active (an unknown Latin word is split into letters). */
  @Test
  public void testControlUnigramsAreActive() throws IOException {
    List<String> terms = terms(unigramTokenizer(), "xyz 문장");
    assertEquals("[x, y, z, 문장]", terms.toString());
  }

  /** The bug: unigram mode with the default discardPunctuation=true emits punctuation tokens. */
  @Test
  public void testUnigramsDefaultDiscardPunctuation() throws IOException {
    for (String text : INPUTS) {
      List<String> terms = terms(unigramTokenizer(), text);
      assertNoPunctuationTokens("outputUnknownUnigrams=true (discardPunctuation default)", text, terms);
    }
  }

  /** Same, with an exact expected token list for one input. */
  @Test
  public void testUnigramsExactEmail() throws IOException {
    List<String> terms = terms(unigramTokenizer(), "user@host.org");
    assertEquals(
        "[u, s, e, r, h, o, s, t, o, r, g]", terms.toString());
  }

  /** Through the factory with only outputUnknownUnigrams=true (discardPunctuation default true). */
  @Test
  public void testFactoryDefaults() throws IOException {
    Map<String, String> args = new HashMap<>();
    args.put("outputUnknownUnigrams", "true");
    KoreanTokenizerFactory factory = new KoreanTokenizerFactory(args);
    factory.inform(null); // no user dictionary configured, the loader is not used
    for (String text : INPUTS) {
      Tokenizer tok = factory.create(TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY);
      List<String> terms = terms(tok, text);
      assertNoPunctuationTokens("factory outputUnknownUnigrams=true", text, terms);
    }
  }

  /** Through KoreanAnalyzer(outputUnknownUnigrams=true), which has no discardPunctuation option. */
  @Test
  public void testAnalyzer() throws IOException {
    try (Analyzer a =
        new KoreanAnalyzer(
            null,
            KoreanTokenizer.DecompoundMode.DISCARD,
            KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS,
            true)) {
      for (String text : INPUTS) {
        List<String> terms = drain(a.tokenStream("f", text));
        assertNoPunctuationTokens("KoreanAnalyzer outputUnknownUnigrams=true", text, terms);
      }
    }
  }
}
