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
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.ko.KoreanTokenizer.DecompoundMode;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * KoreanTokenizer with outputUnknownUnigrams=true and discardPunctuation=true (the default) must
 * still drop punctuation and whitespace, as it does with outputUnknownUnigrams=false.
 */
public class TestKoreanUnigramsDiscardPunctuation extends BaseTokenStreamTestCase {

  private static Analyzer analyzer(boolean outputUnknownUnigrams) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t =
            new KoreanTokenizer(
                newAttributeFactory(), null, DecompoundMode.DISCARD, outputUnknownUnigrams, true);
        return new TokenStreamComponents(t, t);
      }
    };
  }

  /** Control: without unigrams the punctuation and whitespace are dropped. */
  public void testControlWithoutUnigrams() throws IOException {
    try (Analyzer a = analyzer(false)) {
      assertAnalyzesTo(a, "가격: $100", new String[] {"가격", "100"});
      assertAnalyzesTo(a, "다음\n\n문장", new String[] {"다음", "문장"});
    }
  }

  /** "$" is punctuation (a currency symbol); it must not become a token. */
  public void testCurrencySymbolWithUnigrams() throws IOException {
    try (Analyzer a = analyzer(true)) {
      assertAnalyzesTo(a, "가격: $100", new String[] {"가격", "1", "0", "0"});
    }
  }

  /** A paragraph break must not become two "\n" tokens. */
  public void testNewlinesWithUnigrams() throws IOException {
    try (Analyzer a = analyzer(true)) {
      assertAnalyzesTo(a, "다음\n\n문장", new String[] {"다음", "문장"});
    }
  }

  /** The same through the SPI factory with only outputUnknownUnigrams="true". */
  public void testFactory() throws IOException {
    try (Analyzer a =
        CustomAnalyzer.builder()
            .withTokenizer("korean", "outputUnknownUnigrams", "true")
            .build()) {
      assertAnalyzesTo(
          a,
          "test@example.com",
          new String[] {
            "t", "e", "s", "t", "e", "x", "a", "m", "p", "l", "e", "c", "o", "m"
          });
    }
  }
}
