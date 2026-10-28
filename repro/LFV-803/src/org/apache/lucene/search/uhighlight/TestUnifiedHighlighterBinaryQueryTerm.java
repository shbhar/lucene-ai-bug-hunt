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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * With a field matcher that accepts every field (requireFieldMatch=false), the UnifiedHighlighter
 * extracts the terms of every field. Its re-analysis offset strategies (the documented mode of
 * highlightWithoutSearcher) then decode each term as UTF-8: MemoryIndexOffsetStrategy through
 * Automata.makeStringUnion (code-point mode) and TokenStreamOffsetStrategy through
 * BytesRef#utf8ToString. A binary term, here the value of a binary StringField id, is not UTF-8,
 * so highlighting the text throws.
 */
public class TestUnifiedHighlighterBinaryQueryTerm extends LuceneTestCase {

  private static final BytesRef BINARY_ID =
      new BytesRef(new byte[] {(byte) 0x9f, 0x12, (byte) 0xff, 0x00, 0x41});

  private static Query query() {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("id", BINARY_ID)), BooleanClause.Occur.SHOULD)
        .add(new TermQuery(new Term("body", "hello")), BooleanClause.Occur.SHOULD)
        .build();
  }

  private static Analyzer analyzer() {
    return new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
  }

  /** Default flags otherwise: MemoryIndexOffsetStrategy. */
  public void testAnyFieldMatcher() throws Exception {
    UnifiedHighlighter highlighter =
        UnifiedHighlighter.builder(null, analyzer()).withFieldMatcher(f -> true).build();
    Object snippet = highlighter.highlightWithoutSearcher("body", query(), "hello world", 1);
    assertEquals("<b>hello</b> world", String.valueOf(snippet));
  }

  /** passageRelevancyOverSpeed=false: TokenStreamOffsetStrategy. */
  public void testAnyFieldMatcherTokenStreamStrategy() throws Exception {
    UnifiedHighlighter highlighter =
        UnifiedHighlighter.builder(null, analyzer())
            .withFieldMatcher(f -> true)
            .withPassageRelevancyOverSpeed(false)
            .build();
    Object snippet = highlighter.highlightWithoutSearcher("body", query(), "hello world", 1);
    assertEquals("<b>hello</b> world", String.valueOf(snippet));
  }
}
