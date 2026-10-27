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
 * KoreanNumberFilter, same defect: KeywordAttribute is checked only on the first token of a number.
 * The analyzer is the keyword-marking analyzer of the upstream TestKoreanNumberFilter#testName (without
 * its user dictionary), which protects 경일.
 */
public class TestKoreanNumberFilterKeywordInsideNumber extends BaseTokenStreamTestCase {

  public void testKeywordTokenAfterNumeralIsLeftUntouched() throws IOException {
    Analyzer keywordMarkingAnalyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            CharArraySet set = new CharArraySet(1, false);
            set.add("경일");
            Tokenizer tokenizer =
                new KoreanTokenizer(
                    newAttributeFactory(), null, KoreanTokenizer.DEFAULT_DECOMPOUND, false, false);
            TokenStream stream = new KoreanPartOfSpeechStopFilter(tokenizer, EnumSet.of(POS.Tag.SP));
            return new TokenStreamComponents(
                tokenizer, new KoreanNumberFilter(new SetKeywordMarkerFilter(stream, set)));
          }
        };
    assertAnalyzesTo(keywordMarkingAnalyzer, "전중경일", new String[] {"전중", "경일"});
    // the tokenizer gives 전중, 삼, 경일: the protected 경일 must stay 경일
    assertAnalyzesTo(
        keywordMarkingAnalyzer,
        "전중삼경일",
        new String[] {"전중", "3", "경일"},
        new int[] {0, 2, 3},
        new int[] {2, 3, 5});
    keywordMarkingAnalyzer.close();
  }
}
