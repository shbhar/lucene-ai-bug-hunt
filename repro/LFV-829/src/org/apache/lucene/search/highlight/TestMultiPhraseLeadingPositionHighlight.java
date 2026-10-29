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
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMultiPhraseLeadingPositionHighlight extends LuceneTestCase {

  public void testFirstPositionAboveZeroAddsNoSlop() throws Exception {
    // expected: only the adjacent "dogs bark" marked; actual: "dogs often bark" too (as slop 1)
    Query query =
        new MultiPhraseQuery.Builder()
            .add(new Term[] {new Term("f", "dogs"), new Term("f", "dog")}, 1)
            .add(new Term[] {new Term("f", "bark")}, 2)
            .build();
    Highlighter highlighter = new Highlighter(new QueryScorer(query, "f"));
    highlighter.setTextFragmenter(new NullFragmenter());
    assertEquals(
        "dogs often bark but my <B>dogs</B> <B>bark</B>",
        highlighter.getBestFragment(
            new MockAnalyzer(random()), "f", "dogs often bark but my dogs bark"));
  }
}
