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

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;

/**
 * lucene/analysis/icu/src/data/utr30/DiacriticFolding.txt ("## Remove diacritics", generated from
 * "# Rule: [:Diacritic:] >") lists the spacing accents for removal:
 *
 * <pre>
 * 005E>  0060>  00A8>  00AF>  00B4>  00B7..00B8>  02B0..034E>
 * </pre>
 *
 * ICUFoldingFilter removes GRAVE ACCENT and CIRCUMFLEX ACCENT, but turns ACUTE ACCENT, DIAERESIS,
 * MACRON, CEDILLA, BREVE and SMALL TILDE into a space: for these code points the NFKC data
 * (nfkc.txt / nfkc_cf.txt, "00B4>0020 0301") is passed to gennorm2 after Lucene's own tables and
 * overrides them. Up to 8.11 the same table removed all of them.
 */
public class TestICUFoldingTailoringPrecedence extends BaseTokenStreamTestCase {
  private Analyzer a;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    a =
        new Analyzer() {
          @Override
          public TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new MockTokenizer(MockTokenizer.WHITESPACE, false);
            return new TokenStreamComponents(tokenizer, new ICUFoldingFilter(tokenizer));
          }
        };
  }

  @Override
  public void tearDown() throws Exception {
    a.close();
    super.tearDown();
  }

  /** Control (passes): spacing accents without an NFKC mapping are removed (lines 37-38). */
  public void testGraveAndCircumflexAreRemoved() throws IOException {
    assertAnalyzesTo(a, "a`b a^b", new String[] {"ab", "ab"});
  }

  /** Lines 39-43: the other spacing accents are removed the same way (fails: "a b"). */
  public void testOtherSpacingAccentsAreRemoved() throws IOException {
    assertAnalyzesTo(
        a, "a´b a¨b a¯b a¸b a˘b a˜b", new String[] {"ab", "ab", "ab", "ab", "ab", "ab"});
  }

  /** The acute accent used as an apostrophe, in a keyword-like field: no space in the term. */
  public void testAcuteAsApostropheKeepsOneTerm() throws IOException {
    assertAnalyzesTo(a, "O´Brien", new String[] {"obrien"});
  }
}
