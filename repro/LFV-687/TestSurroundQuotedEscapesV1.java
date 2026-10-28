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
package org.apache.lucene.queryparser.surround.verify;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.surround.parser.ParseException;
import org.apache.lucene.queryparser.surround.parser.QueryParser;
import org.apache.lucene.queryparser.surround.query.BasicQueryFactory;
import org.apache.lucene.queryparser.surround.query.SrndQuery;
import org.apache.lucene.queryparser.surround.query.SrndTermQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The surround grammar documents its QUOTED token as "Literal non empty term between [...] quotes,
 * escape quoted quote or backslash by backslash." Only public API is used here: QueryParser.parse,
 * SrndQuery.makeLuceneQueryField, BasicQueryFactory and SrndTermQuery.getTermText.
 */
public class TestSurroundQuotedEscapesV1 extends LuceneTestCase {

  private static final String F = "f";

  // Untokenized values; "id" identifies which document a query hit.
  private static final String[][] DOCS = {
    {"quote", "a\"b"}, // a"b
    {"backslash", "c:\\temp dir"}, // c:\temp dir
    {"raw-quote", "a\\\"b"}, // a\"b  (the raw token text between the quotes)
    {"raw-backslash", "c:\\\\temp dir"}, // c:\\temp dir
    {"space", "x y"},
    {"unquoted-backslash", "p\\q"} // p\q
  };

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    for (String[] d : DOCS) {
      Document doc = new Document();
      doc.add(new StringField("id", d[0], Field.Store.YES));
      doc.add(new StringField(F, d[1], Field.Store.NO));
      w.addDocument(doc);
    }
    reader = w.getReader();
    w.close();
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private String hits(String surround) throws Exception {
    SrndQuery sq = QueryParser.parse(surround);
    Query q = sq.makeLuceneQueryField(F, new BasicQueryFactory());
    TopDocs td = searcher.search(q, 10);
    StringBuilder sb = new StringBuilder();
    for (ScoreDoc sd : td.scoreDocs) {
      if (sb.length() > 0) sb.append(',');
      sb.append(searcher.storedFields().document(sd.doc).get("id"));
    }
    return sb.toString();
  }

  private String termText(String surround) throws Exception {
    SrndQuery sq = QueryParser.parse(surround);
    assertTrue(sq.getClass().getName(), sq instanceof SrndTermQuery);
    return ((SrndTermQuery) sq).getTermText();
  }

  /** Control: a quoted term without escapes is the literal text between the quotes. */
  public void testControlQuotedNoEscape() throws Exception {
    assertEquals("x y", termText("\"x y\""));
    assertEquals("space", hits("\"x y\""));
  }

  /** Control: an unquoted backslash is an ordinary term character. */
  public void testControlUnquotedBackslash() throws Exception {
    assertEquals("p\\q", termText("p\\q"));
    assertEquals("unquoted-backslash", hits("p\\q"));
  }

  /** Control: inside quotes the lexer only accepts \" and \\ (a lone backslash is rejected). */
  public void testControlLoneBackslashInQuotesRejected() throws Exception {
    expectThrows(ParseException.class, () -> QueryParser.parse("\"p\\q\""));
  }

  /** Query text "a\"b" : by the grammar comment this is the term a"b. */
  public void testEscapedQuoteInsideQuotesHits() throws Exception {
    String query = "\"a\\\"b\"";
    assertEquals("documents hit by " + query, "quote", hits(query));
  }

  public void testEscapedQuoteInsideQuotesTermText() throws Exception {
    String query = "\"a\\\"b\"";
    assertEquals("term text of " + query, "a\"b", termText(query));
  }

  /** Query text "c:\\temp dir" : by the grammar comment this is the term c:\temp dir. */
  public void testEscapedBackslashInsideQuotesHits() throws Exception {
    String query = "\"c:\\\\temp dir\"";
    assertEquals("documents hit by " + query, "backslash", hits(query));
  }

  public void testEscapedBackslashInsideQuotesTermText() throws Exception {
    String query = "\"c:\\\\temp dir\"";
    assertEquals("term text of " + query, "c:\\temp dir", termText(query));
  }
}
