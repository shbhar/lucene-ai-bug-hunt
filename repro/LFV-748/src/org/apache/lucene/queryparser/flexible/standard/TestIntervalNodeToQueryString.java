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
package org.apache.lucene.queryparser.flexible.standard;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;
import org.apache.lucene.queryparser.flexible.standard.parser.EscapeQuerySyntaxImpl;
import org.apache.lucene.queryparser.flexible.standard.parser.StandardSyntaxParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The query node package documents that "a query node tree can also generate a query string that
 * can be parsed back by the original text parser" (QueryNode#toQueryString: "convert to a query
 * string understood by the query parser"). For interval functions the printed string must
 * therefore parse back to the same query.
 */
public class TestIntervalNodeToQueryString extends LuceneTestCase {

  private void assertRoundTrip(String query) throws Exception {
    QueryNode node = new StandardSyntaxParser().parse(query, "body");
    String printed = node.toQueryString(new EscapeQuerySyntaxImpl()).toString();
    StandardQueryParser qp = new StandardQueryParser(new WhitespaceAnalyzer());
    Query expected = qp.parse(query, "body");
    Query reparsed;
    try {
      reparsed = qp.parse(printed, "body");
    } catch (Exception e) {
      throw new AssertionError(
          "toQueryString of '" + query + "' is '" + printed + "', which does not parse: " + e, e);
    }
    assertEquals("toQueryString of '" + query + "' is '" + printed + "'", expected, reparsed);
  }

  /** Control: functions with only term and function arguments round-trip. */
  public void testControlOrderedExtend() throws Exception {
    assertRoundTrip("fn:ordered(a fn:extend(b 1 2))");
  }

  /** maxEdits and maxExpansions are printed without a separator: 2 and 128 become 2128. */
  public void testFuzzyTermDefaults() throws Exception {
    assertRoundTrip("fn:fuzzyTerm(abc)");
  }

  public void testFuzzyTermExplicitArguments() throws Exception {
    assertRoundTrip("fn:fuzzyTerm(abc 1 5)");
  }

  /** maxExpansions is printed as 'maxExpansions:5', which the grammar does not accept. */
  public void testWildcardMaxExpansions() throws Exception {
    assertRoundTrip("fn:wildcard(ab* 5)");
  }

  /** Quoted term literals are printed without quotes unless they contain whitespace. */
  public void testQuotedTermWithSyntaxCharacter() throws Exception {
    assertRoundTrip("fn:or(\"a:b\" c)");
  }
}
