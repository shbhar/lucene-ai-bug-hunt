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
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSimpleFragmenterOffsetGap extends LuceneTestCase {

  public void testFragmentAfterLongToken() throws Exception {
    Highlighter highlighter =
        new Highlighter(new QueryScorer(new TermQuery(new Term("f", "gamma"))));
    highlighter.setTextFragmenter(new SimpleFragmenter(20));
    String text = "x".repeat(60) + " beta gamma";
    MockAnalyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    String best = highlighter.getBestFragment(analyzer, "f", text);
    // expected " beta <B>gamma</B>" (one fragment), actual " <B>gamma</B>"
    assertEquals(" beta <B>gamma</B>", best);
  }
}
