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
package org.apache.lucene.search.highlight;

import java.util.Arrays;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SimpleSpanFragmenter "breaks text up into same-size fragments but does not split up Spans". Its
 * position counter starts at -1 and adds the increment of every token passed to isNewFragment(),
 * but Highlighter calls isNewFragment() only for the first token of each new non-overlapping token
 * group after the first one: the first token's increment and the increments of tokens that overlap
 * the previous token are never added. The counter therefore lags the real position, the span is
 * recognised one (or more) tokens late, and a fragment boundary can fall inside a short phrase.
 */
public class TestSimpleSpanFragmenterPositionCounter extends LuceneTestCase {

  private static String[] fragments(Analyzer analyzer, String text, Query query, int size)
      throws Exception {
    QueryScorer scorer = new QueryScorer(query, "body");
    Highlighter highlighter = new Highlighter(scorer);
    highlighter.setTextFragmenter(new SimpleSpanFragmenter(scorer, size));
    TextFragment[] frags =
        highlighter.getBestTextFragments(analyzer.tokenStream("body", text), text, false, 100);
    Arrays.sort(frags, (a, b) -> Integer.compare(a.getFragNum(), b.getFragNum()));
    return Arrays.stream(frags).map(TextFragment::toString).toArray(String[]::new);
  }

  private static void assertPhraseInOneFragment(String[] frags, String markedPhrase) {
    for (String f : frags) {
      if (f.contains(markedPhrase)) {
        return;
      }
    }
    fail("phrase " + markedPhrase + " split across fragments: " + Arrays.toString(frags));
  }

  /** Plain text, no stop words: the 14-char phrase is split although the fragment size is 20. */
  public void testPhraseInPlainText() throws Exception {
    try (Analyzer analyzer = new StandardAnalyzer()) {
      String text = "Lucene is a search library written in Java";
      Query query = new PhraseQuery("body", "search", "library");
      assertPhraseInOneFragment(
          fragments(analyzer, text, query, 20), "<B>search</B> <B>library</B>");
      QueryScorer scorer = new QueryScorer(query, "body");
      Highlighter highlighter = new Highlighter(scorer);
      highlighter.setTextFragmenter(new SimpleSpanFragmenter(scorer, 20));
      String best = highlighter.getBestFragment(analyzer, "body", text);
      assertTrue("best fragment: " + best, best.contains("<B>search</B> <B>library</B>"));
    }
  }

  /** A leading stop word (first token increment 2) makes the counter lag by 2. */
  public void testPhraseAfterLeadingStopWord() throws Exception {
    CharArraySet stop = new CharArraySet(Arrays.asList("the", "of", "and", "that"), true);
    try (Analyzer analyzer = new StandardAnalyzer(stop)) {
      String text = "The history of search engines and the search library that runs them";
      Query query = new PhraseQuery("body", "search", "library");
      assertPhraseInOneFragment(
          fragments(analyzer, text, query, 24), "<B>search</B> <B>library</B>");
    }
  }

  /** WordDelimiterGraphFilter emits "fi" overlapping "Wi-Fi" with increment 1: that increment is lost too. */
  public void testPhraseAfterOverlappingToken() throws Exception {
    try (Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new WhitespaceTokenizer();
            int flags =
                WordDelimiterGraphFilter.GENERATE_WORD_PARTS
                    | WordDelimiterGraphFilter.CATENATE_WORDS
                    | WordDelimiterGraphFilter.PRESERVE_ORIGINAL;
            return new TokenStreamComponents(
                t, new LowerCaseFilter(new WordDelimiterGraphFilter(t, flags, null)));
          }
        }) {
      String text = "Our Wi-Fi router supports mesh networking out of the box today";
      Query query = new PhraseQuery("body", "mesh", "networking");
      assertPhraseInOneFragment(
          fragments(analyzer, text, query, 33), "<B>mesh</B> <B>networking</B>");
    }
  }
}
