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
 * A word that ends in ZERO WIDTH JOINER (U+200D) and is directly followed by an emoji is dropped by ICUTokenizer
 * together with the emoji. UAX #29 rule WB3c (ZWJ x Extended_Pictographic) keeps the whole run in one segment, the
 * break rules give that segment rule status 0 ("not a word"), and the emoji check only looks at its first code
 * point, so the segment is skipped.
 *
 * <p>Uses only public API: ICUTokenizer, DefaultICUTokenizerConfig, StandardTokenizer.
 */
public class TestICUTokenizerZwjEmoji extends BaseTokenStreamTestCase {

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

  private static String esc(String s) {
    StringBuilder sb = new StringBuilder("\"");
    s.codePoints().forEach(cp -> sb.append(cp < 0x80 ? String.valueOf((char) cp) : String.format("\\u{%X}", cp)));
    return sb.append('"').toString();
  }

  /** Every given substring of the input must be part of some token. */
  private static void assertKept(Tokenizer t, String text, String... parts) throws IOException {
    List<String> tokens = terms(t, text);
    String all = String.join("|", tokens);
    List<String> e = new ArrayList<>();
    for (String tok : tokens) e.add(esc(tok));
    for (String p : parts) {
      assertTrue(esc(p) + " of input " + esc(text) + " is missing from the tokens " + e, all.contains(p));
    }
  }

  // Malayalam "avan" written with the ZWJ form of the chillu (NA + VIRAMA + ZWJ)
  private static final String MALAYALAM_WORD = "\u0D05\u0D35\u0D28\u0D4D";
  private static final String ZWJ = "\u200D";
  private static final String EMOJI = "\uD83D\uDE02"; // U+1F602 FACE WITH TEARS OF JOY

  /** Controls: with a space before the emoji, or without the ZWJ, both the word and the emoji are tokens. */
  public void testControls() throws Exception {
    assertEquals(
        List.of(MALAYALAM_WORD + ZWJ, EMOJI), terms(new ICUTokenizer(), MALAYALAM_WORD + ZWJ + " " + EMOJI));
    assertEquals(List.of("abc", "\uD83D\uDC4D"), terms(new ICUTokenizer(), "abc\uD83D\uDC4D"));
    // StandardTokenizer (UAX #29, JFlex) keeps both
    assertEquals(
        List.of(MALAYALAM_WORD + ZWJ, EMOJI), terms(new StandardTokenizer(), MALAYALAM_WORD + ZWJ + EMOJI));
  }

  public void testMalayalamChilluZwjThenEmoji() throws Exception {
    assertKept(new ICUTokenizer(), MALAYALAM_WORD + ZWJ + EMOJI, MALAYALAM_WORD, EMOJI);
  }

  public void testLatinWordZwjThenEmoji() throws Exception {
    assertKept(new ICUTokenizer(), "abc" + ZWJ + "\uD83D\uDC4D", "abc", "\uD83D\uDC4D");
  }

  public void testCjkNotAsWordsConfig() throws Exception {
    assertKept(
        new ICUTokenizer(new DefaultICUTokenizerConfig(false, true)),
        MALAYALAM_WORD + ZWJ + EMOJI,
        MALAYALAM_WORD,
        EMOJI);
  }
}
