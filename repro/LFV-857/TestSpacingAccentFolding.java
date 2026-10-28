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
package org.apache.lucene.analysis.icu;

import static org.junit.Assert.assertEquals;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.Test;

/**
 * Independent check: lucene/analysis/icu/src/data/utr30/DiacriticFolding.txt ("## Remove diacritics",
 * "# Rule: [:Diacritic:] >") maps the spacing accents U+005E, U+0060, U+00A8, U+00AF, U+00B4,
 * U+00B8, U+02D8, U+02DC to nothing, and ICUFoldingFilter's javadoc lists "Diacritic removal". So a
 * spacing accent inside a word should disappear, the same way for all of them.
 *
 * <p>Plain JUnit 4 and public API only (ICUFoldingFilter, ICUFoldingFilter.NORMALIZER,
 * KeywordTokenizer), so the same source compiles against 8.x, 9.x, 10.x and main.
 */
public class TestSpacingAccentFolding {

  private static String hex(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); ) {
      int cp = s.codePointAt(i);
      if (sb.length() > 0) sb.append(' ');
      sb.append(String.format("U+%04X", cp));
      i += Character.charCount(cp);
    }
    return "[" + sb + "]";
  }

  private static void assertFolds(String input, String expected) {
    String actual = ICUFoldingFilter.NORMALIZER.normalize(input);
    assertEquals(
        "NORMALIZER.normalize(" + hex(input) + ") expected " + hex(expected) + " got " + hex(actual),
        expected,
        actual);
  }

  private static List<String> terms(String text) throws Exception {
    Tokenizer tok = new KeywordTokenizer();
    tok.setReader(new StringReader(text));
    List<String> out = new ArrayList<>();
    try (TokenStream ts = new ICUFoldingFilter(tok)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  /** Control: grave and circumflex (no NFKC decomposition) are removed. */
  @Test
  public void testControlGraveAndCircumflexRemoved() {
    assertFolds("a`b", "ab");
    assertFolds("a^b", "ab");
  }

  /** Control: combining acute (U+0301) is removed. */
  @Test
  public void testControlCombiningAcuteRemoved() {
    assertFolds("áb", "ab");
  }

  @Test
  public void testSpacingAcuteRemoved() {
    assertFolds("a´b", "ab");
  }

  @Test
  public void testSpacingDiaeresisRemoved() {
    assertFolds("a¨b", "ab");
  }

  @Test
  public void testSpacingMacronRemoved() {
    assertFolds("a¯b", "ab");
  }

  @Test
  public void testSpacingCedillaRemoved() {
    assertFolds("a¸b", "ab");
  }

  @Test
  public void testSpacingBreveRemoved() {
    assertFolds("a˘b", "ab");
  }

  @Test
  public void testSpacingSmallTildeRemoved() {
    assertFolds("a˜b", "ab");
  }

  /** Through the TokenFilter: one keyword token must stay one word without an embedded space. */
  @Test
  public void testTokenFilterAcuteAsApostrophe() throws Exception {
    List<String> got = terms("O´Brien");
    assertEquals("terms of O´Brien: " + got, List.of("obrien"), got);
  }
}
