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
package org.apache.lucene.analysis.ckb;

import java.io.IOException;
import java.io.StringReader;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * SoraniNormalizer documents that a word-final HEH (U+0647) becomes AE (U+06D5) and that "formatting
 * characters such as directional controls are removed". The word-final test is made when the HEH is
 * reached, before a trailing directional control is deleted, so a word that ends in HEH followed by
 * an invisible directional mark (RLM, LRM, ALM) keeps HEH, while the same word without the mark gets
 * AE. The marks do not change how the word is displayed or joined.
 *
 * <p>Not claimed here: HEH followed by harakat or tatweel (a vowel mark or a forced joining form can
 * mark HEH as the consonant h; the upstream vocabulary data expects HEH + TATWEEL to stay HEH), or
 * by ZERO WIDTH JOINER (also forces a joining form).
 */
public class TestSoraniFinalHehRule extends BaseTokenStreamTestCase {

  private static String hex(String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) b.append(String.format("U+%04X ", (int) s.charAt(i)));
    return b.toString().trim();
  }

  /** Runs the filter alone on a single keyword token and returns the term. */
  private static String normalizeWithFilter(String in) throws IOException {
    Tokenizer t = new KeywordTokenizer();
    t.setReader(new StringReader(in));
    try (TokenStream ts = new SoraniNormalizationFilter(t)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      assertTrue(ts.incrementToken());
      String s = term.toString();
      assertFalse(ts.incrementToken());
      ts.end();
      return s;
    }
  }

  // KHAH ALEF NOON HEH ("house", final vowel typed with HEH)
  static final String WORD = "خانه";
  static final String EXPECTED = "خانە";

  public void testControlWordFinalHeh() throws IOException {
    assertEquals(hex(EXPECTED), hex(normalizeWithFilter(WORD))); // passes: documented rule applies
  }

  public void testWordFinalHehFollowedByRightToLeftMark() throws IOException {
    assertEquals(hex(EXPECTED), hex(normalizeWithFilter(WORD + "\u200F")));
  }

  public void testWordFinalHehFollowedByLeftToRightMark() throws IOException {
    assertEquals(hex(EXPECTED), hex(normalizeWithFilter(WORD + "\u200E")));
  }

  public void testWordFinalHehFollowedByArabicLetterMark() throws IOException {
    assertEquals(hex(EXPECTED), hex(normalizeWithFilter(WORD + "\u061C")));
  }

  /** Query-side impact through SoraniAnalyzer.normalize. */
  public void testSoraniAnalyzerNormalize() throws IOException {
    try (SoraniAnalyzer a = new SoraniAnalyzer()) {
      String plain = a.normalize("f", WORD).utf8ToString();
      assertEquals(hex(plain), hex(a.normalize("f", WORD + "\u200F").utf8ToString()));
    }
  }
}
