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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The classic and flexible grammars treat exactly U+0020, TAB, LF, CR and U+3000 (added by LUCENE-1528) as clause
 * separators. Every other Unicode space - including the ones Java itself classifies as whitespace
 * (Character.isWhitespace), such as EN SPACE, EM SPACE, THIN SPACE and MEDIUM MATHEMATICAL SPACE - is a term
 * character, so an operator written next to one silently becomes part of a search term.
 *
 * <p>Only public API is used.
 */
public class TestUnicodeSpaceBetweenClauses extends LuceneTestCase {

  /** Breakable Unicode spaces (Zs, Character.isWhitespace == true). */
  private static final String[] SPACES = {"\u2002", "\u2003", "\u2009", "\u205F"};

  private static String name(String ws) {
    return String.format("U+%04X", (int) ws.charAt(0));
  }

  public void testClassicOperatorsNextToUnicodeSpace() throws Exception {
    Analyzer a = new StandardAnalyzer();
    Query expected = new QueryParser("body", a).parse("title:foo AND -bar");
    assertEquals(expected, new QueryParser("body", a).parse("title:foo\u3000AND\u3000-bar"));
    for (String ws : SPACES) {
      assertTrue(name(ws), Character.isWhitespace(ws.charAt(0)));
      Query actual = new QueryParser("body", a).parse("title:foo" + ws + "AND" + ws + "-bar");
      assertEquals("separator " + name(ws), expected, actual);
    }
  }

  public void testFlexibleOperatorsNextToUnicodeSpace() throws Exception {
    Analyzer a = new StandardAnalyzer();
    Query expected = new StandardQueryParser(a).parse("title:foo AND -bar", "body");
    for (String ws : SPACES) {
      Query actual = new StandardQueryParser(a).parse("title:foo" + ws + "AND" + ws + "-bar", "body");
      assertEquals("separator " + name(ws), expected, actual);
    }
  }

  /** Search-level effect: the prohibited clause is lost and the document containing bar matches. */
  public void testProhibitedClauseLostAtSearchTime() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new StandardAnalyzer()))) {
        Document withBar = new Document();
        withBar.add(new TextField("title", "foo", Field.Store.NO));
        withBar.add(new TextField("body", "bar", Field.Store.NO));
        w.addDocument(withBar);
        Document withoutBar = new Document();
        withoutBar.add(new TextField("title", "foo", Field.Store.NO));
        withoutBar.add(new TextField("body", "baz", Field.Store.NO));
        w.addDocument(withoutBar);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        QueryParser qp = new QueryParser("body", new StandardAnalyzer());
        assertEquals(1, s.count(qp.parse("title:foo AND -bar")));
        Query em = qp.parse("title:foo\u2003AND\u2003-bar");
        assertEquals("query " + em, 1, s.count(em));
      }
    }
  }
}
