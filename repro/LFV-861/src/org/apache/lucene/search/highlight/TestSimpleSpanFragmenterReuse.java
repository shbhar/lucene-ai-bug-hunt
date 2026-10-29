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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSimpleSpanFragmenterReuse extends LuceneTestCase {

  private static Highlighter newHighlighter() {
    QueryScorer scorer = new QueryScorer(new PhraseQuery("body", "search", "library"), "body");
    Highlighter highlighter = new Highlighter(scorer);
    highlighter.setTextFragmenter(new SimpleSpanFragmenter(scorer, 20));
    return highlighter;
  }

  private static String fragments(Highlighter highlighter, Analyzer analyzer, String text)
      throws Exception {
    TextFragment[] frags =
        highlighter.getBestTextFragments(analyzer.tokenStream("body", text), text, false, 100);
    Arrays.sort(frags, (a, b) -> Integer.compare(a.getFragNum(), b.getFragNum()));
    return Arrays.toString(frags);
  }

  public void testReusedHighlighterFragmentsLikeAFreshOne() throws Exception {
    String first = "Lucene is an open source project that ships a search library";
    String second =
        "Highlighting picks short fragments of text around each match so that a reader of the"
            + " search library manual sees why a document matched";
    try (Analyzer analyzer = new StandardAnalyzer()) {
      Highlighter reused = newHighlighter();
      fragments(reused, analyzer, first);
      // expected: the fragments of a fresh Highlighter; actual: one 70-char fragment, then 1-word ones
      assertEquals(
          fragments(newHighlighter(), analyzer, second), fragments(reused, analyzer, second));
    }
  }
}
