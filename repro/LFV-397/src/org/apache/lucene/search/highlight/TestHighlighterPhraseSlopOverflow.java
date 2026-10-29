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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestHighlighterPhraseSlopOverflow extends LuceneTestCase {

  public void testPhraseQuerySlopMaxValue() throws Exception {
    PhraseQuery q =
        new PhraseQuery.Builder()
            .add(new Term("f", "quick"), 0)
            .add(new Term("f", "fox"), 2)
            .setSlop(Integer.MAX_VALUE)
            .build();
    String text = "the quick brown fox jumps";
    String fragment =
        new Highlighter(new QueryScorer(q, "f")).getBestFragment(new StandardAnalyzer(), "f", text);
    // the phrase matches; expected quick and fox marked, actual null (slop + 1 gap wraps negative)
    assertEquals("the <B>quick</B> brown <B>fox</B> jumps", fragment);
  }
}
