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
package org.apache.lucene.queryparser.classic;

import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.DelegatingAnalyzerWrapper;
import org.apache.lucene.analysis.Tokenizer;
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
import org.apache.lucene.tests.analysis.MockSynonymFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * MultiFieldQueryParser under AND: "all the query's terms must appear, but it doesn't matter in
 * what fields they appear" (the javadocs). So here it must find a document that QueryParser on one
 * of its fields finds, and no document that lacks one of the query's words in every field.
 */
public class TestMultiFieldQueryParserClauseGroups extends LuceneTestCase {

  /** title: split on whitespace only; body: split on every non-letter. Both lowercase. */
  private static Analyzer perField() {
    Analyzer whitespace = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    Analyzer letters = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
    return new DelegatingAnalyzerWrapper(Analyzer.PER_FIELD_REUSE_STRATEGY) {
      @Override
      protected Analyzer getWrappedAnalyzer(String fieldName) {
        return "title".equals(fieldName) ? whitespace : letters;
      }
    };
  }

  /** Letters only, lowercase, plus MockSynonymFilter's multi-word synonym "guinea pig" = cavy. */
  private static Analyzer synonyms() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new MockTokenizer(MockTokenizer.SIMPLE, true);
        return new TokenStreamComponents(t, new MockSynonymFilter(t));
      }
    };
  }

  /** Indexes one document per {title, body} pair (doc ids 0, 1, ...). */
  private static Directory index(Analyzer a, String[]... docs) throws Exception {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
      for (String[] d : docs) {
        Document doc = new Document();
        doc.add(new TextField("title", d[0], Field.Store.NO));
        doc.add(new TextField("body", d[1], Field.Store.NO));
        w.addDocument(doc);
      }
    }
    return dir;
  }

  private static Set<Integer> hits(Directory dir, Query q) throws Exception {
    Set<Integer> docs = new TreeSet<>();
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      for (ScoreDoc sd : new IndexSearcher(r).search(q, 10).scoreDocs) {
        docs.add(sd.doc);
      }
    }
    return docs;
  }

  private static <T extends QueryParser> T and(T parser) {
    parser.setDefaultOperator(QueryParser.Operator.AND);
    return parser;
  }

  private static final String[][] DOCS = {
    {"wi-fi router", "cheap and fast"}, // doc 0: the title holds the whole query
    {"router", "wi router"} // doc 1: no "wi-fi" and no "fi" in any field
  };

  /** Released since 7.0.0 (default splitOnWhitespace=false): misses doc 0, matches doc 1. */
  public void testTwoWordsPerFieldAnalyzers() throws Exception {
    Analyzer a = perField();
    String text = "wi-fi router";
    try (Directory dir = index(a, DOCS)) {
      Set<Integer> expected = new TreeSet<>();
      expected.addAll(hits(dir, and(new QueryParser("title", a)).parse(text))); // doc 0
      expected.addAll(hits(dir, and(new QueryParser("body", a)).parse(text))); // none
      assertEquals(Set.of(0), expected);
      Query q = and(new MultiFieldQueryParser(new String[] {"title", "body"}, a)).parse(text);
      assertEquals(q.toString(), expected, hits(dir, q));
    }
  }

  /** Since #16446 (unreleased): a single word misses the title match too. */
  public void testOneWordPerFieldAnalyzers() throws Exception {
    Analyzer a = perField();
    try (Directory dir = index(a, DOCS)) {
      Query title = and(new QueryParser("title", a)).parse("wi-fi");
      assertEquals(Set.of(0), hits(dir, title));
      Query q = and(new MultiFieldQueryParser(new String[] {"title", "body"}, a)).parse("wi-fi");
      assertTrue(q + " must find doc 0", hits(dir, q).contains(0));
    }
  }

  /** Since #16446 (unreleased): a word and its synonym are both required, even on one field. */
  public void testMultiWordSynonymOneField() throws Exception {
    Analyzer a = synonyms();
    Analyzer plain = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true); // no synonyms
    try (Directory dir = index(plain, new String[] {"pets", "my guinea pig"})) {
      QueryParser single = and(new QueryParser("body", a));
      MultiFieldQueryParser multi = and(new MultiFieldQueryParser(new String[] {"body"}, a));
      for (QueryParser p : new QueryParser[] {single, multi}) {
        p.setSplitOnWhitespace(true);
        p.setAutoGeneratePhraseQueries(true);
      }
      Query expected = single.parse("guinea-pig"); // body:"guinea pig" body:cavy
      assertEquals(Set.of(0), hits(dir, expected));
      Query q = multi.parse("guinea-pig");
      assertEquals(q.toString(), Set.of(0), hits(dir, q));
    }
  }
}
