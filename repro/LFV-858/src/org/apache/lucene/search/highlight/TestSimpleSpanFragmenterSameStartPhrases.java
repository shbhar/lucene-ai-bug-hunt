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
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSimpleSpanFragmenterSameStartPhrases extends LuceneTestCase {

  public void testSameStartPhrasesNotSplit() throws Exception {
    Query query =
        new BooleanQuery.Builder()
            .add(new PhraseQuery("f", "new", "york"), Occur.SHOULD)
            .add(new PhraseQuery("f", "new", "york", "city", "hall"), Occur.SHOULD)
            .build();
    QueryScorer scorer = new QueryScorer(query, "f");
    Highlighter highlighter = new Highlighter(scorer);
    highlighter.setTextFragmenter(new SimpleSpanFragmenter(scorer, 20));
    String text = "We flew to New York City Hall last week for a conference";
    String best = highlighter.getBestFragment(new StandardAnalyzer(), "f", text);
    String phrase = "<B>New</B> <B>York</B> <B>City</B> <B>Hall</B>";
    // expected: the 4-word phrase match stays in one fragment; actual: it is cut before "Hall"
    assertTrue(
        "expected a fragment holding " + phrase + ", got [" + best + "]", best.contains(phrase));
  }
}
