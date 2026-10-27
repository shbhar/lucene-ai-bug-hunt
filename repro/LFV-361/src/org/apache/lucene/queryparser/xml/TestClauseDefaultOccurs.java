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
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * LuceneCoreQuery.dtd declares {@code <!ATTLIST Clause occurs (should | must | mustnot) "should">}, and
 * BooleanQueryBuilder.getOccursValue returns SHOULD when the attribute is absent ({@code occs == null}). But
 * Element.getAttribute returns "" (never null) for an absent attribute, so a Clause without "occurs" is rejected.
 */
public class TestClauseDefaultOccurs extends LuceneTestCase {

  private static Query parse(String xml) throws ParserException {
    CoreParser parser = new CoreParser("contents", new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false));
    return parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  public void testClauseWithoutOccursDefaultsToShould() throws Exception {
    Query expected =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("f", "a")), Occur.SHOULD)
            .add(new TermQuery(new Term("f", "b")), Occur.MUST)
            .build();
    // passes: occurs="should" spelled out
    assertEquals(
        expected,
        parse(
            "<BooleanQuery fieldName='f'><Clause occurs='should'><TermQuery>a</TermQuery></Clause>"
                + "<Clause occurs='must'><TermQuery>b</TermQuery></Clause></BooleanQuery>"));
    // fails: ParserException 'Invalid value for "occurs" attribute of clause:'
    assertEquals(
        expected,
        parse(
            "<BooleanQuery fieldName='f'><Clause><TermQuery>a</TermQuery></Clause>"
                + "<Clause occurs='must'><TermQuery>b</TermQuery></Clause></BooleanQuery>"));
  }
}
