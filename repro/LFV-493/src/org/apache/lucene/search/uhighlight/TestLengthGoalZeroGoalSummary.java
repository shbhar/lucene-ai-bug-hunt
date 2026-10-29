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
package org.apache.lucene.search.uhighlight;

import java.text.BreakIterator;
import java.util.Locale;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLengthGoalZeroGoalSummary extends LuceneTestCase {

  public void testZeroLengthGoalDefaultSummary() throws Exception {
    UnifiedHighlighter highlighter =
        UnifiedHighlighter.builderWithoutSearcher(new StandardAnalyzer())
            .withBreakIterator(
                () ->
                    LengthGoalBreakIterator.createMinLength(
                        BreakIterator.getSentenceInstance(Locale.ROOT), 0, 0.5f))
            .build();
    TermQuery noMatch = new TermQuery(new Term("body", "missing"));
    Object summary;
    try {
      summary =
          highlighter.highlightWithoutSearcher(
              "body", noMatch, "First sentence. Second sentence.", 1);
    } catch (IllegalArgumentException e) {
      // expected: the default summary (first sentence); actual: IllegalArgumentException
      throw new AssertionError("expected the summary \"First sentence. \" but got " + e, e);
    }
    assertEquals("First sentence. ", summary);
  }
}
