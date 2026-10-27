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

import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;
import org.apache.lucene.queryparser.flexible.standard.parser.EscapeQuerySyntaxImpl;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The flexible grammar accepts escaped field names (StandardSyntaxParser.jj FieldName(): "This utility method strips
 * escape characters from field names"), and QueryNode#toQueryString is documented to "convert to a query string
 * understood by the query parser". The toQueryString implementations print the field name without escaping it, so
 * the string of a node for such a field does not parse, or parses into a query on other fields.
 */
public class TestToQueryStringFieldNames extends LuceneTestCase {

  private static void assertRoundTrip(String query) throws Exception {
    StandardQueryParser p = new StandardQueryParser(new MockAnalyzer(random(), MockTokenizer.KEYWORD, false));
    QueryNode node = p.getSyntaxParser().parse(query, "df");
    String queryString = node.toQueryString(new EscapeQuerySyntaxImpl()).toString();
    assertEquals("query string of " + query + " was: " + queryString, p.parse(query, "df"), p.parse(queryString, "df"));
  }

  /** Field "my field": the query string "my field:x" is df:my OR field:x. */
  public void testFieldNameWithSpace() throws Exception {
    assertRoundTrip("my\\ field:x");
  }

  /** Field "a:b": the query string "a:b:x" is a syntax error. */
  public void testFieldNameWithColon() throws Exception {
    assertRoundTrip("a\\:b:x");
  }

  /** The same for a range node (AbstractRangeQueryNode#toQueryString, GITHUB#7865). */
  public void testRangeOnFieldNameWithSpace() throws Exception {
    assertRoundTrip("my\\ field:[a TO b]");
  }

  /** Control: a plain field name round-trips. */
  public void testPlainFieldName() throws Exception {
    assertRoundTrip("field:x");
  }
}
