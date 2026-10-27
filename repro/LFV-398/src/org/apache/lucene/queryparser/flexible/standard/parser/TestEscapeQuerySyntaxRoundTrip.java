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
package org.apache.lucene.queryparser.flexible.standard.parser;

import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * QueryNode#toQueryString ("convert to a query string understood by the query parser") escapes term text with
 * EscapeQuerySyntaxImpl. Since LUCENE-10223 (9.1) the grammar no longer accepts an unescaped '@' anywhere in a term
 * (intended: '@' must be escaped or quoted), but EscapeQuerySyntaxImpl still escapes '@' only as the first character
 * of a term; and it never escapes a term that is exactly "&&" or "||", which the grammar reads as AND / OR.
 */
public class TestEscapeQuerySyntaxRoundTrip extends LuceneTestCase {

  private static void assertRoundTrip(String query) throws Exception {
    StandardQueryParser p = new StandardQueryParser(new MockAnalyzer(random(), MockTokenizer.KEYWORD, false));
    QueryNode node = p.getSyntaxParser().parse(query, "df");
    String queryString = node.toQueryString(new EscapeQuerySyntaxImpl()).toString();
    assertEquals("query string of " + query + " was: " + queryString, p.parse(query, "df"), p.parse(queryString, "df"));
  }

  /** Control: an escaped '@' as the first character round-trips. */
  public void testAtSignFirst() throws Exception {
    assertRoundTrip("\\@example.com");
  }

  /** The term user@example.com: toQueryString gives "df:user@example.com", which does not parse. */
  public void testAtSignInsideTerm() throws Exception {
    assertRoundTrip("user\\@example.com");
  }

  /** The term "&&": toQueryString gives "df:&&", which the parser reads as the AND operator (syntax error). */
  public void testAndAndWord() throws Exception {
    assertRoundTrip("\\&&");
  }

  /** The term "||": same, with the OR operator. */
  public void testOrOrWord() throws Exception {
    assertRoundTrip("\\||");
  }
}
