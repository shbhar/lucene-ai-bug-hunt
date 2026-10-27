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
package org.apache.lucene.queryparser.simple;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The class javadoc says that whitespace and the operators ( ) + | " delimit tokens, so a sloppy phrase followed
 * directly by an operator must parse like the same phrase followed by a space and the operator, and like a phrase
 * without slop followed directly by the operator. After a phrase with a "~N" slop, the parser skips the character
 * that ends the slop number, so that operator is lost.
 */
public class TestSimpleQueryParserPhraseSlopDelimiter extends LuceneTestCase {

  private static SimpleQueryParser parser(Occur defaultOperator) {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    SimpleQueryParser parser = new SimpleQueryParser(analyzer, "field");
    parser.setDefaultOperator(defaultOperator);
    return parser;
  }

  private static Query and(Query a, Query b) {
    return new BooleanQuery.Builder().add(a, Occur.MUST).add(b, Occur.MUST).build();
  }

  private static Query or(Query a, Query b) {
    return new BooleanQuery.Builder().add(a, Occur.SHOULD).add(b, Occur.SHOULD).build();
  }

  /** "a b"~2+c must be phrase AND c, as "a b"~2 +c and "a b"+c are. */
  public void testAndDirectlyAfterSloppyPhrase() {
    SimpleQueryParser p = parser(Occur.SHOULD);
    Query expected = and(new PhraseQuery(2, "field", "a", "b"), new TermQuery(new Term("field", "c")));
    assertEquals(expected, p.parse("\"a b\"~2 +c")); // passes: space before '+'
    assertEquals(
        and(new PhraseQuery("field", "a", "b"), new TermQuery(new Term("field", "c"))),
        p.parse("\"a b\"+c")); // passes: no slop
    assertEquals(expected, p.parse("\"a b\"~2+c")); // fails: '+' is skipped, query is phrase OR c
  }

  /** With default operator MUST, "a b"~2|c must be phrase OR c. */
  public void testOrDirectlyAfterSloppyPhrase() {
    SimpleQueryParser p = parser(Occur.MUST);
    Query expected = or(new PhraseQuery(2, "field", "a", "b"), new TermQuery(new Term("field", "c")));
    assertEquals(expected, p.parse("\"a b\"~2 |c")); // passes
    assertEquals(expected, p.parse("\"a b\"~2|c")); // fails: '|' is skipped, query is phrase AND c
  }

  /** "a b"~2"c d" must be two phrases; the second phrase's opening quote is skipped instead. */
  public void testPhraseDirectlyAfterSloppyPhrase() {
    SimpleQueryParser p = parser(Occur.SHOULD);
    Query expected = or(new PhraseQuery(2, "field", "a", "b"), new PhraseQuery("field", "c", "d"));
    assertEquals(expected, p.parse("\"a b\"~2 \"c d\"")); // passes
    assertEquals(expected, p.parse("\"a b\"~2\"c d\"")); // fails: parsed as phrase OR c OR d
  }

  /** The lost operator changes the hits: "a b"~2+c must match no document of this index, but matches both. */
  public void testWrongHits() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    for (String text : new String[] {"a b", "c"}) {
      Document doc = new Document();
      doc.add(new TextField("field", text, Field.Store.NO));
      w.addDocument(doc);
    }
    IndexReader r = w.getReader();
    w.close();
    IndexSearcher s = newSearcher(r);
    SimpleQueryParser p = parser(Occur.SHOULD);
    try {
      assertEquals(0, s.count(p.parse("\"a b\"~2 +c"))); // passes
      assertEquals(0, s.count(p.parse("\"a b\"~2+c"))); // fails: 2 hits
    } finally {
      r.close();
      dir.close();
    }
  }
}
