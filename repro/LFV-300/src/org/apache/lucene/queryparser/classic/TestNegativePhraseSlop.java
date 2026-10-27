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
package org.apache.lucene.queryparser.classic;

import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The lexer accepts a negative phrase slop ("a b"~-1) and QueryParserBase#handleQuotedTerm passes
 * it on unchecked, so the IllegalArgumentException of the PhraseQuery constructor escapes parse()
 * instead of a ParseException. A negative fuzzy edit distance (a~-1) is rejected with a
 * ParseException.
 */
public class TestNegativePhraseSlop extends LuceneTestCase {

  public void testNegativeFuzzyIsAParseException() {
    QueryParser qp = new QueryParser("f", new MockAnalyzer(random()));
    expectThrows(ParseException.class, () -> qp.parse("a~-1"));
  }

  public void testNegativePhraseSlopIsAParseException() {
    QueryParser qp = new QueryParser("f", new MockAnalyzer(random()));
    expectThrows(ParseException.class, () -> qp.parse("\"a b\"~-1"));
  }

  public void testNegativePhraseSlopIsAParseExceptionMultiField() {
    MultiFieldQueryParser qp =
        new MultiFieldQueryParser(new String[] {"f", "g"}, new MockAnalyzer(random()));
    expectThrows(ParseException.class, () -> qp.parse("\"a b\"~-1"));
  }
}
