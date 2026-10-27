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
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * JapaneseNumberFilter checks KeywordAttribute only on the first token of a number. A keyword token
 * that directly follows a numeral token is merged into the number and normalized. The analyzer is the
 * keyword-marking analyzer of the upstream TestJapaneseNumberFilter#testName, which protects the name
 * 京一 (Kyōichi).
 */
public class TestJapaneseNumberFilterKeywordInsideNumber extends BaseTokenStreamTestCase {

  public void testKeywordTokenAfterNumeralIsLeftUntouched() throws IOException {
    Analyzer keywordMarkingAnalyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            CharArraySet set = new CharArraySet(1, false);
            set.add("京一");
            Tokenizer tokenizer =
                new JapaneseTokenizer(newAttributeFactory(), null, false, JapaneseTokenizer.Mode.SEARCH);
            return new TokenStreamComponents(
                tokenizer, new JapaneseNumberFilter(new SetKeywordMarkerFilter(tokenizer, set)));
          }
        };
    // the upstream test: 京一 right after a non-numeral token is kept (passes)
    assertAnalyzesTo(keywordMarkingAnalyzer, "田中京一", new String[] {"田中", "京一"});
    // the tokenizer gives 社員, の, 三, 京一: the protected 京一 must stay 京一
    assertAnalyzesTo(
        keywordMarkingAnalyzer,
        "社員の三京一",
        new String[] {"社員", "の", "3", "京一"},
        new int[] {0, 2, 3, 4},
        new int[] {2, 3, 4, 6});
    keywordMarkingAnalyzer.close();
  }
}
