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
package org.apache.lucene.analysis.shingle;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.FlattenGraphFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.CharsRefBuilder;

/**
 * Version-portable form of TestFixedShingleFilterRouteGaps (plain main(), only core and
 * analysis-common public API, no test-framework classes), so the same checks run on every release
 * that has FixedShingleFilter (7.4.0 and later).
 *
 * <p>BUG checks encode the expected output (see the candidate's "expected" field); a failing BUG
 * check means the defect shows on this version. CONTROL checks use the same inputs without the
 * trigger and should pass everywhere (the posLen control is expected to fail on versions whose
 * FixedShingleFilter ignores position lengths). Shingles are compared by term text; positions are
 * printed for information. Exit code 3 when any BUG check fails, 0 otherwise.
 */
public class RouteGapHistoryCheck {

  /** A fixed token stream: {term, posInc, posLen, startOffset, endOffset}. */
  static final class Canned extends TokenStream {
    private final Object[][] tokens;
    private int upto;
    private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute inc = addAttribute(PositionIncrementAttribute.class);
    private final PositionLengthAttribute len = addAttribute(PositionLengthAttribute.class);
    private final OffsetAttribute off = addAttribute(OffsetAttribute.class);
    private final int finalOffset;

    Canned(Object[]... tokens) {
      this.tokens = tokens;
      int max = 0;
      for (Object[] t : tokens) {
        max = Math.max(max, (Integer) t[4]);
      }
      this.finalOffset = max;
    }

    @Override
    public boolean incrementToken() {
      if (upto == tokens.length) {
        return false;
      }
      Object[] t = tokens[upto++];
      clearAttributes();
      term.setEmpty().append((String) t[0]);
      inc.setPositionIncrement((Integer) t[1]);
      len.setPositionLength((Integer) t[2]);
      off.setOffset((Integer) t[3], (Integer) t[4]);
      return true;
    }

    @Override
    public void end() throws IOException {
      super.end();
      inc.setPositionIncrement(0);
      off.setOffset(finalOffset, finalOffset);
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      upto = 0;
    }
  }

  static Object[] tok(String term, int posInc, int posLen, int start, int end) {
    return new Object[] {term, posInc, posLen, start, end};
  }

  /** Emitted shingles as "position:term". */
  static List<String> shingles(TokenStream ts) throws IOException {
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    PositionIncrementAttribute inc = ts.addAttribute(PositionIncrementAttribute.class);
    List<String> out = new ArrayList<>();
    int pos = -1;
    ts.reset();
    while (ts.incrementToken()) {
      pos += inc.getPositionIncrement();
      out.add(pos + ":" + term);
    }
    ts.end();
    ts.close();
    return out;
  }

  static TreeSet<String> terms(List<String> shingles) {
    TreeSet<String> s = new TreeSet<>();
    for (String x : shingles) {
      s.add(x.substring(x.indexOf(':') + 1));
    }
    return s;
  }

