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

import org.apache.lucene.queryparser.flexible.core.QueryNodeParseException;
import org.apache.lucene.queryparser.flexible.standard.parser.EscapeQuerySyntaxImpl;
import org.apache.lucene.queryparser.flexible.standard.parser.StandardSyntaxParser;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBoostQueryNodeToQueryString extends LuceneTestCase {

  public void testSmallBoostParsesBack() throws Exception {
    QueryNode node = new StandardSyntaxParser().parse("ab^0.0009", "f");
    String printed = node.toQueryString(new EscapeQuerySyntaxImpl()).toString();
    // expected: text that parses back to boost 0.0009; actual: f:ab^9.0E-4, a ParseException
    try {
      QueryNode back = new StandardSyntaxParser().parse(printed, "f");
      assertEquals(0.0009f, ((BoostQueryNode) back).getValue(), 0f);
    } catch (QueryNodeParseException e) {
      throw new AssertionError("expected text that parses back, got " + printed, e);
    }
  }
}
