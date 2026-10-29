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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A Highlighter keeps one Fragmenter and calls Fragmenter#start ("Initializes the Fragmenter") for
 * every text, so a Highlighter that is reused for a text must cut it into the same fragments as a
 * new Highlighter. Public API only.
 */
public class TestReusedHighlighterFragments extends LuceneTestCase {
  private static final String FIELD = "body";
  private static final Query QUERY = new PhraseQuery(FIELD, "search", "library");

  /** The phrase match is the end of this text. */
  private static final String ENDS_WITH_MATCH =
      "Lucene is an open source project that ships a search library";

  private static final String NEXT =
      "Highlighting picks short fragments of text around each match so that a reader of the"
          + " search library manual sees why a document matched";

  private static final String NEXT_WITHOUT_MATCH =
      "Highlighting picks short fragments of text around each match so that a reader sees why"
          + " a document matched";

  private static Highlighter spanHighlighter(Query query, int fragmentSize) {
    QueryScorer scorer = new QueryScorer(query, FIELD);
    Highlighter highlighter = new Highlighter(scorer);
    highlighter.setTextFragmenter(new SimpleSpanFragmenter(scorer, fragmentSize));
    return highlighter;
  }

  /** All fragments of the text, in text order. */
  private static List<String> fragments(Highlighter highlighter, Analyzer analyzer, String text)
      throws Exception {
    TextFragment[] frags =
        highlighter.getBestTextFragments(analyzer.tokenStream(FIELD, text), text, false, 100);
    Arrays.sort(frags, Comparator.comparingInt(TextFragment::getFragNum));
    List<String> out = new ArrayList<>();
    for (TextFragment frag : frags) {
      out.add(frag.toString());
    }
    return out;
  }

  private static String words(String prefix, int n) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < n; i++) {
      sb.append(prefix).append(i).append(' ');
    }
    return sb.toString();
  }

  public void testNextTextIsCutLikeWithANewHighlighter() throws Exception {
    try (Analyzer analyzer = new StandardAnalyzer()) {
      List<String> expected = fragments(spanHighlighter(QUERY, 20), analyzer, NEXT);
      Highlighter reused = spanHighlighter(QUERY, 20);
      fragments(reused, analyzer, ENDS_WITH_MATCH);
      assertEquals(expected, fragments(reused, analyzer, NEXT));
    }
  }

  public void testNextTextWithoutMatch() throws Exception {
    try (Analyzer analyzer = new StandardAnalyzer()) {
      List<String> expected = fragments(spanHighlighter(QUERY, 20), analyzer, NEXT_WITHOUT_MATCH);
      Highlighter reused = spanHighlighter(QUERY, 20);
      fragments(reused, analyzer, ENDS_WITH_MATCH);
      assertEquals(expected, fragments(reused, analyzer, NEXT_WITHOUT_MATCH));
    }
  }

  public void testSameTextTwice() throws Exception {
    try (Analyzer analyzer = new StandardAnalyzer()) {
      Highlighter highlighter = spanHighlighter(QUERY, 20);
      List<String> first = fragments(highlighter, analyzer, ENDS_WITH_MATCH);
      assertEquals(first, fragments(highlighter, analyzer, ENDS_WITH_MATCH));
    }
  }

  /** The default fragment size (100) and getBestFragment, the most common call. */
  public void testBestFragmentWithDefaultFragmentSize() throws Exception {
    Query query = new PhraseQuery(FIELD, "quick", "fox");
    String previous = words("w", 120) + "the quick fox";
    String next = words("x", 60) + "quick fox " + words("y", 80) + "quick fox " + words("z", 10);
    try (Analyzer analyzer = new StandardAnalyzer()) {
      QueryScorer scorer = new QueryScorer(query, FIELD);
      Highlighter reused = new Highlighter(scorer);
      reused.setTextFragmenter(new SimpleSpanFragmenter(scorer));
      reused.getBestFragment(analyzer, FIELD, previous);
      String actual = reused.getBestFragment(analyzer, FIELD, next);

      QueryScorer freshScorer = new QueryScorer(query, FIELD);
      Highlighter fresh = new Highlighter(freshScorer);
      fresh.setTextFragmenter(new SimpleSpanFragmenter(freshScorer));
      String expected = fresh.getBestFragment(analyzer, FIELD, next);

      assertEquals("length of the best fragment", expected.length(), actual.length());
      assertEquals(expected, actual);
    }
  }

  /** Control: the same reuse with SimpleFragmenter gives the same fragments as a new one. */
  public void testControlSimpleFragmenter() throws Exception {
    try (Analyzer analyzer = new StandardAnalyzer()) {
      Highlighter fresh = new Highlighter(new QueryScorer(QUERY, FIELD));
      fresh.setTextFragmenter(new SimpleFragmenter(20));
      List<String> expected = fragments(fresh, analyzer, NEXT);
      Highlighter reused = new Highlighter(new QueryScorer(QUERY, FIELD));
      reused.setTextFragmenter(new SimpleFragmenter(20));
      fragments(reused, analyzer, ENDS_WITH_MATCH);
      assertEquals(expected, fragments(reused, analyzer, NEXT));
    }
  }
}