  static List<String> foldChain(String text, int size) throws IOException {
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(text));
    TokenStream ts = new StopFilter(tokenizer, new CharArraySet(Arrays.asList("the"), false));
    ts = new ASCIIFoldingFilter(ts, true);
    return shingles(new FixedShingleFilter(ts, size));
  }

  static List<String> synonymChain(String text, boolean removeIn) throws IOException {
    SynonymMap.Builder builder = new SynonymMap.Builder(true);
    builder.add(
        SynonymMap.Builder.join(new String[] {"log", "in"}, new CharsRefBuilder()),
        new CharsRef("login"),
        true);
    SynonymMap map = builder.build();
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(text));
    TokenStream ts = new SynonymGraphFilter(tokenizer, map, true);
    ts = new FlattenGraphFilter(ts);
    if (removeIn) {
      ts = new StopFilter(ts, new CharArraySet(Arrays.asList("in"), false));
    }
    return shingles(new FixedShingleFilter(ts, 2));
  }

  static int bugFailures = 0;
  static int controlFailures = 0;

  static void check(String kind, String name, boolean ok, List<String> out) {
    System.out.println(kind + " " + (ok ? "PASS" : "FAIL") + " " + name + "  " + out);
    if (!ok) {
      if (kind.equals("BUG")) {
        bugFailures++;
      } else {
        controlFailures++;
      }
    }
  }

  public static void main(String[] args) throws Exception {
    // 1. Finder test testStackedTokensAfterHole: a, hole, b/c stacked.
    List<String> s2 =
        shingles(
            new FixedShingleFilter(
                new Canned(tok("a", 1, 1, 0, 1), tok("b", 2, 1, 6, 7), tok("c", 0, 1, 6, 7)), 2));
    check("BUG", "stacked-after-hole size2: no 'a c'", !terms(s2).contains("a c"), s2);
    List<String> s3 =
        shingles(
            new FixedShingleFilter(
                new Canned(tok("a", 1, 1, 0, 1), tok("b", 2, 1, 6, 7), tok("c", 0, 1, 6, 7)), 3));
    check(
        "BUG",
        "stacked-after-hole size3: 'a _ b' and 'a _ c'",
        terms(s3).contains("a _ b") && terms(s3).contains("a _ c"),
        s3);

    // Order metamorphic check: swapping the two stacked tokens must give the same shingle set.
    List<String> s2swap =
        shingles(
            new FixedShingleFilter(
                new Canned(tok("a", 1, 1, 0, 1), tok("c", 2, 1, 6, 7), tok("b", 0, 1, 6, 7)), 2));
    check(
        "BUG",
        "stacked-after-hole size2: order of b/c does not matter",
        terms(s2).equals(terms(s2swap)),
        s2swap);

    // 2. Finder test testFoldingPreserveOriginalAfterStopWord.
    List<String> f2 = foldChain("coffee at the café", 2);
    check(
        "BUG",
        "StopFilter->ASCIIFolding(preserveOriginal) size2: 'at _', no 'at café'",
        terms(f2).contains("at _") && !terms(f2).contains("at café"),
        f2);
    List<String> f3 = foldChain("coffee at the café", 3);
    check(
        "BUG",
        "StopFilter->ASCIIFolding(preserveOriginal) size3: 'at _ cafe', 'at _ café', no 'coffee at café'",
        terms(f3).contains("at _ cafe")
            && terms(f3).contains("at _ café")
            && !terms(f3).contains("coffee at café"),
        f3);

    // 3. Finder test testMultiPositionTokenFollowedByTokenCanned.
    List<String> m1 =
        shingles(
            new FixedShingleFilter(
                new Canned(
                    tok("login", 1, 2, 0, 6), tok("log", 0, 1, 0, 3), tok("page", 2, 1, 7, 11)),
                2));
    check(
        "BUG",
        "multi-position canned: 'log _' and 'login page'",
        terms(m1).contains("log _") && terms(m1).contains("login page"),
        m1);

    // 4. Finder test testMultiPositionTokenFollowedByToken (synonym, flatten, stop 'in').
    List<String> m2 = synonymChain("log in page", true);
    check(
        "BUG",
        "multi-position synonym chain: 'log _' and 'login page'",
        terms(m2).contains("log _") && terms(m2).contains("login page"),
        m2);

    // Controls.
    List<String> c1 =
        shingles(
            new FixedShingleFilter(
                new Canned(tok("a", 1, 1, 0, 1), tok("b", 1, 1, 2, 3), tok("c", 0, 1, 2, 3)), 2));
    check(
        "CONTROL",
        "stacked without hole size2: 'a b' and 'a c'",
        terms(c1).contains("a b") && terms(c1).contains("a c"),
        c1);
    List<String> c2 =
        shingles(new FixedShingleFilter(new Canned(tok("a", 1, 1, 0, 1), tok("b", 2, 1, 6, 7)), 3));
    check("CONTROL", "hole without stack size3: 'a _ b'", terms(c2).contains("a _ b"), c2);
    List<String> c3 = foldChain("coffee at a café", 3);
    check(
        "CONTROL",
        "no stop word size3: 'at a cafe' and 'at a café'",
        terms(c3).contains("at a cafe") && terms(c3).contains("at a café"),
        c3);
    List<String> c4 = synonymChain("log in page", false);
    check(
        "CONTROL",
        "posLen-aware routes (needs a posLen-aware filter): 'login page', no 'login in'",
        terms(c4).contains("login page") && !terms(c4).contains("login in"),
        c4);

    System.out.println(
        "SUMMARY bug_check_failures=" + bugFailures + " control_failures=" + controlFailures);
    if (bugFailures > 0) {
      System.exit(3);
    }
  }
}
