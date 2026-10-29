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
import java.util.Locale;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ja.JapaneseTokenizer.Mode;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * With discardPunctuation=true (the default), "punctuation tokens should be dropped from the
 * output". The tokenizer decides by Unicode general category, so a character outside the BMP must
 * be treated like a BMP character of the same category.
 */
public class TestJapaneseDiscardSupplementaryPunctuation extends BaseTokenStreamTestCase {

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

  private static String label(int codePoint, Object category) {
    return String.format(Locale.ROOT, "U+%04X (%s)", codePoint, category);
  }

  private static Analyzer tokenizerOnly(Mode mode, int nBestCost) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        JapaneseTokenizer t = new JapaneseTokenizer(newAttributeFactory(), null, true, mode);
        t.setNBestCost(nBestCost);
        return new TokenStreamComponents(t);
      }
    };
  }

  /** Terms, and the position increment of each term after the first one. */
  private static List<String> terms(Analyzer a, String text, boolean withIncrements)
      throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      PositionIncrementAttribute posInc = ts.addAttribute(PositionIncrementAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        String inc =
            withIncrements && out.isEmpty() == false ? "/+" + posInc.getPositionIncrement() : "";
        out.add(term + inc);
      }
      ts.end();
    }
    return out;
  }

  /** "日本 X 日本" must give [日本, 日本] for every punctuation or symbol X, in every mode. */
  public void testSupplementaryPunctuationIsDiscarded() throws IOException {
    List<String> broken = new ArrayList<>();
    for (Mode mode : Mode.values()) {
      for (int nBestCost : new int[] {0, 2000}) {
        try (Analyzer a = tokenizerOnly(mode, nBestCost)) {
          for (Object[] pair : PAIRS) {
            for (int i = 1; i <= 2; i++) { // the BMP character is the control
              int cp = (Integer) pair[i];
              List<String> got = terms(a, "日本 " + str(cp) + " 日本", false);
              if (got.equals(List.of("日本", "日本")) == false) {
                String where = mode + (nBestCost > 0 ? " n-best" : "");
                broken.add(where + ", " + label(cp, pair[0]) + ": " + got);
              }
            }
          }
        }
      }
    }
    assertTrue(String.join("\n", broken), broken.isEmpty());
  }

  /**
   * The default JapaneseAnalyzer removes the kept token later (part-of-speech stop tag), but leaves
   * a position gap that the BMP character does not leave.
   */
  public void testDefaultAnalyzerLeavesNoPositionGap() throws IOException {
    List<String> broken = new ArrayList<>();
    try (Analyzer a = new JapaneseAnalyzer()) {
      for (Object[] pair : PAIRS) {
        for (int i = 1; i <= 2; i++) {
          int cp = (Integer) pair[i];
          List<String> got = terms(a, "日本" + str(cp) + "日本", true);
          if (got.equals(List.of("日本", "日本/+1")) == false) {
            broken.add("JapaneseAnalyzer, " + label(cp, pair[0]) + ": " + got);
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
    List<String> broken = new ArrayList<>();
    String letter = str(0x20BB7);
    for (Mode mode : Mode.values()) {
      try (Analyzer a = tokenizerOnly(mode, 0)) {
        List<String> control = terms(a, "日本 ★吉 日本", false);
        assertEquals(mode + " control", List.of("日本", "吉", "日本"), control);
        List<String> got = terms(a, "日本 " + str(0x1F600) + letter + " 日本", false);
        if (got.equals(List.of("日本", letter, "日本")) == false) {
          broken.add(mode + ", U+1F600 U+20BB7: " + got);
        }
      }
    }
    assertTrue(String.join("\n", broken), broken.isEmpty());
  }
}
