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
 * Modified Hepburn writes the sokuon ッ by doubling the next consonant (ch becomes tch).
 * ToStringUtil.getRomanization, which JapaneseReadingFormFilter uses for romaji output, does this
 * only before the k, s, t and p rows and silently drops ッ before every other consonant, so for
 * example バッグ (bag) and バグ (bug) get the same romaji term.
 */
public class TestRomajiSokuonBeforeVoicedConsonant extends BaseTokenStreamTestCase {

  /** Behaviour the class already has (and upstream tests rely on): these pass. */
  public void testControls() {
    assertEquals("kitto", ToStringUtil.getRomanization("キット"));
    assertEquals("kippu", ToStringUtil.getRomanization("キップ"));
    assertEquals("matchi", ToStringUtil.getRomanization("マッチ"));
    assertEquals("bagu", ToStringUtil.getRomanization("バグ"));
  }

  /** Common loanwords with ッ before g, d, z, j, b, f, h. */
  public void testSokuonBeforeOtherConsonants() {
    String[][] cases = {
      {"バッグ", "baggu"}, // bag
      {"ベッド", "beddo"}, // bed
      {"グッズ", "guzzu"}, // goods
      {"ブリッジ", "burijji"}, // bridge
      {"ホッブズ", "hobbuzu"}, // Hobbes
      {"スタッフ", "sutaffu"}, // staff
      {"バッハ", "bahha"}, // Bach
    };
    List<String> wrong = new ArrayList<>();
    for (String[] c : cases) {
      String got = ToStringUtil.getRomanization(c[0]);
      if (!c[1].equals(got)) {
        wrong.add(c[0] + " -> " + got + " (expected " + c[1] + ")");
      }
    }
    assertTrue(wrong.size() + " wrong romanizations: " + wrong, wrong.isEmpty());
  }

  /** End to end: the romaji reading form of バッグ (bag) equals that of バグ (bug). */
  public void testReadingFormFilterCollision() throws IOException {
    Analyzer romaji =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer =
                new JapaneseTokenizer(newAttributeFactory(), null, true, JapaneseTokenizer.Mode.SEARCH);
            return new TokenStreamComponents(tokenizer, new JapaneseReadingFormFilter(tokenizer, true));
          }
        };
    assertAnalyzesTo(romaji, "バグ", new String[] {"bagu"}); // control
    assertAnalyzesTo(romaji, "バッグ", new String[] {"baggu"});
    romaji.close();
  }
}
