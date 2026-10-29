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
package org.apache.lucene.queryparser.flexible.core;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * QueryParserHelper#setQueryNodeProcessor documents "The argument can be null, which means that no
 * processor will be used to process the query node tree", and the constructor, setQueryConfigHandler
 * and parse all handle a null processor; setQueryNodeProcessor itself dereferences it.
 */
public class TestSetQueryNodeProcessorNull extends LuceneTestCase {

  public void testNullProcessorMeansNoProcessing() throws Exception {
    StandardQueryParser qp = new StandardQueryParser(new StandardAnalyzer());
    qp.setQueryNodeProcessor(null); // documented as allowed
    assertNull(qp.getQueryNodeProcessor());
    // no processing: the syntax tree is built as is (no analysis, so the term keeps its case)
    assertEquals(new TermQuery(new Term("f", "Foo")), qp.parse("Foo", "f"));
  }

  /** The constructor accepts a null processor, as documented for setQueryNodeProcessor. */
  public void testConstructorAcceptsNullProcessor() throws Exception {
    StandardQueryParser std = new StandardQueryParser(new StandardAnalyzer());
    QueryParserHelper helper =
        new QueryParserHelper(
            std.getQueryConfigHandler(), std.getSyntaxParser(), null, std.getQueryBuilder());
    assertNull(helper.getQueryNodeProcessor());
    assertEquals(new TermQuery(new Term("f", "Foo")), helper.parse("Foo", "f"));
  }
}
