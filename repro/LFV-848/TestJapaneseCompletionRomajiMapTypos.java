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
 * Three rows of romaji_map.txt are typos. The key of "ッへ,hhe" has the hiragana へ (U+3078), not the
 * katakana ヘ (U+30D8), so katakana ッヘ never matches the row and is romanized "xtuhe";
 * "ッスォ,sswo,suxo" lacks the doubled consonant; "ッドゥ,ddwu,ddoxi" repeats the spelling of ッドィ.
 */
public class TestJapaneseCompletionRomajiMapTypos extends BaseTokenStreamTestCase {

  public void testSokuonHe() {
    // the sibling rows ッハ,hha and ッホ,hho
    assertEquals(List.of("hha"), romanize("ッハ"));
    assertEquals(List.of("hho"), romanize("ッホ"));
    assertEquals(List.of("hhe"), romanize("ッヘ"));
  }

  public void testCopiedSpellings() {
    // the sibling rows ッスェ,sswe,ssuxe and ッドォ,ddwo,ddoxo
    assertEquals(List.of("sswe", "ssuxe"), romanize("ッスェ"));
    assertEquals(List.of("ddwo", "ddoxo"), romanize("ッドォ"));
    assertEquals(
        List.of(List.of("sswo", "ssuxo"), List.of("ddwu", "ddoxu")),
        List.of(romanize("ッスォ"), romanize("ッドゥ")));
  }

  public void testTypedRomajiCompletesWordWithSokuonHe() throws IOException {
    try (Analyzer index =
            new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.INDEX);
        Analyzer query =
            new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.QUERY)) {
      // controls: the sibling word ヨッハン, and ヨッヘン typed fully in kana
      assertTrue(completes(index, "ヨッハン", query, "yohhan"));
      assertTrue(completes(index, "ヨッハン", query, "よっh"));
      assertTrue(completes(index, "ヨッヘン", query, "よっへ"));
      // raw romaji, and the input-method state before へ is composed
      List<String> misses = new ArrayList<>();
      for (String typed : new String[] {"yohhen", "よっh"}) {
        if (completes(index, "ヨッヘン", query, typed) == false) {
          misses.add("'" + typed + "' " + terms(query, typed));
        }
      }
      assertTrue(
          "ヨッヘン " + terms(index, "ヨッヘン") + " is not completed by " + misses, misses.isEmpty());
    }
  }

  private static List<String> romanize(String katakana) {
    List<String> out = new ArrayList<>();
    for (CharsRef r : KatakanaRomanizer.getInstance().romanize(new CharsRef(katakana))) {
      out.add(r.toString());
    }
    return out;
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
