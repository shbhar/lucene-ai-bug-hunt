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
 * QueryParserBase.parse documents "@throws ParseException if the parsing fails", and it already
 * converts the generated parser's TokenMgrError (an Error) into a ParseException. Deeply nested
 * groups instead let a StackOverflowError escape: the generated Query() and Clause() methods recurse
 * once per '(' with no depth bound.
 */
public class TestClassicDeepNesting extends LuceneTestCase {

  /** 100,000 nested groups (about 200 KB of query text) overflow any thread stack up to ~16 MB. */
  private static final int DEPTH = 100_000;

  private static void assertParsesOrThrowsParseException(QueryParser qp, String query) {
    try {
      qp.parse(query);
    } catch (ParseException expected) {
      // the documented failure mode
    } catch (StackOverflowError e) {
      throw new AssertionError(
          "QueryParser.parse let a StackOverflowError escape for "
              + query.length()
              + " chars of nested groups; documented outcome is a Query or a ParseException",
          e);
    }
  }

  public void testNestedParentheses() {
    String q = "(".repeat(DEPTH) + "a" + ")".repeat(DEPTH);
    assertParsesOrThrowsParseException(new QueryParser("f", new MockAnalyzer(random())), q);
  }

  public void testNestedRequiredClauses() {
    String q = "+(".repeat(DEPTH) + "a" + ")".repeat(DEPTH);
    assertParsesOrThrowsParseException(new QueryParser("f", new MockAnalyzer(random())), q);
  }

  public void testMultiFieldQueryParserSharesTheRecursion() {
    String q = "(".repeat(DEPTH) + "a" + ")".repeat(DEPTH);
    assertParsesOrThrowsParseException(
        new MultiFieldQueryParser(new String[] {"f", "g"}, new MockAnalyzer(random())), q);
  }
}
