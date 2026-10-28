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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;

/**
 * Independent check: SuggestStopFilter is documented as "Like StopFilter except it will not remove
 * the last token if that token was not followed by some token separator". When the input ends
 * with stop words that ARE followed by a separator, they are removed; StopFilter then reports
 * their positions in the final position increment after end(). This test checks whether
 * SuggestStopFilter does the same, and whether the difference changes AnalyzingSuggester results.
 */
public class TestSuggestStopFilterEndState extends LuceneTestCase {

  private static final CharArraySet STOP = StopFilter.makeStopSet("to", "a", "the", "of");

  /** Consumes the stream; returns "term/posInc ... | end posInc=N offset=M". */
  private static String dump(TokenStream ts) throws IOException {
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    PositionIncrementAttribute posInc = ts.addAttribute(PositionIncrementAttribute.class);
    OffsetAttribute offset = ts.addAttribute(OffsetAttribute.class);
    StringBuilder sb = new StringBuilder();
    ts.reset();
    while (ts.incrementToken()) {
      sb.append(term.toString()).append('/').append(posInc.getPositionIncrement()).append(' ');
    }
    ts.end();
    sb.append("| end posInc=")
        .append(posInc.getPositionIncrement())
        .append(" offset=")
        .append(offset.endOffset());
    ts.close();
    return sb.toString();
  }

  private static String analyze(String text, boolean suggestStop) throws IOException {
    Tokenizer tok = new MockTokenizer(MockTokenizer.WHITESPACE, true);
    tok.setReader(new StringReader(text));
    TokenStream ts = suggestStop ? new SuggestStopFilter(tok, STOP) : new StopFilter(tok, STOP);
    return dump(ts);
  }

  /**
   * Texts that end with a token separator: here the javadoc's exception does not apply, so
   * SuggestStopFilter should behave exactly like StopFilter (tokens, increments and end state).
   */
  public void testEndStateLikeStopFilterWhenTrailingSeparator() throws Exception {
    String[] texts = {
      "go school ", // no trailing stop word: control, expected to agree
      "go to school ", // mid stop word only: control, expected to agree
      "go the ", // one trailing stop word
      "go to a the ", // three trailing stop words
      "one of the ", // two trailing stop words
    };
    List<String> mismatches = new ArrayList<>();
    for (String text : texts) {
      String stop = analyze(text, false);
      String suggest = analyze(text, true);
      System.out.println("text='" + text + "'\n  StopFilter        : " + stop
          + "\n  SuggestStopFilter : " + suggest);
      if (stop.equals(suggest) == false) {
        mismatches.add("'" + text + "': StopFilter [" + stop + "] vs SuggestStopFilter [" + suggest + "]");
      }
    }
    assertTrue("SuggestStopFilter end state differs from StopFilter:\n" + String.join("\n", mismatches),
        mismatches.isEmpty());
  }

  /** Simple in-memory InputIterator (public suggest API only). */
  private static final class Entries implements InputIterator {
    private final Iterator<String> it;
    private final Iterator<Long> weights;
    private long weight;

    Entries(List<String> keys, List<Long> ws) {
      this.it = keys.iterator();
      this.weights = ws.iterator();
    }

    @Override
    public BytesRef next() {
      if (it.hasNext() == false) {
        return null;
      }
      weight = weights.next();
      return new BytesRef(it.next());
    }

    @Override
    public long weight() {
      return weight;
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
        Tokenizer tok = new MockTokenizer(MockTokenizer.WHITESPACE, true);
        TokenStream ts = suggestStop ? new SuggestStopFilter(tok, STOP) : new StopFilter(tok, STOP);
        return new TokenStreamComponents(tok, ts);
      }
    };
  }

  private static List<String> lookup(Directory dir, String prefix, Analyzer index, Analyzer query,
      String q) throws IOException {
    AnalyzingSuggester s = new AnalyzingSuggester(dir, prefix, index, query);
    s.build(new Entries(List.of("one direction", "one of the best"), List.of(10L, 5L)));
    List<String> out = new ArrayList<>();
    for (LookupResult r : s.lookup(q, false, 10)) {
      out.add(r.key.toString());
    }
    return out;
  }

  /**
   * The setup the class javadoc prescribes: StopFilter at index time, SuggestStopFilter at query
   * time. Compared with the same suggester using StopFilter at query time. For the query text
   * "one of the " (both stop words followed by a separator, so both are removed) the javadoc's
   * exception does not apply and both query analyzers should give the same suggestions.
   */
  public void testAnalyzingSuggesterMatchesStopFilterQueryAnalyzer() throws Exception {
    Analyzer index = analyzer(false);
    Analyzer queryStop = analyzer(false);
    Analyzer querySuggest = analyzer(true);
    Directory dir = newDirectory();
    try {
      String q = "one of the ";
      List<String> withStop = lookup(dir, "a", index, queryStop, q);
      List<String> withSuggest = lookup(dir, "b", index, querySuggest, q);
      System.out.println("lookup('" + q + "'): StopFilter query analyzer -> " + withStop
          + "; SuggestStopFilter query analyzer -> " + withSuggest);
      // control: the StopFilter query analyzer gives the obvious answer
      assertEquals(List.of("one of the best"), withStop);
      assertEquals("SuggestStopFilter query analyzer vs StopFilter query analyzer for '" + q + "'",
          withStop, withSuggest);
    } finally {
      IOUtils.close(dir, index, queryStop, querySuggest);
    }
  }
}
