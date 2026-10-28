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

import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * StandardQueryParser documents ("Special character escaping") that any character of a search term
 * can be quoted with a backslash, and that the escaped form is an alternative to the quoted form.
 * The interval-function package documents that term literals inside fn: functions are converted
 * into analyzed text intervals. So an escaped term literal inside fn: should stand for the
 * unescaped text, exactly as it does outside fn:.
 *
 * <p>Each index is a single segment written in add order, so document ids equal array indexes.
 */
public class TestIntervalFnEscapedLiterals extends LuceneTestCase {

  private static final String FIELD = "body";

  // Whitespace-tokenized index: each document is a single token.
  private static final String[] WS_DOCS = {
    "a:b", // 0: the text a:b
    "a\\:b", // 1: the text a\:b (a literal backslash)
    "a\"b", // 2: the text a"b
    "a\\\"b", // 3: the text a\"b (a literal backslash)
    ":(quoted+term):", // 4: the term of the StandardQueryParser javadoc example
    "\\:\\(quoted\\+term\\)\\:", // 5: the same with literal backslashes
    "other" // 6
  };

  private Analyzer whitespace() {
    return new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
  }

  private static Set<Integer> hits(Directory dir, Analyzer analyzer, String query)
      throws Exception {
    StandardQueryParser parser = new StandardQueryParser(analyzer);
    Query q = parser.parse(query, FIELD);
    Set<Integer> result = new TreeSet<>();
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      for (ScoreDoc sd : searcher.search(q, 100).scoreDocs) {
        result.add(sd.doc);
      }
    }
    return result;
  }

  private static Query parse(Analyzer analyzer, String query) throws Exception {
    return new StandardQueryParser(analyzer).parse(query, FIELD);
  }

  private static Directory index(Directory dir, Analyzer analyzer, String[] docs) throws Exception {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      for (String text : docs) {
        Document doc = new Document();
        doc.add(new TextField(FIELD, text, Field.Store.NO));
        w.addDocument(doc);
      }
    }
    return dir;
  }

  private void check(Directory dir, Analyzer analyzer, String query, Set<Integer> expected)
      throws Exception {
    Set<Integer> actual = hits(dir, analyzer, query);
    assertEquals(
        "query text [" + query + "] parsed as [" + parse(analyzer, query) + "]", expected, actual);
  }

  /** Control: outside fn: the escapes are removed, as documented. Expected to pass. */
  public void testControlEscapesOutsideFunction() throws Exception {
    Analyzer a = whitespace();
    try (Directory dir = index(newDirectory(), a, WS_DOCS)) {
      check(dir, a, "a\\:b", Set.of(0));
      check(dir, a, "\"a\\\"b\"", Set.of(2));
      check(dir, a, "a\\:b~0", Set.of(0));
      // the javadoc example and its documented quoted alternative
      check(dir, a, "\\:\\(quoted\\+term\\)\\:", Set.of(4));
      check(dir, a, "\":(quoted+term):\"", Set.of(4));
      // the quoted alternative also works inside fn:
      check(dir, a, "fn:or(\":(quoted+term):\")", Set.of(4));
      check(dir, a, "fn:or(\"a:b\")", Set.of(0));
    }
  }

  /** fn:or(a\:b) should search the term a:b, like the clause a\:b does. */
  public void testUnquotedEscapedLiteralInOr() throws Exception {
    Analyzer a = whitespace();
    try (Directory dir = index(newDirectory(), a, WS_DOCS)) {
      check(dir, a, "fn:or(a\\:b)", Set.of(0));
    }
  }

  /** The javadoc escaping example inside fn: should equal its quoted alternative. */
  public void testJavadocEscapingExampleInsideFunction() throws Exception {
    Analyzer a = whitespace();
    try (Directory dir = index(newDirectory(), a, WS_DOCS)) {
      check(dir, a, "fn:or(\\:\\(quoted\\+term\\)\\:)", Set.of(4));
      assertEquals(
          parse(a, "fn:or(\":(quoted+term):\")"), parse(a, "fn:or(\\:\\(quoted\\+term\\)\\:)"));
    }
  }

  /** A quote inside a quoted fn: literal can only be written escaped; it should mean a". */
  public void testQuotedLiteralWithEscapedQuote() throws Exception {
    Analyzer a = whitespace();
    try (Directory dir = index(newDirectory(), a, WS_DOCS)) {
      check(dir, a, "fn:or(\"a\\\"b\")", Set.of(2));
    }
  }

  /** fn:fuzzyTerm(a\:b 0) (0 edits) should match exactly the term a:b, like a\:b~0 does. */
  public void testFuzzyTermEscapedLiteral() throws Exception {
    Analyzer a = whitespace();
    try (Directory dir = index(newDirectory(), a, WS_DOCS)) {
      check(dir, a, "fn:fuzzyTerm(a\\:b 0)", Set.of(0));
    }
  }

  /** With StandardAnalyzer (a:b is one token), the escaped literal is split into a, b. */
  public void testStandardAnalyzerEscapedLiteral() throws Exception {
    Analyzer a = new StandardAnalyzer(CharArraySet.EMPTY_SET);
    String[] docs = {"a:b", "a b c"};
    try (Directory dir = index(newDirectory(), a, docs)) {
      check(dir, a, "a\\:b", Set.of(0)); // control: outside fn:
      check(dir, a, "fn:or(\"a:b\")", Set.of(0)); // control: quoted inside fn:
      check(dir, a, "fn:or(a\\:b)", Set.of(0));
    }
  }
}
