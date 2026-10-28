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
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.CharsRef;

/**
 * SimpleSpanFragmenter "does not split up Spans", but it treats a span as open only from a single
 * "span starts here" event: at a token it is shown, whose position equals the span start, while it
 * is not already waiting for another span, using the first listed span with that start. A span
 * that starts while another one is open, a longer span listed after a shorter one with the same
 * start, and a span whose first token is a stacked token the fragmenter is not shown are never
 * protected, so a fragment boundary can fall inside them even though they are much shorter than
 * the fragment size.
 */
public class TestSimpleSpanFragmenterSpanTracking extends LuceneTestCase {

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

  /** "new york" and "new york city" both start at "New": only the first listed span is kept. */
  public void testLongerSpanWithSameStart() throws Exception {
    try (Analyzer analyzer = new StandardAnalyzer()) {
      String text = "We flew to New York City last week for a conference";
      Query query =
          new BooleanQuery.Builder()
              .add(new PhraseQuery("body", "new", "york"), BooleanClause.Occur.SHOULD)
              .add(new PhraseQuery("body", "new", "york", "city"), BooleanClause.Occur.SHOULD)
              .build();
      assertPhraseInOneFragment(
          fragments(analyzer, text, query, 17), "<B>New</B> <B>York</B> <B>City</B>");
    }
  }

  /** "york city hall" starts while the fragmenter still waits for the end of "new york". */
  public void testOverlappingSpans() throws Exception {
    try (Analyzer analyzer = new StandardAnalyzer()) {
      String text = "The museum is near New York City Hall and the park";
      Query query =
          new BooleanQuery.Builder()
              .add(new PhraseQuery("body", "new", "york"), BooleanClause.Occur.SHOULD)
              .add(new PhraseQuery("body", "york", "city", "hall"), BooleanClause.Occur.SHOULD)
              .build();
      assertPhraseInOneFragment(
          fragments(analyzer, text, query, 16), "<B>York</B> <B>City</B> <B>Hall</B>");
    }
  }

  /**
   * SynonymGraphFilter emits the synonym "notebook" before the original "laptop" at the same
   * position; Highlighter passes only the first of them to the fragmenter, so the span of the
   * phrase "laptop bag" is never seen to start.
   */
  public void testSpanStartingOnStackedToken() throws Exception {
    SynonymMap.Builder b = new SynonymMap.Builder(true);
    b.add(new CharsRef("laptop"), new CharsRef("notebook"), true);
    SynonymMap map = b.build();
    try (Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new WhitespaceTokenizer();
            return new TokenStreamComponents(
                t, new SynonymGraphFilter(new LowerCaseFilter(t), map, true));
          }
        }) {
      String text = "I left my laptop bag on the train this morning again";
      Query query = new PhraseQuery("body", "laptop", "bag");
      assertPhraseInOneFragment(fragments(analyzer, text, query, 18), "<B>laptop</B> <B>bag</B>");
    }
  }
}
