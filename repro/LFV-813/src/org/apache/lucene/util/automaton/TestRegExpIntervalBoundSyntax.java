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
package org.apache.lucene.util.automaton;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * RegExp class javadoc: "Numerical intervals are specified by non-negative decimal integers and
 * include both end points, and if n and m have the same number of digits, then the conforming
 * strings must have that length (i.e. prefixed by 0's)."
 *
 * <p>The interval bounds are parsed with Integer.parseInt, and the fixed width is taken from the
 * raw lengths of the bound strings. Integer.parseInt also accepts a leading '+' and any Unicode
 * decimal digit, so such bounds are accepted silently, and a '+' is counted as a digit.
 *
 * <p>Each test accepts either outcome a fix could choose: the bound is rejected with
 * IllegalArgumentException, or it is accepted with its documented meaning.
 */
public class TestRegExpIntervalBoundSyntax extends LuceneTestCase {

  /** null if the parser rejects the expression (a loud, acceptable outcome). */
  private static CharacterRunAutomaton compileOrNull(String regexp) {
    RegExp re;
    try {
      re = new RegExp(regexp);
    } catch (IllegalArgumentException rejected) {
      return null;
    }
    return new CharacterRunAutomaton(Operations.determinize(re.toAutomaton(), 10_000));
  }

  /** '<1-9>' and '<+1-+9>' both have one-digit bounds: the conforming strings have length 1. */
  public void testPlusSignCountedAsDigitInFixedWidth() {
    CharacterRunAutomaton control = compileOrNull("<1-9>");
    assertTrue(control.run("5"));
    assertFalse(control.run("05"));

    CharacterRunAutomaton plus = compileOrNull("<+1-+9>");
    if (plus != null) {
      assertTrue("<+1-+9> is the interval [1, 9] with one-digit bounds: must match \"5\"", plus.run("5"));
      assertFalse("<+1-+9> has one-digit bounds: must not match the two-character \"05\"", plus.run("05"));
    }
  }

  /** '<+100-999>': both bounds have three digits, so the conforming strings have length 3. */
  public void testPlusSignOnOneBoundDropsFixedWidth() {
    CharacterRunAutomaton control = compileOrNull("<100-999>");
    assertTrue(control.run("500"));
    assertFalse(control.run("0500"));

    CharacterRunAutomaton plus = compileOrNull("<+100-999>");
    if (plus != null) {
      assertTrue(plus.run("500"));
      assertFalse("<+100-999> has three-digit bounds: must not match \"0500\"", plus.run("0500"));
    }
  }

  /** The same through RegexpQuery (default flags RegExp.ALL) and the classic QueryParser. */
  public void testRegexpQueryAndQueryParser() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        for (String v : new String[] {"5", "05"}) {
          Document d = new Document();
          d.add(new StringField("f", v, Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        Query control = new RegexpQuery(new Term("f", "<1-9>"));
        assertEquals(1, s.count(and(control, "5")));
        assertEquals(0, s.count(and(control, "05")));

        Query regexpQuery;
        try {
          regexpQuery = new RegexpQuery(new Term("f", "<+1-+9>"));
        } catch (IllegalArgumentException rejected) {
          regexpQuery = null;
        }
        if (regexpQuery != null) {
          assertEquals("RegexpQuery <+1-+9> must match the term \"5\"", 1, s.count(and(regexpQuery, "5")));
          assertEquals("RegexpQuery <+1-+9> must not match the term \"05\"", 0, s.count(and(regexpQuery, "05")));
        }

        Query parsed;
        try {
          parsed = new QueryParser("f", new StandardAnalyzer()).parse("/<+1-+9>/");
        } catch (IllegalArgumentException | ParseException rejected) {
          parsed = null;
        }
        if (parsed != null) {
          assertEquals("QueryParser /<+1-+9>/ must match the term \"5\"", 1, s.count(and(parsed, "5")));
          assertEquals("QueryParser /<+1-+9>/ must not match the term \"05\"", 0, s.count(and(parsed, "05")));
        }
      }
    }
  }

  private static Query and(Query q, String term) {
    return new BooleanQuery.Builder()
        .add(q, BooleanClause.Occur.MUST)
        .add(new TermQuery(new Term("f", term)), BooleanClause.Occur.MUST)
        .build();
  }

  /**
   * Non-ASCII decimal digits: an interval written with Arabic-Indic digits one to nine
   * (U+0661-U+0669) is accepted and means the ASCII strings "1".."9"; it never matches the digits
   * that were written.
   */
  public void testNonAsciiDigitBoundsReadAsAscii() {
    CharacterRunAutomaton arabicIndic = compileOrNull("<\u0661-\u0669>");
    if (arabicIndic != null) {
      assertFalse(
          "an interval written with Arabic-Indic digits silently became the ASCII interval [1, 9]:"
              + " it matches \"5\" but not the Arabic-Indic five U+0665",
          arabicIndic.run("5") && !arabicIndic.run("\u0665"));
    }
  }
}
