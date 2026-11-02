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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * discardPunctuation is documented as "true if punctuation tokens should be dropped from the
 * output", but JapaneseTokenizer tests only the first character of a dictionary token. The system
 * dictionary has four nouns that start with a fullwidth parenthesis, the company-type
 * abbreviations （株） （有） （社） （財） (part of speech 名詞-一般), and with the default
 * discardPunctuation=true they are discarded whole, Han letter included.
 */
public class TestDiscardPunctuationDictionaryNoun extends BaseTokenStreamTestCase {

  private static boolean isPunct(char ch) {
    // same character classes as the tokenizer's punctuation test
    switch (Character.getType(ch)) {
      case Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR,
          Character.CONTROL, Character.FORMAT, Character.DASH_PUNCTUATION,
          Character.START_PUNCTUATION, Character.END_PUNCTUATION,
          Character.CONNECTOR_PUNCTUATION, Character.OTHER_PUNCTUATION, Character.MATH_SYMBOL,
          Character.CURRENCY_SYMBOL, Character.MODIFIER_SYMBOL, Character.OTHER_SYMBOL,
          Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION:
        return true;
      default:
        return false;
    }
  }

  private static List<String> tokens(boolean discardPunctuation, String text, boolean dropAllPunct)
      throws Exception {
    Tokenizer t = new JapaneseTokenizer(null, discardPunctuation, JapaneseTokenizer.Mode.SEARCH);
    t.setReader(new StringReader(text));
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    OffsetAttribute off = t.addAttribute(OffsetAttribute.class);
    List<String> out = new ArrayList<>();
    t.reset();
    while (t.incrementToken()) {
      String s = term.toString();
      boolean allPunct = true;
      for (int i = 0; i < s.length(); i++) allPunct &= isPunct(s.charAt(i));
      if (dropAllPunct && allPunct) continue;
      out.add(s + "[" + off.startOffset() + "," + off.endOffset() + ")");
    }
    t.end();
    t.close();
    return out;
  }

  private static void check(String text) throws Exception {
    // expected: the discardPunctuation=false output without the tokens made only of punctuation
    assertEquals(text, tokens(false, text, true), tokens(true, text, false));
  }

  public void testStockCompanyAfterName() throws Exception {
    check("東芝（株）"); // Toshiba (Co., Ltd.)
  }

  public void testStockCompanyInSentence() throws Exception {
    check("当社は（株）です");
  }

  public void testLimitedCompanyBeforeName() throws Exception {
    check(" （有） 山田商店");
  }

  /** Control: an ordinary sentence with punctuation behaves as documented. Passes. */
  public void testControlOrdinaryPunctuation() throws Exception {
    check("東京、大阪。「京都」");
  }
}
