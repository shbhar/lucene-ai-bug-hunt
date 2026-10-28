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

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.surround.parser.ParseException;
import org.apache.lucene.queryparser.surround.parser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Intended-behaviour devil's advocate probe. Public API only: QueryParser.parse,
 * SrndQuery.makeLuceneQueryField, SrndTermQuery.getTermText, toString/equals.
 *
 * <p>Index: a KeywordAnalyzer TextField (one term per value, a common way to index untokenized
 * values that the surround parser, which does no analysis, is meant to search).
 *
 * <p>Tests named bug* assert the escaping semantics stated by the QUOTED token comment and are
 * expected to FAIL on the stock jar. Tests named info* assert the CURRENT behaviour (expected to
 * pass) and document the facts the devil's-advocate case relies on.
 */
public class TestSrndQuotedEscapesV3 extends LuceneTestCase {
  private static final String[] VALUES = {
    "say \"hi\"", // contains double quotes and a space
    "x\\y z", // contains a backslash and a space
    "x\\\\y z", // contains two backslashes and a space
    "k:v w", // colon and space: quoting needed, no escape involved
  };

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new KeywordAnalyzer()))) {
      for (String v : VALUES) {
        Document d = new Document();
        d.add(new TextField("f", v, Field.Store.YES));
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

  private int count(String q) throws Exception {
    return searcher.count(QueryParser.parse(q).makeLuceneQueryField("f", new BasicQueryFactory()));
  }

  /** The stored values of the matching documents, joined by '|'. */
  private String hits(String q) throws Exception {
    StringBuilder sb = new StringBuilder();
    for (var sd :
        searcher.search(
                QueryParser.parse(q).makeLuceneQueryField("f", new BasicQueryFactory()), 10)
            .scoreDocs) {
      if (sb.length() > 0) sb.append('|');
      sb.append(searcher.storedFields().document(sd.doc).get("f"));
    }
    return sb.toString();
  }

  // ---- control: quoting without escapes is the documented way to search such values ----
  public void testInfoControlQuotedColonSpace() throws Exception {
    assertEquals(1, count("\"k:v w\""));
  }

  // ---- the bug: escapes accepted by the lexer are not removed ----
  public void testBugEscapedQuote() throws Exception {
    // query text:  "say \"hi\""   -> per the QUOTED comment, the term  say "hi"
    String q = "\"say \\\"hi\\\"\"";
    assertEquals("term text", "say \"hi\"", ((SrndTermQuery) QueryParser.parse(q)).getTermText());
  }

  public void testBugEscapedQuoteHits() throws Exception {
    assertEquals(1, count("\"say \\\"hi\\\"\""));
  }

  public void testBugEscapedBackslashHits() throws Exception {
    // query text:  "x\\y z"   -> per the QUOTED comment, the term  x\y z (one backslash)
    assertEquals("x\\y z", hits("\"x\\\\y z\""));
  }

  // ---- facts for the devil's-advocate case (current behaviour; expected to pass) ----
  public void testInfoEscapedBackslashMatchesDoubleBackslashValue() throws Exception {
    // the raw text between the quotes is used: "x\\y z" matches the stored value x\\y z
    assertEquals("x\\\\y z", hits("\"x\\\\y z\""));
    assertEquals("x\\\\y z", ((SrndTermQuery) QueryParser.parse("\"x\\\\y z\"")).getTermText());
  }

  public void testInfoLoneBackslashInQuotesRejected() throws Exception {
    expectThrows(ParseException.class, () -> QueryParser.parse("\"x\\y z\""));
  }

  public void testInfoTruncQuotedKeepsLoneBackslashLiterally() throws Exception {
    // TRUNCQUOTED has no escape mechanism at all: "x\y z"* is the prefix x\y z
    assertEquals("x\\y z", hits("\"x\\y z\"*"));
    // and "x\\y z"* is the prefix x\\y z (two backslashes), not x\y z
    assertEquals("x\\\\y z", hits("\"x\\\\y z\"*"));
  }

  public void testInfoQuoteCannotBeTruncated() throws Exception {
    expectThrows(ParseException.class, () -> QueryParser.parse("\"say \\\"hi\\\"\"*"));
  }

  public void testInfoToStringRoundTripCurrentlyStable() throws Exception {
    // SrndQuery.equals/hashCode use toString() (LUCENE-2945); toString re-emits the raw text in quotes,
    // which re-parses to an equal query today.
    for (String q : new String[] {"\"say \\\"hi\\\"\"", "\"x\\\\y z\""}) {
      SrndQuery p = QueryParser.parse(q);
      assertEquals(q, p.toString());
      assertEquals(p, QueryParser.parse(p.toString()));
    }
  }

  public void testInfoUnquotedBackslashIsLiteral() throws Exception {
    // unquoted terms keep backslashes literally too (there is no escape outside quotes)
    assertEquals("a\\b", ((SrndTermQuery) QueryParser.parse("a\\b")).getTermText());
    // and \\uXXXX is not processed (USER_CHAR_STREAM): the term text is the six characters
    assertEquals("a\\u0022b", ((SrndTermQuery) QueryParser.parse("a\\u0022b")).getTermText());
  }

  // ---- reachability with ordinary tokenized text: WhitespaceAnalyzer keeps quote characters ----
  public void testBugWhitespaceTokenWithQuotesInDistanceQuery() throws Exception {
    try (Directory d2 = newDirectory()) {
      try (IndexWriter w = new IndexWriter(d2, new IndexWriterConfig(new WhitespaceAnalyzer()))) {
        Document d = new Document();
        d.add(new TextField("t", "he said \"hello\" twice", Field.Store.NO));
        w.addDocument(d);
      }
      try (IndexReader r2 = DirectoryReader.open(d2)) {
        IndexSearcher s2 = newSearcher(r2);
        // control: the neighbouring plain term
        assertEquals(1, s2.count(QueryParser.parse("he w said").makeLuceneQueryField("t", new BasicQueryFactory())));
        // the indexed token is  "hello"  (with its quotes); surround query:  said w "\"hello\""
        assertEquals(
            1,
            s2.count(
                QueryParser.parse("said w \"\\\"hello\\\"\"")
                    .makeLuceneQueryField("t", new BasicQueryFactory())));
      }
    }
  }
}
