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
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ja.dict.ToStringUtil;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * In romaji the small tsu ッ doubles the next consonant: キット is "kitto" and バッグ (bag) is "baggu".
 * The romaji mode of JapaneseReadingFormFilter does this only before the k, s, t and p rows and
 * drops the ッ before every other row, so バッグ (bag) gets the same term as バグ (bug).
 */
public class TestRomajiSokuon extends BaseTokenStreamTestCase {

  /** Before the k, s, t and p rows the ッ is doubled (passes). */
  public void testSokuonBeforeKSTP() {
    assertRomaji(
        new String[][] {{"キット", "kitto"}, {"キップ", "kippu"}, {"ザッシ", "zasshi"}, {"マッチ", "matchi"}});
  }

  /** Before g, z/j, d, b and h/f the ッ is dropped (fails). */
  public void testSokuonBeforeOtherRows() {
    assertRomaji(
        new String[][] {
          {"バッグ", "baggu"}, // bag
          {"ベッド", "beddo"}, // bed
          {"ビッグ", "biggu"}, // big
          {"キッズ", "kizzu"}, // kids
          {"ブリッジ", "burijji"}, // bridge
          {"スタッフ", "sutaffu"}, // staff
          {"バッハ", "bahha"}, // Bach
        });
  }

  /** End to end: bag and bug must not get the same romaji term (fails). */
  public void testBagAndBugGetDifferentTerms() throws IOException {
    try (Analyzer romaji =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer =
                new JapaneseTokenizer(
                    newAttributeFactory(), null, true, JapaneseTokenizer.Mode.SEARCH);
            return new TokenStreamComponents(
                tokenizer, new JapaneseReadingFormFilter(tokenizer, true));
          }
        }) {
      assertAnalyzesTo(romaji, "バグ", new String[] {"bagu"}); // bug
      assertAnalyzesTo(romaji, "baggu", new String[] {"baggu"}); // a typed romaji query
      assertAnalyzesTo(romaji, "バッグ", new String[] {"baggu"}); // bag
    }
  }

  private static void assertRomaji(String[][] cases) {
    List<String> wrong = new ArrayList<>();
    for (String[] c : cases) {
      String got = ToStringUtil.getRomanization(c[0]);
      if (got.equals(c[1]) == false) {
        wrong.add(c[0] + " -> " + got + " (expected " + c[1] + ")");
      }
    }
    assertTrue(wrong.size() + " of " + cases.length + " wrong: " + wrong, wrong.isEmpty());
  }
}
