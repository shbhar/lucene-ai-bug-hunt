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
package org.apache.lucene.queryparser.v1check;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Both query grammars declare TAB, LF, CR and U+3000 as whitespace (next to U+0020) and SKIP
 * whitespace in the range lexical state. These tests check that a range written with one of those
 * characters instead of a plain space parses to the same query as the space-separated range.
 * Public API only.
 */
public class TestRangeWhitespaceV1 extends LuceneTestCase {

  private static final String[] WS = {"\t", "\n", "\r", "\u3000"};

  private static final String REF_INCL = "date:[20200101 TO 20201231]";
  private static final String REF_EXCL = "date:{20200101 TO 20201231}";

  /** Templates, '#' is replaced by the whitespace character; second element is the reference. */
  private static final String[][] SEPARATOR_TEMPLATES = {
    {"date:[20200101#TO#20201231]", REF_INCL},
    {"date:[20200101#TO 20201231]", REF_INCL},
    {"date:[20200101 TO#20201231]", REF_INCL},
  };

  private static final String[][] EDGE_TEMPLATES = {
    {"date:[#20200101 TO 20201231]", REF_INCL},
    {"date:[20200101 TO 20201231#]", REF_INCL},
    {"date:{#20200101 TO 20201231}", REF_EXCL},
    {"date:{20200101 TO 20201231#}", REF_EXCL},
  };

  interface Parser {
    Query parse(String s) throws Exception;
  }

  private static Parser classic(Analyzer a) {
    return s -> new QueryParser("body", a).parse(s);
  }

  private static Parser flexible(Analyzer a) {
    return s -> new StandardQueryParser(a).parse(s, "body");
  }

  static String show(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      switch (c) {
        case '\t' -> sb.append("<TAB>");
        case '\n' -> sb.append("<LF>");
        case '\r' -> sb.append("<CR>");
        case '\u3000' -> sb.append("<U+3000>");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }

  static String describe(Query q) {
    if (q instanceof TermRangeQuery trq) {
      return "TermRangeQuery(lower="
          + (trq.getLowerTerm() == null ? "null" : "'" + show(trq.getLowerTerm().utf8ToString()) + "'")
          + ", upper="
          + (trq.getUpperTerm() == null ? "null" : "'" + show(trq.getUpperTerm().utf8ToString()) + "'")
          + ", incl="
          + trq.includesLower()
          + "/"
          + trq.includesUpper()
          + ")";
    }
    return q.getClass().getSimpleName() + "(" + show(q.toString()) + ")";
  }

  private static List<String> check(String label, Parser p, String[][] templates) throws Exception {
    List<String> bad = new ArrayList<>();
    for (String[] t : templates) {
      Query ref = p.parse(t[1]);
      for (String ws : WS) {
        String in = t[0].replace("#", ws);
        String outcome;
        try {
          Query got = p.parse(in);
          if (got.equals(ref)) {
            outcome = null;
          } else {
            outcome = "parsed to " + describe(got) + " but expected " + describe(ref);
          }
        } catch (Exception e) {
          outcome = "threw " + e.getClass().getSimpleName() + ": " + show(String.valueOf(e.getMessage())).split("\n|<LF>")[0];
        }
        String line = label + " '" + show(in) + "': " + (outcome == null ? "OK" : outcome);
        System.out.println(line);
        if (outcome != null) {
          bad.add(line);
        }
      }
    }
    return bad;
  }

  private static void assertNone(List<String> bad, int total) {
    if (!bad.isEmpty()) {
      fail(bad.size() + " of " + total + " inputs differ from the space-separated range:\n  " + String.join("\n  ", bad));
    }
  }

  /** Control: in the DEFAULT state the same four characters separate clauses like a space. */
  public void testControlDefaultStateTreatsThemAsWhitespace() throws Exception {
    Analyzer a = new StandardAnalyzer();
    for (Parser p : new Parser[] {classic(a), flexible(a)}) {
      Query ref = p.parse("body:alpha body:beta");
      for (String ws : WS) {
        assertEquals(show(ws), ref, p.parse("body:alpha" + ws + "body:beta"));
      }
      // and inside a range a plain space works, with extra spaces too
      assertEquals(p.parse(REF_INCL), p.parse("date:[  20200101   TO   20201231  ]"));
    }
    a.close();
  }

  public void testClassicRangeSeparatedByGrammarWhitespace() throws Exception {
    Analyzer a = new StandardAnalyzer();
    assertNone(check("classic", classic(a), SEPARATOR_TEMPLATES), SEPARATOR_TEMPLATES.length * WS.length);
    a.close();
  }

  public void testClassicRangeBoundsNextToBrackets() throws Exception {
    Analyzer a = new StandardAnalyzer();
    assertNone(check("classic", classic(a), EDGE_TEMPLATES), EDGE_TEMPLATES.length * WS.length);
    a.close();
  }

  public void testFlexibleRangeSeparatedByGrammarWhitespace() throws Exception {
    Analyzer a = new StandardAnalyzer();
    assertNone(check("flexible", flexible(a), SEPARATOR_TEMPLATES), SEPARATOR_TEMPLATES.length * WS.length);
    a.close();
  }

  public void testFlexibleRangeBoundsNextToBrackets() throws Exception {
    Analyzer a = new StandardAnalyzer();
    assertNone(check("flexible", flexible(a), EDGE_TEMPLATES), EDGE_TEMPLATES.length * WS.length);
    a.close();
  }

  /**
   * Informational: the same LF is skipped when followed by a space, so a pretty-printed lower bound
   * is right while a compact one is not. Asserts only the expected (space-equivalent) behaviour.
   */
  public void testClassicLfThenSpaceVsLfThenTerm() throws Exception {
    Analyzer a = new StandardAnalyzer();
    Parser p = classic(a);
    Query ref = p.parse(REF_INCL);
    Query lfSpace = p.parse("date:[\n 20200101 TO 20201231]");
    Query lfTerm = p.parse("date:[\n20200101 TO 20201231]");
    System.out.println("classic '[<LF> 20200101 TO 20201231]' -> " + describe(lfSpace));
    System.out.println("classic '[<LF>20200101 TO 20201231]'  -> " + describe(lfTerm));
    assertEquals("LF followed by a space", ref, lfSpace);
    assertEquals("LF directly before the lower bound", ref, lfTerm);
    a.close();
  }

  /** End to end: the changed bounds change the hit count. */
  public void testHitCountsChange() throws Exception {
    String[] values = {"20191231", "20200101", "20200615", "20201231", "20210101"};
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new StandardAnalyzer()))) {
        for (String v : values) {
          Document d = new Document();
          d.add(new StringField("date", v, Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        Analyzer a = new StandardAnalyzer();
        List<String> bad = new ArrayList<>();
        String[][] cases = {
          {"date:[\n20200101 TO 20201231\n]", REF_INCL},
          {"date:{20200101 TO 20201231\t}", REF_EXCL},
          {"date:[\u300020200101 TO 20201231]", REF_INCL},
        };
        for (Parser p : new Parser[] {classic(a), flexible(a)}) {
          for (String[] c : cases) {
            int want = s.count(p.parse(c[1]));
            int got = s.count(p.parse(c[0]));
            String line = "'" + show(c[0]) + "' hits=" + got + " (space version hits=" + want + ")";
            System.out.println(line);
            if (got != want) {
              bad.add(line);
            }
          }
        }
        a.close();
        assertNone(bad, cases.length * 2);
      }
    }
  }
}
