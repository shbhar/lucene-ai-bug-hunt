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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.surround.parser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Inside a quoted surround term the lexer accepts exactly two escapes, \" and \\ (QueryParser.jj,
 * token QUOTED: "escape quoted quote or backslash by backslash"), but the parser keeps the escaping
 * backslashes in the term it searches.
 */
public class TestSurroundQuotedTermEscapes extends LuceneTestCase {

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    // id -> the single untokenized term in field "k"
    String[][] docs = {
      {"plain", "plain"},
      {"quote", "a\"b"}, // a"b
      {"raw-quote", "a\\\"b"}, // a\"b
      {"path", "c:\\temp dir"}, // c:\temp dir
      {"raw-path", "c:\\\\temp dir"} // c:\\temp dir
    };
    for (String[] d : docs) {
      Document doc = new Document();
      doc.add(new StringField("id", d[0], Field.Store.YES));
      doc.add(new StringField("k", d[1], Field.Store.NO));
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

  /** The ids of the documents that the surround query matches in field "k". */
  private List<String> hits(String surround) throws Exception {
    SrndQuery q = QueryParser.parse(surround);
    List<String> ids = new ArrayList<>();
    for (ScoreDoc sd :
        searcher.search(q.makeLuceneQueryField("k", new BasicQueryFactory()), 10).scoreDocs) {
      ids.add(searcher.storedFields().document(sd.doc).get("id"));
    }
    return ids;
  }

  public void testControlQuotedTermWithoutEscapes() throws Exception {
    assertEquals(List.of("plain"), hits("\"plain\""));
  }

  public void testEscapedQuote() throws Exception {
    // query text: "a\"b"  ->  the term a"b
    assertEquals("documents matched by \"a\\\"b\"", List.of("quote"), hits("\"a\\\"b\""));
  }

  public void testEscapedBackslash() throws Exception {
    // query text: "c:\\temp dir"  ->  the term c:\temp dir (quoted for the ':' and the space)
    assertEquals(
        "documents matched by \"c:\\\\temp dir\"", List.of("path"), hits("\"c:\\\\temp dir\""));
  }

  /** Passes today; a fix must keep it passing (SrndQuery.equals and hashCode use toString). */
  public void testToStringRoundTrip() throws Exception {
    for (String text : new String[] {"\"plain\"", "\"a\\\"b\"", "\"c:\\\\temp dir\""}) {
      SrndQuery q = QueryParser.parse(text);
      assertEquals(text, q.toString());
      assertEquals(q, QueryParser.parse(q.toString()));
    }
  }
}
