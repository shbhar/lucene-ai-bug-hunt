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
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestToQueryStringFieldNames extends LuceneTestCase {

  public void testFieldNameWithSpace() throws Exception {
    StandardQueryParser parser = new StandardQueryParser();
    QueryNode node = parser.getSyntaxParser().parse("my\\ field:x", "df");
    String printed = node.toQueryString(new EscapeQuerySyntaxImpl()).toString();
    // expected the printed string to parse back to my field:x; actual "my field:x" is df:my field:x
    assertEquals(
        "printed: " + printed, parser.parse("my\\ field:x", "df"), parser.parse(printed, "df"));
  }
}
