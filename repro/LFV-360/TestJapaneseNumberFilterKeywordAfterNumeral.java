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

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;

/**
 * A keyword-marked token must come out of JapaneseNumberFilter unchanged, also when it directly
 * follows a numeral token. The analyzer is the keyword-marking analyzer of
 * TestJapaneseNumberFilter#testName, which protects the name 京一.
 */
public class TestJapaneseNumberFilterKeywordAfterNumeral extends BaseTokenStreamTestCase {

  private static CharArraySet protect(String word) {
    CharArraySet set = new CharArraySet(1, false);
    set.add(word);
    return set;
  }

  private static void assertTokens(
      TokenStream ts, String[] terms, int[] starts, int[] ends, boolean[] keywords)
      throws IOException {
    assertTokenStreamContents(ts, terms, starts, ends, null, null, null, null, keywords, true);
  }

  public void testKeywordTokenAfterNumeral() throws IOException {
    Analyzer keywordMarkingAnalyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer =
                new JapaneseTokenizer(
                    newAttributeFactory(), null, false, JapaneseTokenizer.Mode.SEARCH);
            return new TokenStreamComponents(
                tokenizer,
                new JapaneseNumberFilter(new SetKeywordMarkerFilter(tokenizer, protect("京一"))));
          }
        };
    // upstream testName: 京一 after the non-numeral 田中 is kept
    assertTokens(
        keywordMarkingAnalyzer.tokenStream("f", "田中京一"),
        new String[] {"田中", "京一"},
        new int[] {0, 2},
        new int[] {2, 4},
        new boolean[] {false, true});
    // the tokenizer gives 社員, の, 三, 京一: 三 becomes 3, the protected 京一 must stay 京一
    assertTokens(
        keywordMarkingAnalyzer.tokenStream("f", "社員の三京一"),
        new String[] {"社員", "の", "3", "京一"},
        new int[] {0, 2, 3, 4},
        new int[] {2, 3, 4, 6},
        new boolean[] {false, false, false, true});
    keywordMarkingAnalyzer.close();
  }

  /** The same without a tokenizer: the tokens 三 and 京一, 京一 protected. */
  public void testKeywordTokenAfterNumeralCanned() throws IOException {
    TokenStream ts =
        new JapaneseNumberFilter(
            new SetKeywordMarkerFilter(
                new CannedTokenStream(new Token("三", 0, 1), new Token("京一", 1, 3)), protect("京一")));
    assertTokens(
        ts,
        new String[] {"3", "京一"},
        new int[] {0, 1},
        new int[] {1, 3},
        new boolean[] {false, true});
  }
}
