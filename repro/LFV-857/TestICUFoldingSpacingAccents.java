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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * Lucene's own folding table DiacriticFolding.txt ("## Remove diacritics", rule "[:Diacritic:] >")
 * maps U+005E, U+0060, U+00A8, U+00AF, U+00B4, U+00B7..00B8, U+02B0..034E and other diacritics to
 * nothing. Since 9.0 the build passes the NFKC data files to gennorm2 after this table, and a later
 * file overrides an earlier one, so the spacing accents that also have an NFKC mapping (acute,
 * diaeresis, macron, cedilla, breve, small tilde and others, e.g. "00B4>0020 0301") become a space
 * inside the term. Grave and circumflex have no NFKC mapping and are still removed. Up to 8.11 the
 * table won for all of these accents.
 */
public class TestICUFoldingSpacingAccents extends BaseTokenStreamTestCase {

  private static Analyzer keywordFolding() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new KeywordTokenizer();
        return new TokenStreamComponents(tokenizer, new ICUFoldingFilter(tokenizer));
      }
    };
  }

  private static Analyzer standardFolding() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new StandardTokenizer();
        return new TokenStreamComponents(tokenizer, new ICUFoldingFilter(tokenizer));
      }
    };
  }

  /** Control (passes): spacing accents without an NFKC mapping are removed. */
  public void testGraveAndCircumflexAreRemoved() throws Exception {
    try (Analyzer a = keywordFolding()) {
      assertAnalyzesTo(a, "a`b", new String[] {"ab"});
      assertAnalyzesTo(a, "a^b", new String[] {"ab"});
    }
  }

  /** Acute, diaeresis, macron, cedilla, breve, small tilde: same table, same rule. */
  public void testOtherSpacingAccentsAreRemoved() throws Exception {
    try (Analyzer a = keywordFolding()) {
      for (String accent : new String[] {"´", "¨", "¯", "¸", "˘", "˜"}) {
        assertAnalyzesTo(a, "a" + accent + "b", new String[] {"ab"});
      }
    }
  }

  /** An acute accent typed as an apostrophe, in a keyword-normalized field. */
  public void testAcuteAsApostropheGivesOneWord() throws Exception {
    try (Analyzer a = keywordFolding()) {
      assertAnalyzesTo(a, "O´Brien", new String[] {"obrien"});
    }
  }

  /** The spacing katakana voiced sound mark stays inside the token with StandardTokenizer. */
  public void testKatakanaVoicedSoundMarkWithStandardTokenizer() throws Exception {
    try (Analyzer a = standardFolding()) {
      assertAnalyzesTo(a, "カ゛", new String[] {"カ"});
    }
  }
}
