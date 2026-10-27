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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.util.QueryBuilder;

/**
 * With outputUnknownUnigrams=true, discardPunctuation=true (the default) must still drop
 * punctuation and whitespace, as it does with outputUnknownUnigrams=false.
 */
public class TestKoreanTokenizerUnigramPunctuation extends BaseTokenStreamTestCase {

  private static Analyzer tokenizer(boolean outputUnknownUnigrams, boolean discardPunctuation) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t =
            new KoreanTokenizer(
                newAttributeFactory(),
                null,
                DecompoundMode.DISCARD,
                outputUnknownUnigrams,
                discardPunctuation);
        return new TokenStreamComponents(t, t);
      }
    };
  }

  /** Control: without unigrams, punctuation and whitespace are dropped. */
  public void testControlWithoutUnigrams() throws IOException {
    try (Analyzer a = tokenizer(false, true)) {
      assertAnalyzesTo(a, "가격: $100", new String[] {"가격", "100"});
      assertAnalyzesTo(a, "다음\n\n문장", new String[] {"다음", "문장"});
      assertAnalyzesTo(a, "50% 할인", new String[] {"50", "할인"});
    }
  }

  /** Control: with discardPunctuation=false, unigram mode keeps punctuation and whitespace. */
  public void testControlKeepPunctuation() throws IOException {
    try (Analyzer a = tokenizer(true, false)) {
      assertAnalyzesTo(a, "가격: $100", new String[] {"가격", ":", " ", "$", "1", "0", "0"});
    }
  }

  /** The "$" of a price must not become a token. */
  public void testUnigramsDropCurrencySymbol() throws IOException {
    try (Analyzer a = tokenizer(true, true)) {
      assertAnalyzesTo(
          a,
          "가격: $100",
          new String[] {"가격", "1", "0", "0"},
          new int[] {0, 5, 6, 7},
          new int[] {2, 6, 7, 8});
    }
  }

  /** Line breaks, a space after a symbol, a doubled space and a tab must not become tokens. */
  public void testUnigramsDropWhitespace() throws IOException {
    try (Analyzer a = tokenizer(true, true)) {
      assertAnalyzesTo(a, "다음\n\n문장", new String[] {"다음", "문장"});
      assertAnalyzesTo(a, "50% 할인", new String[] {"5", "0", "할인"});
      assertAnalyzesTo(a, "ab  cd", new String[] {"a", "b", "c", "d"});
      assertAnalyzesTo(a, "a\tb", new String[] {"a", "b"});
    }
  }

  /** The 'korean' factory with only outputUnknownUnigrams="true". */
  public void testFactory() throws IOException {
    try (Analyzer a =
        CustomAnalyzer.builder().withTokenizer("korean", "outputUnknownUnigrams", "true").build()) {
      assertAnalyzesTo(
          a,
          "test@example.com",
          new String[] {"t", "e", "s", "t", "e", "x", "a", "m", "p", "l", "e", "c", "o", "m"});
    }
  }

  /** KoreanAnalyzer with unigrams: the exact phrase "가격 100" must match "가격: $100". */
  public void testExactPhraseAcrossSymbol() throws IOException {
    try (Analyzer a =
            new KoreanAnalyzer(
                null,
                DecompoundMode.DISCARD,
                KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS,
                true);
        Directory dir = newDirectory()) {
      try (RandomIndexWriter w = new RandomIndexWriter(random(), dir, newIndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(newTextField("body", "가격: $100", Field.Store.NO));
        w.addDocument(doc);
        try (IndexReader r = w.getReader()) {
          IndexSearcher s = newSearcher(r);
          Query q = new QueryBuilder(a).createPhraseQuery("body", "가격 100");
          assertEquals(q.toString(), 1, s.count(q));
        }
      }
    }
  }
}
