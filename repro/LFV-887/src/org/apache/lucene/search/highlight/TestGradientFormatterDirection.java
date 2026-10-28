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

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * GradientFormatter javadoc: maxScore is "The score (and above) displayed as maxColor";
 * minForegroundColor is "The hex color used for representing IDF scores of zero eg #FFFFFF (white)";
 * maxForegroundColor is "The largest hex color used for representing IDF scores eg #000000
 * (black)". With the javadoc's own example colours, the top-scoring term must therefore be black
 * and a low-scoring term must be close to white. Only public API is used.
 */
public class TestGradientFormatterDirection extends LuceneTestCase {

  /** The javadoc example: min = #FFFFFF (white), max = #000000 (black). */
  public void testTopScoringTermGetsMaxColorWithJavadocExample() throws Exception {
    GradientFormatter formatter = new GradientFormatter(1f, "#FFFFFF", "#000000", null, null);
    Highlighter highlighter =
        new Highlighter(formatter, new QueryScorer(new TermQuery(new Term("f", "foo"))));
    String fragment =
        highlighter.getBestFragment(new MockAnalyzer(random()), "f", "foo bar");
    // score of "foo" is 1.0 == maxScore, so it must be displayed with maxForegroundColor
    assertEquals("<font color=\"#000000\" >foo</font> bar", fragment);
  }

  /**
   * Two terms with weights 1 and 4, maxScore 4: the stronger term must be nearer the max colour
   * (black) than the weaker one. Same for the background colour and SpanGradientFormatter.
   */
  public void testStrongerTermIsNearerMaxColor() throws Exception {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    b.add(new TermQuery(new Term("f", "weak")), BooleanClause.Occur.SHOULD);
    b.add(new BoostQuery(new TermQuery(new Term("f", "strong")), 4f), BooleanClause.Occur.SHOULD);
    SpanGradientFormatter formatter =
        new SpanGradientFormatter(4f, null, null, "#FFFFFF", "#000000");
    Highlighter highlighter = new Highlighter(formatter, new QueryScorer(b.build()));
    String fragment =
        highlighter.getBestFragment(new MockAnalyzer(random()), "f", "weak strong");
    // weight 1 of 4 -> 3/4 of the way from white to black would be #404040 (or nearby);
    // weight 4 of 4 -> exactly the max colour #000000
    assertTrue(
        "strong term must get the max background colour #000000: " + fragment,
        fragment.contains("<span style=\"background: #000000; \">strong</span>"));
  }
}
