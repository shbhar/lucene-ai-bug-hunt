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
package org.apache.lucene.queryparser.xml;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCoreParserCdataText extends LuceneTestCase {

  public void testCdataSectionText() throws Exception {
    CoreParser parser = new CoreParser("f", new StandardAnalyzer());
    // expected (f:a f:b) as for the plain text "a b", actual (f:a): the CDATA text is dropped
    assertEquals(parse(parser, "a b"), parse(parser, "a <![CDATA[b]]>"));
  }

  private static Query parse(CoreParser parser, String text) throws ParserException {
    String xml = "<TermsQuery fieldName=\"f\">" + text + "</TermsQuery>";
    return parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }
}
