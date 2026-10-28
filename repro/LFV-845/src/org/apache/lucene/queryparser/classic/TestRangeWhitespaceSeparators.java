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
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Both query grammars declare TAB, LF, CR and U+3000 (IDEOGRAPHIC SPACE) as whitespace that is skipped inside a range
 * ({@code <DEFAULT, Range> SKIP : { <_WHITESPACE> }} in QueryParser.jj, {@code <DEFAULT, Range, Function> SKIP} in
 * StandardSyntaxParser.jj), exactly like U+0020. But RANGE_GOOP is {@code (~[" ", "]", "}"])+}: it excludes only
 * U+0020, so under the lexer's longest-match rule the other four separators become part of a range bound.
 *
 * <p>Only public API is used.
 */
public class TestRangeWhitespaceSeparators extends LuceneTestCase {

  /** The grammar's own _WHITESPACE characters other than U+0020. */
  private static final String[] SEPARATORS = {"\t", "\n", "\r", "\u3000"};

  private static String name(String ws) {
    return String.format("U+%04X", (int) ws.charAt(0));
  }

  /** A range written with TAB/LF/CR/U+3000 around TO parses like the U+0020 form (classic QueryParser). */
  public void testClassicRangeAcceptsGrammarWhitespaceAroundTo() throws Exception {
    Analyzer a = new StandardAnalyzer();
    Query expected = new QueryParser("body", a).parse("date:[20200101 TO 20201231]");
    for (String ws : SEPARATORS) {
      String q = "date:[20200101" + ws + "TO" + ws + "20201231]";
      Query actual;
      try {
        actual = new QueryParser("body", a).parse(q);
      } catch (ParseException e) {
        throw new AssertionError(
            "classic QueryParser rejects a range separated by " + name(ws) + ": " + e.getMessage(), e);
      }
      assertEquals("separator " + name(ws), expected, actual);
    }
  }

  /** Same for the flexible StandardQueryParser, which uses the same token definitions. */
  public void testFlexibleRangeAcceptsGrammarWhitespaceAroundTo() throws Exception {
    Analyzer a = new StandardAnalyzer();
    Query expected = new StandardQueryParser(a).parse("date:[20200101 TO 20201231]", "body");
    for (String ws : SEPARATORS) {
      String q = "date:[20200101" + ws + "TO" + ws + "20201231]";
      Query actual;
      try {
        actual = new StandardQueryParser(a).parse(q, "body");
      } catch (Exception e) {
        throw new AssertionError(
            "StandardQueryParser rejects a range separated by " + name(ws) + ": " + e.getMessage(), e);
      }
      assertEquals("separator " + name(ws), expected, actual);
    }
  }

  /**
   * Silent variant: a line break directly after '[' (and before ']') becomes part of the bounds, so the range
   * "[LF 20200101 TO 20201231 LF]" also matches 20191231 (LF sorts before '2').
   */
  public void testLineBreakInsideBracketsChangesHits() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new StandardAnalyzer()))) {
        for (String v : new String[] {"20191231", "20200101", "20200615", "20201231", "20210101"}) {
          Document d = new Document();
          d.add(new StringField("date", v, Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        QueryParser qp = new QueryParser("body", new StandardAnalyzer());
        int withSpaces = s.count(qp.parse("date:[ 20200101 TO 20201231 ]"));
        assertEquals(3, withSpaces);
        Query multiLine = qp.parse("date:[\n20200101 TO 20201231\n]");
        assertEquals("query " + multiLine, withSpaces, s.count(multiLine));
      }
    }
  }

  /** Silent variant: a TAB before '}' makes an exclusive upper bound admit the bound value itself. */
  public void testTabBeforeExclusiveEndAdmitsUpperBound() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new StandardAnalyzer()))) {
        for (String v : new String[] {"20200101", "20200615", "20201231"}) {
          Document d = new Document();
          d.add(new StringField("date", v, Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        StandardQueryParser flexible = new StandardQueryParser(new StandardAnalyzer());
        assertEquals(1, s.count(flexible.parse("date:{20200101 TO 20201231 }", "body")));
        Query tab = flexible.parse("date:{20200101 TO 20201231\t}", "body");
        assertEquals("query " + tab, 1, s.count(tab));
      }
    }
  }
}
