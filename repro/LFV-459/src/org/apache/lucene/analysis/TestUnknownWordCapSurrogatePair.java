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

import java.io.StringReader;
import org.apache.lucene.analysis.ja.JapaneseTokenizer;
import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.UnicodeUtil;

public class TestUnknownWordCapSurrogatePair extends LuceneTestCase {

  private static void assertTermsValidUTF16(Tokenizer t, String text) throws Exception {
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    t.setReader(new StringReader(text));
    t.reset();
    while (t.incrementToken()) {
      // expected: every term is valid UTF-16; actual: the 1024-char unknown-word cut splits a pair
      assertTrue(
          "term of length " + term.length() + " is not valid UTF-16",
          UnicodeUtil.validUTF16String(term));
    }
    t.end();
    t.close();
  }

  public void testJapaneseTokenizer() throws Exception {
    // 1023 x U+0E01 (Thai), then U+1F600: its high surrogate is char 1023
    assertTermsValidUTF16(
        new JapaneseTokenizer(null, true, JapaneseTokenizer.Mode.SEARCH),
        "\u0E01".repeat(1023) + "\uD83D\uDE00");
  }

  public void testKoreanTokenizer() throws Exception {
    // 342 x (U+1F441 U+FE0F), 3 chars each: the high surrogate of the last U+1F441 is char 1023
    assertTermsValidUTF16(new KoreanTokenizer(), "\uD83D\uDC41\uFE0F".repeat(342));
  }
}
