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

import org.apache.lucene.tests.util.LuceneTestCase;

// toQueryString must print a query string the parser accepts; the unescaped output fails to parse
public class TestEscapeQuerySyntaxRoundTrip extends LuceneTestCase {

  private static void assertRoundTrip(String query, String expected) throws Exception {
    StandardSyntaxParser parser = new StandardSyntaxParser();
    String printed = parser.parse(query, "df").toQueryString(new EscapeQuerySyntaxImpl()).toString();
    assertEquals(expected, printed);
    parser.parse(printed, "df");
  }

  public void testAtSignInsideTerm() throws Exception {
    assertRoundTrip("user\\@example.com", "df:user\\@example.com");
  }

  public void testAndAndTerm() throws Exception {
    assertRoundTrip("\\&&", "df:\\&&");
  }
}
