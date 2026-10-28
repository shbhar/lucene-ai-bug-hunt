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
package org.apache.lucene.queryparser.flexible.core.nodes;

import org.apache.lucene.queryparser.flexible.standard.parser.EscapeQuerySyntaxImpl;
import org.apache.lucene.queryparser.flexible.standard.parser.StandardSyntaxParser;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * QueryNode#toQueryString: "convert to a query string understood by the query parser"; the
 * flexible core nodes package javadoc: "A query node tree can also generate a query string that
 * can be parsed back by the original text parser". The phrase slop is an int (parsed with
 * Integer.parseInt), so printing it must keep its value. Only public API is used.
 */
public class TestSlopNodeToQueryStringRoundTrip extends LuceneTestCase {

  private static int reparsedSlop(String query) throws Exception {
    StandardSyntaxParser parser = new StandardSyntaxParser();
    QueryNode node = parser.parse(query, "f");
    assertTrue(node.toString(), node instanceof SlopQueryNode);
    CharSequence printed = node.toQueryString(new EscapeQuerySyntaxImpl());
    QueryNode back = new StandardSyntaxParser().parse(printed, "f");
    assertTrue(printed + " -> " + back, back instanceof SlopQueryNode);
    return ((SlopQueryNode) back).getValue();
  }

  /** 2^24 + 1 is the smallest slop that a float cannot hold. */
  public void testSlopAboveTwoToThe24RoundTrips() throws Exception {
    StandardSyntaxParser parser = new StandardSyntaxParser();
    SlopQueryNode node = (SlopQueryNode) parser.parse("\"a b\"~16777217", "f");
    assertEquals(16777217, node.getValue()); // the parser keeps the exact int
    String printed = node.toQueryString(new EscapeQuerySyntaxImpl()).toString();
    assertTrue("printed slop changed: " + printed, printed.endsWith("~16777217"));
    assertEquals(16777217, reparsedSlop("\"a b\"~16777217"));
  }

  /** Integer.MAX_VALUE, a common "any distance" slop, is printed as 2147483648. */
  public void testMaxIntSlopIsPrintedExactly() throws Exception {
    StandardSyntaxParser parser = new StandardSyntaxParser();
    SlopQueryNode node = (SlopQueryNode) parser.parse("\"a b\"~2147483647", "f");
    assertEquals(Integer.MAX_VALUE, node.getValue());
    String printed = node.toQueryString(new EscapeQuerySyntaxImpl()).toString();
    assertTrue("printed slop changed: " + printed, printed.endsWith("~2147483647"));
  }

  /** The same helper in PhraseSlopQueryNode (built directly, the parser does not create it). */
  public void testPhraseSlopQueryNodePrintsExactSlop() throws Exception {
    PhraseSlopQueryNode node =
        new PhraseSlopQueryNode(new QuotedFieldQueryNode("f", "a b", 0, 3), 123456789);
    String printed = node.toQueryString(new EscapeQuerySyntaxImpl()).toString();
    assertTrue("printed slop changed: " + printed, printed.endsWith("~123456789"));
  }
}
