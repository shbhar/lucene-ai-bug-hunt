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
package org.apache.lucene.analysis.miscellaneous;

import java.io.IOException;
import java.io.StringReader;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;

/**
 * CapitalizationFilter changes case one UTF-16 char at a time, so letters outside the BMP (Adlam,
 * Osage, Deseret, ...) are neither capitalized nor lower-cased, unlike the BMP letters around
 * them.
 */
public class TestCapitalizationFilterSupplementaryLetters extends BaseTokenStreamTestCase {

  private static String s(int... codePoints) {
    return new String(codePoints, 0, codePoints.length);
  }

  // ADLAM SMALL/CAPITAL LETTER ALIF and DAALI; OSAGE SMALL/CAPITAL LETTER A
  private static final int ADLAM_SMALL_ALIF = 0x1E922, ADLAM_CAPITAL_ALIF = 0x1E900;
  private static final int ADLAM_SMALL_DAALI = 0x1E923, ADLAM_CAPITAL_DAALI = 0x1E901;
  private static final int OSAGE_SMALL_A = 0x104D8, OSAGE_CAPITAL_A = 0x104B0;

  private static String codePoints(String s) {
    StringBuilder sb = new StringBuilder();
    s.codePoints().forEach(c -> sb.append(sb.length() == 0 ? "" : " ").append(String.format("U+%04X", c)));
    return sb.toString();
  }

  private static void assertCapitalizes(String input, String expected) throws IOException {
    Tokenizer tok = new MockTokenizer(MockTokenizer.WHITESPACE, false);
    tok.setReader(new StringReader(input));
    TokenStream ts = new CapitalizationFilter(tok);
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    ts.reset();
    assertTrue(ts.incrementToken());
    String actual = term.toString();
    assertFalse(ts.incrementToken());
    ts.end();
    ts.close();
    assertEquals("input " + codePoints(input), codePoints(expected), codePoints(actual));
  }

  /** Control: the same shapes with BMP letters. */
  public void testBmpLetters() throws IOException {
    assertCapitalizes("ab", "Ab");
    assertCapitalizes("AB", "Ab");
  }

  /** "make the first letter capital": a lower-case Adlam word gets no capital. */
  public void testFirstLetterOutsideBmpIsCapitalized() throws IOException {
    assertCapitalizes(
        s(ADLAM_SMALL_ALIF, ADLAM_SMALL_DAALI), s(ADLAM_CAPITAL_ALIF, ADLAM_SMALL_DAALI));
  }

  /** "and the rest lower case": an all-capital Adlam word stays all capitals. */
  public void testRestOutsideBmpIsLowerCased() throws IOException {
    assertCapitalizes(
        s(ADLAM_CAPITAL_ALIF, ADLAM_CAPITAL_DAALI), s(ADLAM_CAPITAL_ALIF, ADLAM_SMALL_DAALI));
  }

  /** Mixed token: the BMP letter is lower-cased, the Osage capital after it is not. */
  public void testMixedBmpAndSupplementary() throws IOException {
    assertCapitalizes(s('a', 'B', OSAGE_CAPITAL_A), s('A', 'b', OSAGE_SMALL_A));
  }
}
