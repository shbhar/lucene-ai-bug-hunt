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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestUnifiedHighlighterPrefixAndSpanOnSameToken extends LuceneTestCase {

  public void testPrefixAndSpanTermMatchTheSameToken() throws Exception {
    Query query =
        new BooleanQuery.Builder()
            .add(new PrefixQuery(new Term("body", "qu")), BooleanClause.Occur.SHOULD)
            .add(new SpanTermQuery(new Term("body", "quick")), BooleanClause.Occur.SHOULD)
            .build();
    UnifiedHighlighter highlighter =
        UnifiedHighlighter.builderWithoutSearcher(new StandardAnalyzer()).build();
    // expected "the <b>quick</b> brown fox"; actual (-ea): AssertionError in OffsetsEnum
    assertEquals(
        "the <b>quick</b> brown fox",
        highlighter.highlightWithoutSearcher("body", query, "the quick brown fox", 1));
  }
}
