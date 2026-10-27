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
package org.apache.lucene.analysis.icu.segmentation;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * ICUTokenizer silently drops a Korean word when a Hangul syllable is followed by a Format or Extend character
 * (soft hyphen U+00AD, ZWNJ U+200C, ZWJ U+200D, U+FEFF, a combining mark such as U+0336, a variation selector).
 *
 * <p>Uses only public API: ICUTokenizer, DefaultICUTokenizerConfig, StandardTokenizer.
 */
public class TestICUTokenizerHangulExtendFormat extends BaseTokenStreamTestCase {

  private static List<String> terms(Tokenizer t, String text) throws IOException {
    List<String> out = new ArrayList<>();
    t.setReader(new StringReader(text));
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    t.reset();
    while (t.incrementToken()) {
      out.add(term.toString());
    }
    t.end();
    t.close();
    return out;
  }

  /** Every Hangul syllable of the input must end up in some token (nothing is silently dropped). */
  private static void assertNoHangulDropped(Tokenizer t, String text) throws IOException {
    List<String> tokens = terms(t, text);
    StringBuilder all = new StringBuilder();
    for (String tok : tokens) all.append(tok);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c >= '\uAC00' && c <= '\uD7A3') {
        assertTrue(
            "Hangul syllable U+" + Integer.toHexString(c).toUpperCase() + " at offset " + i
                + " of " + esc(text) + " is missing from the tokens " + escAll(tokens),
            all.indexOf(String.valueOf(c)) >= 0);
      }
    }
  }

  private static String esc(String s) {
    StringBuilder sb = new StringBuilder("\"");
    for (char c : s.toCharArray()) {
      if (c < 0x80) sb.append(c);
      else sb.append(String.format("\\u%04X", (int) c));
    }
    return sb.append('"').toString();
  }

  private static String escAll(List<String> l) {
    List<String> e = new ArrayList<>();
    for (String s : l) e.add(esc(s));
    return e.toString();
  }

  private static final String KOREAN = "\uD55C\uAD6D\uC5B4"; // "Korean language", three Hangul syllables

  /** Control: without the Format character the word is a single HANGUL token; StandardTokenizer keeps it too. */
  public void testControls() throws Exception {
    assertEquals(List.of(KOREAN), terms(new ICUTokenizer(), KOREAN));
    assertEquals(List.of("abc\u00ADdef"), terms(new ICUTokenizer(), "abc\u00ADdef"));
    assertEquals(List.of("\uD55C\uAD6D\u00AD\uC5B4"), terms(new StandardTokenizer(), "\uD55C\uAD6D\u00AD\uC5B4"));
  }

  /** A soft hyphen (U+00AD, Word_Break=Format) inside a Korean word: UAX #29 WB4 ignores it. */
  public void testSoftHyphenInsideHangulWord() throws Exception {
    assertNoHangulDropped(new ICUTokenizer(), "\uD55C\uAD6D\u00AD\uC5B4");
  }

  /** Same with the non-CJK-dictionary configuration. */
  public void testSoftHyphenInsideHangulWordCjkNotAsWords() throws Exception {
    assertNoHangulDropped(
        new ICUTokenizer(new DefaultICUTokenizerConfig(false, true)), "\uD55C\uAD6D\u00AD\uC5B4");
  }

  /** Combining strikethrough (U+0336, as produced by "strikethrough text" generators): every syllable vanishes. */
  public void testCombiningStrikethroughOnHangul() throws Exception {
    assertNoHangulDropped(new ICUTokenizer(), "\uD55C\u0336\uAD6D\u0336\uC5B4\u0336");
  }

  /** U+FEFF (ZERO WIDTH NO-BREAK SPACE / byte order mark, Word_Break=Format) after a Korean word. */
  public void testZeroWidthNoBreakSpaceAfterHangulWord() throws Exception {
    assertNoHangulDropped(new ICUTokenizer(), KOREAN + "\uFEFF");
  }

  /**
   * Same missing trailing $ExFm* on two other status rules of Default.rbbi: a word ending in an ExtendNumLet
   * (rule 13a) or a Hebrew letter + apostrophe (rule 7a), followed by a combining mark, is dropped too.
   */
  public void testSameDefectOnExtendNumLetAndHebrewQuote() throws Exception {
    for (String text : new String[] {"abc_\u0301", "\u05E9\u05DC\u05D5\u05DD'\u0301"}) {
      List<String> tokens = terms(new ICUTokenizer(), text);
      assertTrue(esc(text) + " produced no token " + escAll(tokens), String.join("", tokens).length() > 0);
    }
  }
}
