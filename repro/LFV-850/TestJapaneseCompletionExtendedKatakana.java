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
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * romaji_map.txt has rows for シェ (she), ジェ (je), ティ (thi), ウィ (wi) and even ッイェ (yye), but none for
 * チェ, イェ, ツァ/ツィ/ツェ/ツォ, ディ, デュ or ウォ. Those kana are romanized only as their base kana plus a small
 * vowel (チェック: "tixekku", "chixekku"), so typing "chekku", "ye" or "tsa" cannot complete チェック, イェール
 * or ツァー.
 */
public class TestJapaneseCompletionExtendedKatakana extends BaseTokenStreamTestCase {

  /** {indexed word, what the user types}: rows that exist, and today's small-vowel spelling. */
  private static final String[][] CONTROLS = {
    {"シェア", "shea"}, {"ティー", "thi"}, {"ウィキ", "wiki"}, {"チェック", "chixekku"}
  };

  /** {indexed word, what the user types}: the Hepburn spelling, also an input-method keystroke. */
  private static final String[][] HEPBURN = {{"チェック", "chekku"}, {"イェール", "ye"}, {"ツァー", "tsa"}};

  /** {indexed word, what the user types}: the input-method keystrokes dhi, dhu and who. */
  private static final String[][] KEYSTROKES = {
    {"ディスク", "dhisuku"}, {"デュエット", "dhuetto"}, {"ウォッカ", "whokka"}
  };

  public void testRowsThatExist() throws IOException {
    assertAllComplete(CONTROLS);
  }

  public void testHepburnSpellings() throws IOException {
    assertAllComplete(HEPBURN);
  }

  public void testInputMethodKeystrokes() throws IOException {
    assertAllComplete(KEYSTROKES);
  }

  private static void assertAllComplete(String[][] cases) throws IOException {
    try (Analyzer index =
            new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.INDEX);
        Analyzer query =
            new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.QUERY)) {
      List<String> misses = new ArrayList<>();
      for (String[] c : cases) {
        if (completes(index, c[0], query, c[1]) == false) {
          misses.add("'" + c[1] + "' does not complete " + c[0] + " " + terms(index, c[0]));
        }
      }
      assertTrue(String.join("; ", misses), misses.isEmpty());
    }
  }

  /**
   * A completion prefix query matches a one-token suggestion when a query-side token is a prefix of
   * an index-side token.
   */
  private static boolean completes(Analyzer index, String word, Analyzer query, String typed)
      throws IOException {
    for (String q : terms(query, typed)) {
      for (String t : terms(index, word)) {
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
