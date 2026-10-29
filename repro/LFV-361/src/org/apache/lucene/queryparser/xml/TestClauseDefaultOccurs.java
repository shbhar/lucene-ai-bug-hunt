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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestClauseDefaultOccurs extends LuceneTestCase {

  public void testClauseWithoutOccursIsShould() throws Exception {
    String xml =
        "<BooleanQuery fieldName='f'><Clause><TermQuery>a</TermQuery></Clause></BooleanQuery>";
    Query expected =
        new BooleanQuery.Builder().add(new TermQuery(new Term("f", "a")), Occur.SHOULD).build();
    try (Analyzer analyzer = new StandardAnalyzer()) {
      Query actual;
      try {
        actual =
            new CoreParser("f", analyzer)
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      } catch (ParserException e) {
        throw new AssertionError("expected one SHOULD clause f:a (default occurs), got " + e, e);
      }
      assertEquals(expected, actual);
    }
  }
}
