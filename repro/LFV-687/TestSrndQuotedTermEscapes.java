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
package org.apache.lucene.queryparser.surround.query;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.surround.parser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Surround: the lexer accepts the escapes \" and \\ inside a quoted term (QueryParser.jj, token QUOTED:
 * "Literal non empty term between single quotes, escape quoted quote or backslash by backslash"), but the
 * parser passes the raw text between the quotes to SrndTermQuery, so the backslashes stay in the term. A
 * term that contains a double quote, or a quoted term that contains a backslash, can never be matched.
 */
public class TestSrndQuotedTermEscapes extends LuceneTestCase {

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
      for (String v : new String[] {"a\"b", "c:\\temp dir", "plain"}) {
        Document d = new Document();
        d.add(new StringField("k", v, Field.Store.YES)); // one untokenized term per document
        w.addDocument(d);
      }
    }
    reader = DirectoryReader.open(dir);
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private int count(String surround) throws Exception {
    return searcher.count(QueryParser.parse(surround).makeLuceneQueryField("k", new BasicQueryFactory()));
  }

  public void testControlQuotedTermWithoutEscapes() throws Exception {
    assertEquals(1, count("\"plain\""));
  }

  public void testEscapedQuoteInQuotedTerm() throws Exception {
    // the query text is  "a\"b"  : the term a"b with its quote escaped
    assertEquals("hits for the term a\"b", 1, count("\"a\\\"b\""));
    SrndTermQuery q = (SrndTermQuery) QueryParser.parse("\"a\\\"b\"");
    assertEquals("a\"b", q.getTermText());
  }

  public void testEscapedBackslashInQuotedTerm() throws Exception {
    // the query text is  "c:\\temp dir"  : the term c:\temp dir (it needs quotes for the space and the colon,
    // and a lone backslash inside quotes is a lexical error, so the escape is the only way to write it)
    assertEquals("hits for the term c:\\temp dir", 1, count("\"c:\\\\temp dir\""));
    SrndTermQuery q = (SrndTermQuery) QueryParser.parse("\"c:\\\\temp dir\"");
    assertEquals("c:\\temp dir", q.getTermText());
  }
}
