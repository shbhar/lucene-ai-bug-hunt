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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * KoreanTokenizer caps an unknown word at 1024 UTF-16 chars. A run of 400 "eye + VS16" emoji (3
 * chars each; the variation selector keeps them in one unknown word) has the high surrogate of an
 * emoji at char 1023, so the cut falls inside the surrogate pair.
 */
public class TestKoreanUnknownWordSurrogatePair extends BaseTokenStreamTestCase {

  private static boolean wellFormed(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
          i++;
        } else {
          return false;
        }
      } else if (Character.isLowSurrogate(c)) {
        return false;
      }
    }
    return true;
  }

  public void testEmojiRunWithVariationSelectors() throws IOException {
    String eye = new String(Character.toChars(0x1F441)) + "️";
    try (Analyzer a = new KoreanAnalyzer();
        TokenStream ts = a.tokenStream("f", eye.repeat(400))) {
      CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      int i = 0;
      while (ts.incrementToken()) {
        assertTrue(
            "term " + i + " (length " + termAtt.length() + ") has an unpaired surrogate",
            wellFormed(termAtt.toString()));
        i++;
      }
      ts.end();
    }
  }
}
