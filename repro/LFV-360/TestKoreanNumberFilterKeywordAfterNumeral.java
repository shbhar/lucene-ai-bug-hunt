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
import java.util.EnumSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * A keyword-marked token must come out of KoreanNumberFilter unchanged, also when it directly
 * follows a numeral token. The analyzer is the keyword-marking analyzer of
 * TestKoreanNumberFilter#testName (without its user dictionary), which protects 경일.
 */
public class TestKoreanNumberFilterKeywordAfterNumeral extends BaseTokenStreamTestCase {

  public void testKeywordTokenAfterNumeral() throws IOException {
    Analyzer keywordMarkingAnalyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            CharArraySet set = new CharArraySet(1, false);
            set.add("경일");
            Tokenizer tokenizer =
                new KoreanTokenizer(
                    newAttributeFactory(), null, KoreanTokenizer.DEFAULT_DECOMPOUND, false, false);
            TokenStream stream =
                new KoreanPartOfSpeechStopFilter(tokenizer, EnumSet.of(POS.Tag.SP));
            return new TokenStreamComponents(
                tokenizer, new KoreanNumberFilter(new SetKeywordMarkerFilter(stream, set)));
          }
        };
    // upstream testName: 경일 after the non-numeral 전중 is kept
    assertTokenStreamContents(
        keywordMarkingAnalyzer.tokenStream("f", "전중경일"),
        new String[] {"전중", "경일"},
        new int[] {0, 2},
        new int[] {2, 4},
        null,
        null,
        null,
        null,
        new boolean[] {false, true},
        true);
    // the name 오경일 (surname 오, given name 경일): the tokenizer gives 오, 경일. 오 alone still
    // becomes 5 (the filter does not use context), but the protected 경일 must stay 경일
    assertTokenStreamContents(
        keywordMarkingAnalyzer.tokenStream("f", "오경일"),
        new String[] {"5", "경일"},
        new int[] {0, 1},
        new int[] {1, 3},
        null,
        null,
        null,
        null,
        new boolean[] {false, true},
        true);
    keywordMarkingAnalyzer.close();
  }
}
