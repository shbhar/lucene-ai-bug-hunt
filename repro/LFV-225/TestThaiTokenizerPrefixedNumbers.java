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
package org.apache.lucene.analysis.th;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Random;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/** Fix checks: tokens and offsets for numbers glued to a leading '.', '#' or currency sign. */
public class TestThaiTokenizerPrefixedNumbers extends BaseTokenStreamTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    assumeTrue("needs the JRE's Thai BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
  }

  private static Analyzer tokenizerOnly(CharArraySet dict) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new ThaiTokenizer(dict));
      }
    };
  }

  public void testPrefixedNumbersKeepTheNumber() throws IOException {
    try (Analyzer a = tokenizerOnly(null)) {
      assertAnalyzesTo(
          a, "ราคา ฿1,500", new String[] {"ราคา", "1,500"}, new int[] {0, 6}, new int[] {4, 11});
      assertAnalyzesTo(
          a, "พ.ศ.2567", new String[] {"พ", "ศ", "2567"}, new int[] {0, 2, 4}, new int[] {1, 3, 8});
      assertAnalyzesTo(a, "ข้อ #3", new String[] {"ข้อ", "3"}, new int[] {0, 5}, new int[] {3, 6});
      assertAnalyzesTo(a, "ค่า .5", new String[] {"ค่า", "5"}, new int[] {0, 5}, new int[] {3, 6});
      assertAnalyzesTo(
          a, "page.42", new String[] {"page", "42"}, new int[] {0, 5}, new int[] {4, 7});
    }
  }

  public void testPunctuationOnlySegmentsAreStillSkipped() throws IOException {
    try (Analyzer a = tokenizerOnly(null)) {
      assertAnalyzesTo(
          a, "ราคา - บาท", new String[] {"ราคา", "บาท"}, new int[] {0, 7}, new int[] {4, 10});
      assertAnalyzesTo(a, "...", new String[0]);
      assertAnalyzesTo(
          a,
          "ราคา 1,500 บาท",
          new String[] {"ราคา", "1,500", "บาท"},
          new int[] {0, 5, 11},
          new int[] {4, 10, 14});
    }
  }

  /** The dictionary lookup now starts at the first letter or digit of the segment. */
  public void testUserDictionaryAfterPrefix() throws IOException {
    CharArraySet dict = new CharArraySet(List.of("1,500บาท"), false);
    try (Analyzer a = tokenizerOnly(dict)) {
      assertAnalyzesTo(a, "฿1,500บาท", new String[] {"1,500บาท"}, new int[] {1}, new int[] {9});
    }
  }

  /** Oracle: every letter or digit of the input lies inside some token. */
  public void testNoLetterOrDigitIsLost() throws IOException {
    String[] alphabet = {"ก", "า", "่", "1", "x", ".", ",", "#", "฿", "$", " ", "-"};
    Random r = random();
    for (int i = 0; i < 2000; i++) {
      StringBuilder sb = new StringBuilder();
      int len = 1 + r.nextInt(10);
      for (int j = 0; j < len; j++) {
        sb.append(alphabet[r.nextInt(alphabet.length)]);
      }
      String text = sb.toString();
      boolean[] covered = new boolean[text.length()];
      Tokenizer t = new ThaiTokenizer();
      t.setReader(new StringReader(text));
      try (TokenStream ts = t) {
        OffsetAttribute off = ts.addAttribute(OffsetAttribute.class);
        ts.reset();
        while (ts.incrementToken()) {
          for (int k = off.startOffset(); k < off.endOffset(); k++) {
            covered[k] = true;
          }
        }
        ts.end();
      }
      for (int k = 0; k < text.length(); k++) {
        if (Character.isLetterOrDigit(text.charAt(k)) && covered[k] == false) {
          fail("lost '" + text.charAt(k) + "' at " + k + " in '" + text + "'");
        }
      }
    }
  }

  public void testRandomStrings() throws IOException {
    try (Analyzer a = tokenizerOnly(null)) {
      checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER);
    }
  }
}
