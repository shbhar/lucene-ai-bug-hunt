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
package org.apache.lucene.analysis.ja;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ja.completion.KatakanaRomanizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.CharsRef;

/**
 * While an input method composes a doubled consonant it shows a small tsu plus the pending letter:
 * typing "sakk" shows "さっｋ", typing "macc" (for 抹茶, "maccha") shows "まっｃ". QUERY mode joins the two
 * tokens and romanizes them ("サッ" + "k" => "sakk"), but romaji_map.txt has such rows only for k, g,
 * s, z, t, d, h, b, p, m, y, r and w, although its full small-tsu rows also double c, j, f and v
 * (ッチャ ccha, ッジ jji, ッフ ffu, ッヴ vvu).
 */
public class TestJapaneseCompletionPendingLetter extends BaseTokenStreamTestCase {

  /** Every letter that the full small-tsu rows of romaji_map.txt double. */
  private static final String DOUBLED_LETTERS = "kgsztdhbpmyrwcjfv";

  public void testSmallTsuPlusPendingLetter() {
    List<String> misses = new ArrayList<>();
    for (char c : DOUBLED_LETTERS.toCharArray()) {
      List<String> got = new ArrayList<>();
      for (CharsRef ref : KatakanaRomanizer.getInstance().romanize(new CharsRef("マッ" + c))) {
        got.add(ref.toString());
      }
      if (got.equals(List.of("ma" + c + c)) == false) {
        misses.add("マッ" + c + " -> " + got + ", expected [ma" + c + c + "]");
      }
    }
    assertTrue(String.join("; ", misses), misses.isEmpty());
  }

  /**
   * {indexed word, what the input method shows while the user types it}; the first is a control.
   */
  private static final String[][] CASES = {
    {"サッカー", "さっｋ"}, // typing sakkaa
    {"抹茶", "まっｃ"}, // typing maccha
    {"バッジ", "ばっｊ"}, // typing bajji
    {"スタッフ", "すたっｆ"} // typing sutaffu
  };

  public void testMidCompositionKeepsSuggestion() throws IOException {
    try (Analyzer index =
            new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.INDEX);
        Analyzer query =
            new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.QUERY)) {
      List<String> misses = new ArrayList<>();
      for (String[] c : CASES) {
        List<String> q = terms(query, c[1]);
        List<String> idx = terms(index, c[0]);
        if (completes(q, idx) == false) {
          misses.add("'" + c[1] + "' " + q + " does not complete " + c[0] + " " + idx);
        }
      }
      assertTrue(String.join("; ", misses), misses.isEmpty());
    }
  }

  /**
   * A completion prefix query matches a one-token suggestion when a query-side token is a prefix of
   * an index-side token.
   */
  private static boolean completes(List<String> queryTerms, List<String> indexTerms) {
    for (String q : queryTerms) {
      for (String t : indexTerms) {
        if (t.startsWith(q)) {
          return true;
        }
      }
    }
    return false;
  }

  private static List<String> terms(Analyzer analyzer, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = analyzer.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }
}
