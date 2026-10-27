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
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * WeightedSpanTermExtractor rewrites a PhraseQuery or MultiPhraseQuery into a SpanNearQuery with
 * slop {@code getSlop() + positionGaps} in int. PhraseQuery accepts any slop >= 0, so a phrase with
 * a position gap (for example a removed stop word) and slop Integer.MAX_VALUE ("any distance") gives
 * a negative span slop: the span query matches nothing and the Highlighter returns no fragment for a
 * document the phrase query itself matches.
 */
public class TestHighlighterHugePhraseSlop extends LuceneTestCase {

  private static final String TEXT = "the quick brown fox jumps";
  private static final String HIGHLIGHTED = "the <B>quick</B> brown <B>fox</B> jumps";

  private static PhraseQuery phrase(int slop) {
    // "quick ? fox": the hole at position 1 is one position gap
    return new PhraseQuery.Builder()
        .add(new Term("f", "quick"), 0)
        .add(new Term("f", "fox"), 2)
        .setSlop(slop)
        .build();
  }

  private static MultiPhraseQuery multiPhrase(int slop) {
    return new MultiPhraseQuery.Builder()
        .add(new Term[] {new Term("f", "quick")}, 0)
        .add(new Term[] {new Term("f", "fox"), new Term("f", "dog")}, 2)
        .setSlop(slop)
        .build();
  }

  private static String highlight(Query q) throws Exception {
    Analyzer a = new WhitespaceAnalyzer();
    MemoryIndex mi = new MemoryIndex();
    mi.addField("f", TEXT, a);
    assertTrue("the query must match the text", mi.search(q) > 0f);
    return new Highlighter(new QueryScorer(q, "f")).getBestFragment(a, "f", TEXT);
  }

  /** Control: slop + gaps still fits in an int. */
  public void testSlopJustBelowLimit() throws Exception {
    assertEquals(HIGHLIGHTED, highlight(phrase(Integer.MAX_VALUE - 1)));
    assertEquals(HIGHLIGHTED, highlight(multiPhrase(Integer.MAX_VALUE - 1)));
  }

  public void testPhraseQuerySlopMaxValue() throws Exception {
    assertEquals(HIGHLIGHTED, highlight(phrase(Integer.MAX_VALUE)));
  }

  public void testMultiPhraseQuerySlopMaxValue() throws Exception {
    assertEquals(HIGHLIGHTED, highlight(multiPhrase(Integer.MAX_VALUE)));
  }
}
