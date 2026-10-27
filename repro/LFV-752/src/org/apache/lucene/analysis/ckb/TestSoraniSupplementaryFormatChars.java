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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SoraniNormalizer javadoc: "Harakat, tatweel, and formatting characters such as directional controls
 * are removed." It tests Character.getType(char) == FORMAT on one UTF-16 unit, so a formatting
 * character outside the BMP (General_Category Cf, for example U+E0001 LANGUAGE TAG, U+E0067 TAG
 * LATIN SMALL LETTER G, U+1BCA0 SHORTHAND FORMAT LETTER OVERLAP) is seen as two surrogates and is
 * kept, while BMP formatting characters (U+200F, U+200E, U+2066) are removed.
 *
 * <p>Uses only public API (SoraniNormalizationFilter, SoraniAnalyzer).
 */
public class TestSoraniSupplementaryFormatChars extends LuceneTestCase {
  private static final String KURD = "کورد"; // Arabic-script letters
  private static final String STAN = "ستان";
  private static final int[] BMP_FORMAT = {0x200F, 0x200E, 0x2066};
  private static final int[] SUPP_FORMAT = {0xE0001, 0xE0067, 0x1BCA0};

  private static String normalize(String s) throws IOException {
    Tokenizer tk = new KeywordTokenizer();
    tk.setReader(new StringReader(s));
    try (TokenStream ts = new SoraniNormalizationFilter(tk)) {
      CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      assertTrue(ts.incrementToken());
      String out = t.toString();
      ts.end();
      return out;
    }
  }

  private static List<String> analyze(Analyzer a, String s) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", s)) {
      CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(t.toString());
      }
      ts.end();
    }
    return out;
  }

  private static String with(int cp) {
    return KURD + new String(Character.toChars(cp)) + STAN;
  }

  /** Control: BMP formatting characters are removed (passes on main). */
  public void testBmpFormatCharactersRemoved() throws IOException {
    for (int cp : BMP_FORMAT) {
      assertEquals(Character.FORMAT, Character.getType(cp));
      assertEquals("U+" + Integer.toHexString(cp), KURD + STAN, normalize(with(cp)));
    }
  }

  /** Supplementary formatting characters must be removed too. */
  public void testSupplementaryFormatCharactersRemoved() throws IOException {
    for (int cp : SUPP_FORMAT) {
      assertEquals(Character.FORMAT, Character.getType(cp));
      assertEquals("U+" + Integer.toHexString(cp), KURD + STAN, normalize(with(cp)));
    }
  }

  /** End to end: SoraniAnalyzer must analyze both spellings to the same terms. */
  public void testSoraniAnalyzerTreatsFormatCharactersAlike() throws IOException {
    try (Analyzer a = new SoraniAnalyzer()) {
      List<String> plain = analyze(a, KURD + STAN);
      for (int cp : BMP_FORMAT) {
        assertEquals("BMP U+" + Integer.toHexString(cp), plain, analyze(a, with(cp)));
      }
      for (int cp : SUPP_FORMAT) {
        assertEquals("U+" + Integer.toHexString(cp), plain, analyze(a, with(cp)));
      }
    }
  }
}
