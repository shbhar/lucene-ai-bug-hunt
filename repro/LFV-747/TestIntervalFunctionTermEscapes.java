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
 * StandardQueryParser documents that "any character can be quoted with a backslash" and that the
 * term literals of interval functions are "converted into (analyzed) text intervals". A term
 * literal inside fn:... must therefore stand for the same text as the same literal outside it.
 */
public class TestIntervalFunctionTermEscapes extends LuceneTestCase {

  /** Indexes docs (doc id = array index) into field body and returns the ids that query hits. */
  private Set<Integer> hits(Analyzer analyzer, String query, String... docs) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        for (String text : docs) {
          Document doc = new Document();
          doc.add(new TextField("body", text, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        Query q = new StandardQueryParser(analyzer).parse(query, "body");
        Set<Integer> ids = new TreeSet<>();
        for (ScoreDoc sd : searcher.search(q, 10).scoreDocs) {
          ids.add(sd.doc);
        }
        return ids;
      }
    }
  }

  private void check(Analyzer analyzer, String query, Set<Integer> expected, String... docs)
      throws Exception {
    Query parsed = new StandardQueryParser(analyzer).parse(query, "body");
    assertEquals(query + " parsed as " + parsed, expected, hits(analyzer, query, docs));
  }

  private Analyzer whitespace() {
    return new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
  }

  /** Whitespace analysis: doc 0 holds the term a:b, doc 1 holds the literal text a\:b. */
  public void testEscapedColonInIntervalTerm() throws Exception {
    Analyzer a = whitespace();
    String[] docs = {"a:b c", "a\\:b c"};
    // control: the escaped term outside an interval function is the term a:b
    check(a, "a\\:b", Set.of(0), docs);
    // the same literal inside interval functions
    check(a, "fn:or(a\\:b)", Set.of(0), docs);
    check(a, "fn:ordered(a\\:b c)", Set.of(0), docs);
    // the documented helper produces exactly this literal: escape("a:b") is a\:b
    check(a, "fn:or(" + QueryParserUtil.escape("a:b") + ")", Set.of(0), docs);
  }

  /** An escaped quote inside a quoted interval literal. */
  public void testEscapedQuoteInQuotedIntervalTerm() throws Exception {
    Analyzer a = whitespace();
    String[] docs = {"a\"b c", "a\\\"b c"};
    check(a, "\"a\\\"b\"", Set.of(0), docs);
    check(a, "fn:or(\"a\\\"b\")", Set.of(0), docs);
  }

  /** StandardAnalyzer keeps a:b as one token; the kept backslash splits the literal into a, b. */
  public void testEscapedColonWithStandardAnalyzer() throws Exception {
    Analyzer a = new StandardAnalyzer();
    String[] docs = {"a:b c", "a b c"};
    check(a, "a\\:b", Set.of(0), docs);
    check(a, "fn:or(a\\:b)", Set.of(0), docs);
  }

  /** fn:fuzzyTerm takes its term the same way. */
  public void testEscapedColonInFuzzyTerm() throws Exception {
    Analyzer a = whitespace();
    String[] docs = {"a:b c", "a\\:b c"};
    check(a, "a\\:b~0", Set.of(0), docs);
    check(a, "fn:fuzzyTerm(a\\:b 0)", Set.of(0), docs);
  }
}
