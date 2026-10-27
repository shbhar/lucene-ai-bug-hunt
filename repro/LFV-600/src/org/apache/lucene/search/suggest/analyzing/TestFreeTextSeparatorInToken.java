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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * FreeTextSuggester's constructor javadoc: "No input tokens should have this [separator] byte,
 * otherwise IllegalArgumentException is thrown." build() only throws when a shingle term holds
 * more separator bytes than grams - 1, so a token that contains the separator is accepted when it
 * has no neighbour token (or when grams is large enough), and is then indexed as if it were an
 * n-gram of several tokens.
 */
public class TestFreeTextSeparatorInToken extends LuceneTestCase {

  private static InputIterator inputs(String... values) {
    Iterator<String> it = Arrays.asList(values).iterator();
    return new InputIterator() {
      @Override
      public BytesRef next() {
        return it.hasNext() ? new BytesRef(it.next()) : null;
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

  /** Same input as upstream testIllegalByteDuringBuild, minus the neighbour token "baz". */
  public void testDefaultSeparatorInSingleTokenRejected() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random()); // whitespace tokenizer: keeps U+001E in the token
    FreeTextSuggester sug = new FreeTextSuggester(analyzer); // bigrams, DEFAULT_SEPARATOR 0x1e
    expectThrows(IllegalArgumentException.class, () -> sug.build(inputs("foo\u001ebar")));
    analyzer.close();
  }

  /** A custom separator that StandardTokenizer keeps inside words. */
  public void testCustomSeparatorInTokenRejected() throws Exception {
    Analyzer analyzer = new StandardAnalyzer(CharArraySet.EMPTY_SET); // "foo_bar" is one token
    FreeTextSuggester sug = new FreeTextSuggester(analyzer, analyzer, 2, (byte) '_');
    expectThrows(IllegalArgumentException.class, () -> sug.build(inputs("foo_bar")));
    analyzer.close();
  }

  /** Trigram model: the token is accepted even next to another token. */
  public void testCustomSeparatorInTokenRejectedTrigrams() throws Exception {
    Analyzer analyzer = new StandardAnalyzer(CharArraySet.EMPTY_SET);
    FreeTextSuggester sug = new FreeTextSuggester(analyzer, analyzer, 3, (byte) '_');
    expectThrows(IllegalArgumentException.class, () -> sug.build(inputs("foo_bar baz", "foo qux")));
    analyzer.close();
  }

  /**
   * What the accepted input does to the model: the token "foo_bar" is stored as the bigram
   * (foo, bar) without a unigram "foo", so an ordinary lookup trips the context-count invariant
   * (AssertionError with assertions enabled, NullPointerException without).
   */
  public void testLookupAfterAcceptedTokenDoesNotCrash() throws Exception {
    Analyzer analyzer = new StandardAnalyzer(CharArraySet.EMPTY_SET);
    FreeTextSuggester sug = new FreeTextSuggester(analyzer, analyzer, 2, (byte) '_');
    try {
      sug.build(inputs("foo_bar"));
    } catch (IllegalArgumentException expected) {
      analyzer.close();
      return; // the documented outcome; nothing more to check
    }
    List<LookupResult> results = sug.lookup("foo ", 10); // must not throw AssertionError / NPE
    assertNotNull(results);
    analyzer.close();
  }
}
