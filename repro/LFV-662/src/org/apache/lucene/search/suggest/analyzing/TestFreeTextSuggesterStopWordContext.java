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

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A query whose last word follows a removed stop word makes FreeTextSuggester look up the count of
 * a context made only of the hole filler "_", which the model never stores.
 */
public class TestFreeTextSuggesterStopWordContext extends LuceneTestCase {

  /** Same analyzer as TestFreeTextSuggester#testEndingHole: just deletes "of". */
  private static Analyzer stopOf() {
    return new Analyzer() {
      @Override
      public TokenStreamComponents createComponents(String field) {
        Tokenizer tokenizer = new MockTokenizer();
        CharArraySet stopSet = StopFilter.makeStopSet("of");
        return new TokenStreamComponents(tokenizer, new StopFilter(tokenizer, stopSet));
      }
    };
  }

  /** Same training text and trigram model as testEndingHole; the user types one more letter. */
  public void testTrigramLookupAfterStopWord() throws Exception {
    Analyzer a = stopOf();
    FreeTextSuggester sug = new FreeTextSuggester(a, a, 3, (byte) 0x20);
    sug.build(entries("wizard of oz"));
    // What testEndingHole asserts today:
    assertEquals("wizard _ oz/1.00", toString(sug.lookup("wizard of", 10)));
    // One more keystroke; the trigram "wizard _ oz" still matches with score 1.0:
    assertEquals("wizard _ oz/1.00", toString(sug.lookup("wizard of o", 10)));
    a.close();
  }

  /** Default constructor (bigram model, default separator) with a stock analyzer. */
  public void testEnglishAnalyzerQueryStartingWithStopWord() throws Exception {
    Analyzer a = new EnglishAnalyzer();
    FreeTextSuggester sug = new FreeTextSuggester(a);
    sug.build(entries("the wizard of oz"));
    // "the" is removed; the unigram model predicts "wizard" from the prefix "w":
    List<LookupResult> results = sug.lookup("the w", 10);
    assertEquals(1, results.size());
    assertTrue(results.get(0).key.toString(), results.get(0).key.toString().endsWith("wizard"));
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

  private static String toString(List<LookupResult> results) {
    StringBuilder b = new StringBuilder();
    for (LookupResult result : results) {
      b.append(' ');
      b.append(result.key);
      b.append('/');
      b.append(String.format(Locale.ROOT, "%.2f", ((double) result.value) / Long.MAX_VALUE));
    }
    return b.toString().trim();
  }
}
