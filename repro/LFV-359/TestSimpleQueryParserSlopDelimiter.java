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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * An operator, quote or parenthesis typed directly after the slop of a phrase ("a b"~2+c) must keep
 * its meaning, as it does after a space ("a b"~2 +c) and after a phrase without slop ("a b"+c).
 */
public class TestSimpleQueryParserSlopDelimiter extends LuceneTestCase {

  private static Query parse(String text, Occur defaultOperator) {
    // MockAnalyzer: whitespace tokenizer, lowercase
    SimpleQueryParser parser = new SimpleQueryParser(new MockAnalyzer(random()), "f");
    parser.setDefaultOperator(defaultOperator);
    return parser.parse(text);
  }

  private static Query term(String text) {
    return new TermQuery(new Term("f", text));
  }

  private static Query phrase(int slop, String... terms) {
    return new PhraseQuery(slop, "f", terms);
  }

  private static Query bool(Query a, Occur occurA, Query b, Occur occurB) {
    return new BooleanQuery.Builder().add(a, occurA).add(b, occurB).build();
  }

  /** '+' (AND) directly after the slop, default operator OR. */
  public void testAndAfterSlop() {
    Query expected = bool(phrase(2, "a", "b"), Occur.MUST, term("c"), Occur.MUST);
    assertEquals(expected, parse("\"a b\"~2 +c", Occur.SHOULD)); // space before '+': passes
    assertEquals(
        bool(phrase(0, "a", "b"), Occur.MUST, term("c"), Occur.MUST),
        parse("\"a b\"+c", Occur.SHOULD)); // no slop: passes
    assertEquals(expected, parse("\"a b\"~2+c", Occur.SHOULD)); // fails: phrase OR c
  }

  /** The empty slop ("a b"~, which means slop 2) followed directly by '+'. */
  public void testAndAfterEmptySlop() {
    Query expected = bool(phrase(2, "a", "b"), Occur.MUST, term("c"), Occur.MUST);
    assertEquals(expected, parse("\"a b\"~ +c", Occur.SHOULD)); // passes
    assertEquals(expected, parse("\"a b\"~+c", Occur.SHOULD)); // fails: phrase OR c
  }

  /** '|' (OR) directly after the slop, default operator AND. */
  public void testOrAfterSlop() {
    Query expected = bool(phrase(2, "a", "b"), Occur.SHOULD, term("c"), Occur.SHOULD);
    assertEquals(expected, parse("\"a b\"~2 |c", Occur.MUST)); // passes
    assertEquals(expected, parse("\"a b\"~2|c", Occur.MUST)); // fails: phrase AND c
  }

  /** A quote directly after the slop opens a second phrase. */
  public void testPhraseAfterSlop() {
    Query expected = bool(phrase(2, "a", "b"), Occur.SHOULD, phrase(0, "c", "d"), Occur.SHOULD);
    assertEquals(expected, parse("\"a b\"~2 \"c d\"", Occur.SHOULD)); // passes
    assertEquals(expected, parse("\"a b\"~2\"c d\"", Occur.SHOULD)); // fails: phrase OR c OR d
  }

  /** '(' directly after the slop opens a group. */
  public void testGroupAfterSlop() {
    Query group = bool(term("c"), Occur.MUST, term("d"), Occur.MUST);
    Query expected = bool(phrase(2, "a", "b"), Occur.SHOULD, group, Occur.SHOULD);
    assertEquals(expected, parse("\"a b\"~2 (c+d)", Occur.SHOULD)); // passes
    assertEquals(expected, parse("\"a b\"~2(c+d)", Occur.SHOULD)); // fails: (phrase OR c) AND d
  }

  /** Forms that are not affected today: a fuzzy term, and a ')' that closes a group. */
  public void testUnaffectedForms() {
    Query fuzzyAndB =
        bool(new FuzzyQuery(new Term("f", "a"), 1), Occur.MUST, term("b"), Occur.MUST);
    assertEquals(fuzzyAndB, parse("a~1+b", Occur.SHOULD));
    assertEquals(parse("(x|\"a b\"~2 )+c", Occur.SHOULD), parse("(x|\"a b\"~2)+c", Occur.SHOULD));
  }

  /** The lost '+' changes the hits: one document has "new york" and "pizza", two do not. */
  public void testHits() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    for (String text : new String[] {"new york pizza", "new york bagels", "chicago pizza"}) {
      Document doc = new Document();
      doc.add(new TextField("f", text, Field.Store.NO));
      w.addDocument(doc);
    }
    IndexReader reader = w.getReader();
    w.close();
    try {
      IndexSearcher searcher = newSearcher(reader);
      assertEquals(1, searcher.count(parse("\"new york\"~2 +pizza", Occur.SHOULD))); // passes
      assertEquals(1, searcher.count(parse("\"new york\"+pizza", Occur.SHOULD))); // passes
      assertEquals(1, searcher.count(parse("\"new york\"~2+pizza", Occur.SHOULD))); // fails: 3
    } finally {
      reader.close();
      dir.close();
    }
  }
}
