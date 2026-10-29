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
package org.apache.lucene.analysis.ko;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * With discardPunctuation=true (the default), "punctuation tokens should be dropped from the
 * output". The tokenizer decides by Unicode general category, so a character outside the BMP must
 * be treated like a BMP character of the same category.
 */
public class TestKoreanDiscardSupplementaryPunctuation extends BaseTokenStreamTestCase {

  /** Each row: general category, a BMP character, a supplementary character of that category. */
  private static final Object[][] PAIRS = {
    {"Po", 0x3001, 0x10100}, // IDEOGRAPHIC COMMA / AEGEAN WORD SEPARATOR LINE
    {"Po", 0xFF01, 0x1E95E}, // FULLWIDTH EXCLAMATION MARK / ADLAM INITIAL EXCLAMATION MARK
    {"So", 0x2605, 0x1F600}, // BLACK STAR / GRINNING FACE
    {"Sm", 0x2207, 0x1D6C1}, // NABLA / MATHEMATICAL BOLD NABLA
  };

  private static String str(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  private static Analyzer tokenizerOnly() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new KoreanTokenizer()); // discardPunctuation=true
      }
    };
  }

  private static List<String> terms(Analyzer a, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  /** "한국 X 한국" must give [한국, 한국] for every punctuation or symbol X. */
  public void testSupplementaryPunctuationIsDiscarded() throws IOException {
    List<String> broken = new ArrayList<>();
    try (Analyzer tokenizer = tokenizerOnly();
        Analyzer analyzer = new KoreanAnalyzer()) {
      for (Analyzer a : List.of(tokenizer, analyzer)) {
        String name = a == tokenizer ? "KoreanTokenizer()" : "KoreanAnalyzer()";
        for (Object[] pair : PAIRS) {
          for (int i = 1; i <= 2; i++) { // the BMP character is the control
            int cp = (Integer) pair[i];
            List<String> got = terms(a, "한국 " + str(cp) + " 한국");
            if (got.equals(List.of("한국", "한국")) == false) {
              String label = String.format(Locale.ROOT, "U+%04X (%s)", cp, pair[0]);
              broken.add(name + ", " + label + ": " + got);
            }
          }
        }
      }
    }
    assertTrue(String.join("\n", broken), broken.isEmpty());
  }

  /**
   * A symbol followed by a letter: "★吉" gives [吉], so the emoji U+1F600 followed by the
   * supplementary letter U+20BB7 must give [U+20BB7]: drop the symbol, keep the letter.
   */
  public void testLetterAfterSupplementarySymbolIsKept() throws IOException {
    String letter = str(0x20BB7);
    try (Analyzer a = tokenizerOnly()) {
      assertEquals("control", List.of("한국", "吉", "한국"), terms(a, "한국 ★吉 한국"));
      assertEquals(
          "U+1F600 U+20BB7",
          List.of("한국", letter, "한국"),
          terms(a, "한국 " + str(0x1F600) + letter + " 한국"));
    }
  }
}
