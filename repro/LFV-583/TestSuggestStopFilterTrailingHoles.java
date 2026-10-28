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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * SuggestStopFilter is documented as "Like StopFilter except it will not remove the last token if
 * that token was not followed by some token separator". When the input ends with stop words that
 * are followed by a separator it removes them like StopFilter, but its end() does not add their
 * positions to the final position increment (StopFilter does, per TokenStream#end).
 */
public class TestSuggestStopFilterTrailingHoles extends LuceneTestCase {

  private static final CharArraySet STOP = StopFilter.makeStopSet("to", "a", "the", "of");

  /** sum of the tokens' position increments plus the final position increment, and that final */
  private static int[] positions(TokenStream ts) throws IOException {
    PositionIncrementAttribute posInc = ts.addAttribute(PositionIncrementAttribute.class);
    int sum = 0;
    ts.reset();
    while (ts.incrementToken()) {
      sum += posInc.getPositionIncrement();
    }
    ts.end();
    int finalPosInc = posInc.getPositionIncrement();
    ts.close();
    return new int[] {sum + finalPosInc, finalPosInc};
  }

  private static Tokenizer tokenizer(String text) {
    Tokenizer t = new MockTokenizer(MockTokenizer.WHITESPACE, true);
    t.setReader(new java.io.StringReader(text));
    return t;
  }

  /** The end state of the stream itself, compared with StopFilter on the same input. */
  public void testFinalPositionIncrementOfRemovedTrailingStopWords() throws IOException {
    for (String text : new String[] {"go to a the ", "one of the ", "go to "}) {
      int[] stop = positions(new StopFilter(tokenizer(text), STOP));
      int[] suggestStop = positions(new SuggestStopFilter(tokenizer(text), STOP));
      assertEquals(
          "final position increment for '" + text + "' (StopFilter reports " + stop[1] + ")",
          stop[1],
          suggestStop[1]);
      assertEquals("final position for '" + text + "'", stop[0], suggestStop[0]);
    }
  }

  private static final class Entries implements InputIterator {
    private final String[] keys;
    private final long[] weights;
    private int i = -1;

    Entries(String[] keys, long[] weights) {
      this.keys = keys;
      this.weights = weights;
    }

    @Override
    public BytesRef next() {
      return ++i < keys.length ? new BytesRef(keys[i]) : null;
    }

    @Override
    public long weight() {
      return weights[i];
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
  }

  private static Analyzer analyzer(boolean suggestStop) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new MockTokenizer(MockTokenizer.WHITESPACE, true);
        TokenStream ts = suggestStop ? new SuggestStopFilter(t, STOP) : new StopFilter(t, STOP);
        return new TokenStreamComponents(t, ts);
      }
    };
  }

  private static List<String> lookup(Directory dir, Analyzer queryAnalyzer, String key)
      throws IOException {
    Analyzer indexAnalyzer = analyzer(false);
    AnalyzingSuggester suggester =
        new AnalyzingSuggester(dir, "suggest", indexAnalyzer, queryAnalyzer);
    suggester.build(
        new Entries(new String[] {"one direction", "one of the best"}, new long[] {10, 5}));
    List<String> out = new ArrayList<>();
    for (LookupResult r : suggester.lookup(key, false, 10)) {
      out.add(r.key.toString());
    }
    indexAnalyzer.close();
    return out;
  }

  /**
   * The configuration the class javadoc prescribes: StopFilter in the index analyzer,
   * SuggestStopFilter in the query analyzer of an analyzing suggester. The user has typed "one of
   * the " (two stop words, then a space), so the suggestions should be the entries that continue
   * after two removed words, as with a StopFilter query analyzer; "one direction" should not be
   * suggested.
   */
  public void testAnalyzingSuggesterTwoTrailingStopWords() throws IOException {
    try (Directory dir = newDirectory()) {
      Analyzer stopQuery = analyzer(false);
      Analyzer suggestStopQuery = analyzer(true);
      List<String> control = lookup(dir, stopQuery, "one of the ");
      List<String> actual = lookup(dir, suggestStopQuery, "one of the ");
      assertEquals("control (StopFilter query analyzer)", List.of("one of the best"), control);
      assertEquals("SuggestStopFilter query analyzer", control, actual);
      stopQuery.close();
      suggestStopQuery.close();
    }
  }
}
