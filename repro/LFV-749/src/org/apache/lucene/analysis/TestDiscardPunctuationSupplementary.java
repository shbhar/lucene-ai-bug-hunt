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
package org.apache.lucene.analysis;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.ja.JapaneseTokenizer;
import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * JapaneseTokenizer and KoreanTokenizer with discardPunctuation=true (the default of both analyzers
 * and factories) must drop punctuation tokens: "@param discardPunctuation true if punctuation tokens
 * should be dropped from the output". The tokenizers decide what is punctuation from the Unicode
 * general category (ViterbiNBest/Viterbi#isPunctuation), but they look at one UTF-16 unit. A
 * character outside the BMP is seen as a SURROGATE and is never punctuation, so supplementary
 * punctuation (General_Category Po) and symbols (So, Sm) are kept, while BMP characters of the same
 * category are dropped.
 *
 * <p>Uses only public constructors.
 */
public class TestDiscardPunctuationSupplementary extends LuceneTestCase {

  // U+10100 AEGEAN WORD SEPARATOR LINE (Po), U+1E95E ADLAM INITIAL EXCLAMATION MARK (Po)
  private static final String SUPP_PUNCT_1 = new String(Character.toChars(0x10100));
  private static final String SUPP_PUNCT_2 = new String(Character.toChars(0x1E95E));
  // U+1F600 GRINNING FACE (So), U+1D6C1 MATHEMATICAL BOLD NABLA (Sm)
  private static final String SUPP_SYMBOL_SO = new String(Character.toChars(0x1F600));
  private static final String SUPP_SYMBOL_SM = new String(Character.toChars(0x1D6C1));
  // BMP counterparts with the same general categories: U+3001 (Po), U+FF01 (Po), U+2605 (So),
  // U+2207 NABLA (Sm)
  private static final String[] BMP_SAME_CATEGORY = {"、", "！", "★", "∇"};
  private static final String[] SUPP = {SUPP_PUNCT_1, SUPP_PUNCT_2, SUPP_SYMBOL_SO, SUPP_SYMBOL_SM};

  private static List<String> tokens(Tokenizer tk, String text) throws IOException {
    tk.setReader(new StringReader(text));
    CharTermAttribute term = tk.addAttribute(CharTermAttribute.class);
    List<String> out = new ArrayList<>();
    tk.reset();
    while (tk.incrementToken()) {
      out.add(term.toString());
    }
    tk.end();
    tk.close();
    return out;
  }

  private static Tokenizer japanese() {
    return new JapaneseTokenizer(null, true, JapaneseTokenizer.Mode.SEARCH);
  }

  private static Tokenizer korean() {
    return new KoreanTokenizer(); // discardPunctuation defaults to true
  }

  /** Control: BMP punctuation and symbols are dropped (passes on main). */
  public void testBmpPunctuationAndSymbolsAreDiscarded() throws IOException {
    for (int i = 0; i < BMP_SAME_CATEGORY.length; i++) {
      String c = BMP_SAME_CATEGORY[i];
      assertEquals(c, List.of("日本", "日本"), tokens(japanese(), "日本 " + c + " 日本"));
      assertEquals(c, List.of("한국", "한국"), tokens(korean(), "한국 " + c + " 한국"));
    }
  }

  /** Supplementary punctuation (General_Category Po) must be dropped like U+3001. */
  public void testJapaneseDiscardsSupplementaryPunctuation() throws IOException {
    assertEquals(List.of("日本", "日本"), tokens(japanese(), "日本 " + SUPP_PUNCT_1 + " 日本"));
    assertEquals(List.of("日本", "日本"), tokens(japanese(), "日本 " + SUPP_PUNCT_2 + " 日本"));
  }

  public void testKoreanDiscardsSupplementaryPunctuation() throws IOException {
    assertEquals(List.of("한국", "한국"), tokens(korean(), "한국 " + SUPP_PUNCT_1 + " 한국"));
    assertEquals(List.of("한국", "한국"), tokens(korean(), "한국 " + SUPP_PUNCT_2 + " 한국"));
  }

  /**
   * A supplementary symbol must be treated like a BMP symbol of the same general category: U+2605
   * (So) is dropped, so U+1F600 (So) must be dropped too; likewise U+2207 / U+1D6C1 (Sm).
   */
  public void testSameCategorySameTreatment() throws IOException {
    for (int i = 0; i < SUPP.length; i++) {
      String bmp = BMP_SAME_CATEGORY[i];
      String supp = SUPP[i];
      assertEquals(
          Character.getType(bmp.codePointAt(0)), Character.getType(supp.codePointAt(0)));
      assertEquals(
          "JapaneseTokenizer U+" + Integer.toHexString(supp.codePointAt(0)),
          tokens(japanese(), "日本 " + bmp + " 日本"),
          tokens(japanese(), "日本 " + supp + " 日本"));
      assertEquals(
          "KoreanTokenizer U+" + Integer.toHexString(supp.codePointAt(0)),
          tokens(korean(), "한국 " + bmp + " 한국"),
          tokens(korean(), "한국 " + supp + " 한국"));
    }
  }
}
