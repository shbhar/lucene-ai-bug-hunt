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
package org.apache.lucene.analysis.ja;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * JapaneseTokenizer groups a run of characters of the same character class into one unknown word
 * of at most 1024 UTF-16 chars. If char 1023 of the run is the high surrogate of a supplementary
 * character (an emoji, an Extension B ideograph), the cut falls inside the surrogate pair: the
 * tokens end and start with unpaired surrogates and the character itself is never a term.
 */
public class TestJapaneseUnknownWordSurrogatePair extends BaseTokenStreamTestCase {

  private static final String EMOJI = new String(Character.toChars(0x1F600));
  private static final String EXT_B = new String(Character.toChars(0x20000));

  private static List<String> terms(Analyzer a, String text) throws IOException {
    List<String> terms = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) terms.add(termAtt.toString());
      ts.end();
    }
    return terms;
  }

  private static boolean wellFormed(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
          i++;
        } else {
          return false;
        }
      } else if (Character.isLowSurrogate(c)) {
        return false;
      }
    }
    return true;
  }

  private static Analyzer tokenizerOnly() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new JapaneseTokenizer(newAttributeFactory(), null, true, JapaneseTokenizer.Mode.SEARCH);
        return new TokenStreamComponents(t, t);
      }
    };
  }

  /** Thai text (no spaces between words) with the probe inserted at char pos. */
  private static String thai(int pos, String probe) {
    StringBuilder sb = new StringBuilder();
    while (sb.length() < pos) sb.append("ภาษาไทย");
    sb.setLength(pos);
    sb.append(probe);
    while (sb.length() < 1200) sb.append("ภาษาไทย");
    return sb.toString();
  }

  private static void assertWellFormedAndContains(Analyzer a, String text, String probe, String what)
      throws IOException {
    List<String> terms = terms(a, text);
    for (int i = 0; i < terms.size(); i++) {
      assertTrue(
          what + ": term " + i + " (length " + terms.get(i).length() + ") has an unpaired surrogate",
          wellFormed(terms.get(i)));
    }
    boolean found = false;
    for (String t : terms) found |= t.contains(probe);
    assertTrue(what + ": no term contains U+" + Integer.toHexString(probe.codePointAt(0)), found);
  }

  /** Controls: one char earlier or later the character survives. */
  public void testControls() throws IOException {
    try (Analyzer a = tokenizerOnly()) {
      for (int pos : new int[] {10, 1022, 1024}) {
        assertWellFormedAndContains(a, thai(pos, EMOJI), EMOJI, "emoji at " + pos);
        assertWellFormedAndContains(a, thai(pos, EXT_B), EXT_B, "U+20000 at " + pos);
      }
    }
  }

  public void testEmojiAtChar1023OfThaiRun() throws IOException {
    try (Analyzer a = tokenizerOnly()) {
      assertWellFormedAndContains(a, thai(1023, EMOJI), EMOJI, "emoji at 1023");
    }
  }

  public void testExtensionBAtChar1023OfThaiRun() throws IOException {
    try (Analyzer a = tokenizerOnly()) {
      assertWellFormedAndContains(a, thai(1023, EXT_B), EXT_B, "U+20000 at 1023");
    }
  }

  /**
   * The same through the SPI factory ("japanese", default parameters). Note: the default
   * JapaneseAnalyzer is not affected, because its part-of-speech stop filter removes these
   * symbol-class unknown words altogether.
   */
  public void testFactoryDefaults() throws IOException {
    try (Analyzer a =
        org.apache.lucene.analysis.custom.CustomAnalyzer.builder().withTokenizer("japanese").build()) {
      assertWellFormedAndContains(a, thai(1023, EXT_B), EXT_B, "factory, U+20000 at 1023");
    }
  }

  /** A run of 400 "eye + VS16" emoji (3 chars each, all in the same character class). */
  public void testEmojiRunWithVariationSelectors() throws IOException {
    String eye = new String(Character.toChars(0x1F441)) + "️";
    String text = eye.repeat(400);
    try (Analyzer a = tokenizerOnly()) {
      List<String> terms = terms(a, text);
      for (int i = 0; i < terms.size(); i++) {
        assertTrue("term " + i + " has an unpaired surrogate", wellFormed(terms.get(i)));
      }
    }
  }
}
