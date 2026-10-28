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
package org.apache.lucene.analysis.miscellaneous;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Independent check of ConcatenateGraphFilter with a custom tokenSeparator. Public API only.
 *
 * <p>Contract: class javadoc "Concatenates/Joins every incoming token with a separator into one
 * output token"; constructor @param tokenSeparator "Separator to use for concatenation".
 */
public class TestConcatGraphSeparatorV1 extends LuceneTestCase {

  private static Tokenizer whitespace(String text) {
    Tokenizer t = new WhitespaceTokenizer();
    t.setReader(new StringReader(text));
    return t;
  }

  private static Tokenizer keyword(String text) {
    Tokenizer t = new KeywordTokenizer();
    t.setReader(new StringReader(text));
    return t;
  }

  /** Consumes the stream and returns deep copies of every emitted term's bytes. */
  private static List<BytesRef> terms(TokenStream ts) throws IOException {
    List<BytesRef> out = new ArrayList<>();
    TermToBytesRefAttribute att = ts.getAttribute(TermToBytesRefAttribute.class);
    ts.reset();
    while (ts.incrementToken()) {
      out.add(BytesRef.deepCopyOf(att.getBytesRef()));
    }
    ts.end();
    ts.close();
    return out;
  }

  private static void assertSingleTerm(String expected, TokenStream ts) throws IOException {
    List<BytesRef> got = terms(ts);
    assertEquals("number of output terms: " + got, 1, got.size());
    BytesRef exp = new BytesRef(expected);
    assertEquals(
        "expected UTF-8 of \"" + expected + "\" " + exp + " but got " + got.get(0), exp, got.get(0));
  }

  private static ConcatenateGraphFilter concat(TokenStream in, char sep) {
    return new ConcatenateGraphFilter(in, sep, false, 100);
  }

  // ---- controls (expected to pass) ----

  public void testControlAsciiSeparatorNotInsideTokens() throws IOException {
    assertSingleTerm("e-mail-x", concat(whitespace("e mail x"), '-'));
  }

  public void testControlSingleTokenWithoutSeparatorChar() throws IOException {
    assertSingleTerm("email", concat(keyword("email"), '-'));
  }

  // ---- the claim, part 1: ASCII custom separator inside a token gets doubled ----

  public void testSingleTokenContainingSeparatorIsUnchanged() throws IOException {
    // one input token, nothing to join: the output should be the token itself
    assertSingleTerm("e-mail", concat(keyword("e-mail"), '-'));
  }

  public void testJoinWhereFirstTokenContainsSeparator() throws IOException {
    assertSingleTerm("e-mail-x", concat(whitespace("e-mail x"), '-'));
  }

  public void testFactorySpaceSeparatorOnKeywordToken() throws IOException {
    Map<String, String> args = new HashMap<>();
    args.put("tokenSeparator", " ");
    TokenStream ts = new ConcatenateGraphFilterFactory(args).create(keyword("new york"));
    assertSingleTerm("new york", ts);
  }

  // ---- the claim, part 2: non-ASCII custom separator ----

  public void testEnDashSeparator() throws IOException {
    assertSingleTerm("new\u2013york", concat(whitespace("new york"), '\u2013'));
  }

  public void testMiddleDotSeparator() throws IOException {
    assertSingleTerm("new\u00B7york", concat(whitespace("new york"), '\u00B7'));
  }

  public void testFactoryEnDashSeparator() throws IOException {
    Map<String, String> args = new HashMap<>();
    args.put("tokenSeparator", "\u2013");
    TokenStream ts = new ConcatenateGraphFilterFactory(args).create(whitespace("new york"));
    assertSingleTerm("new\u2013york", ts);
  }

  // ---- diagnostic (documents current behaviour, expected to pass on the current code) ----

  /**
   * Doubling is not an unambiguous escape: with preservePositionIncrements=true, tokens "e", hole,
   * "mail" join to the same bytes that the single token "e-mail" is turned into.
   */
  public void testDiagnosticDoublingCollidesWithHole() throws IOException {
    CharArraySet stop = new CharArraySet(List.of("stop"), false);
    TokenStream withHole =
        new ConcatenateGraphFilter(
            new StopFilter(whitespace("e stop mail"), stop), '-', true, 100);
    List<BytesRef> a = terms(withHole);
    List<BytesRef> b = terms(concat(keyword("e-mail"), '-'));
    if (VERBOSE) {
      System.out.println("hole: " + a + " single-token: " + b);
    }
    assertEquals(1, a.size());
    assertEquals(new BytesRef("e--mail"), a.get(0));
    assertEquals(a, b);
  }
}
