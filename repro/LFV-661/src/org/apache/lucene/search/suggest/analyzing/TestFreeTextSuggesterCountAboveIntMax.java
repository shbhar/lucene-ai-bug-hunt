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
package org.apache.lucene.search.suggest.analyzing;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TermFrequencyAttribute;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * An n-gram whose total count exceeds Integer.MAX_VALUE. The count is reached cheaply with a
 * custom term frequency (TermFrequencyAttribute; FreeTextSuggester indexes DOCS_AND_FREQS); a
 * large enough training text with a stock analyzer reaches the same count.
 */
public class TestFreeTextSuggesterCountAboveIntMax extends LuceneTestCase {

  /** Token "word^N" is indexed as "word" with term frequency N in its entry. */
  private static final class FreqSuffixFilter extends TokenFilter {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final TermFrequencyAttribute freqAtt = addAttribute(TermFrequencyAttribute.class);

    FreqSuffixFilter(TokenStream in) {
      super(in);
    }

    @Override
    public boolean incrementToken() throws IOException {
      if (input.incrementToken() == false) {
        return false;
      }
      String term = termAtt.toString();
      int caret = term.indexOf('^');
      if (caret >= 0) {
        freqAtt.setTermFrequency(Integer.parseInt(term.substring(caret + 1)));
        termAtt.setLength(caret);
      }
      return true;
    }
  }

  private static Analyzer freqAnalyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new WhitespaceTokenizer();
        return new TokenStreamComponents(tokenizer, new FreqSuffixFilter(tokenizer));
      }
    };
  }

  public void testCountAboveIntMaxKeepsScoreInRange() throws Exception {
    Analyzer a = freqAnalyzer();
    FreeTextSuggester sug = new FreeTextSuggester(a, a, 1);
    // count(a) = Integer.MAX_VALUE + 1, count(ab) = 1, 2^31 + 1 tokens in total:
    sug.build(entries("a^" + Integer.MAX_VALUE, "a", "ab"));
    List<LookupResult> results = sug.lookup("a", 2);
    assertEquals(2, results.size());
    // Class javadoc: value = Long.MAX_VALUE * score, score ranges from 0.0 to 1.0
    for (LookupResult result : results) {
      assertTrue(result.key + "/" + result.value, result.value >= 0);
    }
    // The far more frequent word is suggested first:
    assertEquals("a", results.get(0).key.toString());
    a.close();
  }

  /** Control: at exactly Integer.MAX_VALUE the count still fits and the result is right. */
  public void testCountAtIntMaxControl() throws Exception {
    Analyzer a = freqAnalyzer();
    FreeTextSuggester sug = new FreeTextSuggester(a, a, 1);
    sug.build(entries("a^" + (Integer.MAX_VALUE - 1), "a", "ab"));
    List<LookupResult> results = sug.lookup("a", 2);
    assertEquals("a", results.get(0).key.toString());
    assertTrue(results.get(0).value >= 0);
    a.close();
  }

  private static InputIterator entries(String... texts) {
    return new InputIterator() {
      int upto;

      @Override
      public BytesRef next() {
        return upto < texts.length ? new BytesRef(texts[upto++]) : null;
      }

      @Override
      public long weight() {
        return 1;
      }

      @Override
      public BytesRef payload() {
        return null;
      }

      @Override
      public boolean hasPayloads() {
        return false;
      }

      @Override
      public Set<BytesRef> contexts() {
        return null;
      }

      @Override
      public boolean hasContexts() {
        return false;
      }
    };
  }
}
