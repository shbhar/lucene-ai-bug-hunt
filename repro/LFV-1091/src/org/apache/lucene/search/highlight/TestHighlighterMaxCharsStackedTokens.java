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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.analysis.MockSynonymFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * OffsetLimitTokenFilter adds up token lengths, so stacked tokens (here the synonym "dog" on top of
 * every "dogs") reach setMaxDocCharsToAnalyze long before the text does. QueryScorer hands that cut
 * stream to the Highlighter, so even a plain term inside the limit is not highlighted.
 */
public class TestHighlighterMaxCharsStackedTokens extends LuceneTestCase {

  public void testTermInsideLimitWithStackedTokens() throws Exception {
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            MockTokenizer tokenizer = new MockTokenizer(MockTokenizer.WHITESPACE, false);
            tokenizer.setEnableChecks(false); // the highlighter stops reading at its limit
            return new TokenStreamComponents(tokenizer, new MockSynonymFilter(tokenizer));
          }
        };
    String text = "dogs ".repeat(10) + "big cats"; // 58 characters
    Highlighter highlighter =
        new Highlighter(
            new SimpleHTMLFormatter(), new QueryScorer(new TermQuery(new Term("f", "cats")), "f"));
    highlighter.setTextFragmenter(new NullFragmenter());
    highlighter.setMaxDocCharsToAnalyze(60);
    assertEquals(
        "dogs ".repeat(10) + "big <B>cats</B>", highlighter.getBestFragment(analyzer, "f", text));
  }
}
