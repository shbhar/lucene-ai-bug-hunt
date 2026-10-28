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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.ja.dict.ToStringUtil;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * ToStringUtil.getRomanization is documented as "Romanize katakana with modified hepburn". In
 * Hepburn the sokuon (small tsu) is written by doubling the first consonant of the next syllable.
 * Public static API only.
 */
public class TestSokuonDirect extends LuceneTestCase {

  /** Rows the implementation already doubles: these must pass (control). */
  public void testControlRowsDoubled() {
    assertEquals("kakko", ToStringUtil.getRomanization("カッコ"));
    assertEquals("kitte", ToStringUtil.getRomanization("キッテ"));
    assertEquals("kissa", ToStringUtil.getRomanization("キッサ"));
    assertEquals("kippu", ToStringUtil.getRomanization("キップ"));
    assertEquals("matcha", ToStringUtil.getRomanization("マッチャ"));
  }

  /** The geminate before a voiced / h / f consonant must also be marked by doubling. */
  public void testSokuonBeforeOtherRows() {
    String[][] cases = {
      {"ベッド", "beddo"}, // bed
      {"ドッグ", "doggu"}, // dog
      {"ビッグ", "biggu"}, // big
      {"キッズ", "kizzu"}, // kids
      {"ヘッド", "heddo"}, // head
      {"ブリッジ", "burijji"}, // bridge
      {"ロッバ", "robba"}, // synthetic b-row case
      {"バッハ", "bahha"}, // Bach
      {"スタッフ", "sutaffu"}, // staff
    };
    List<String> wrong = new ArrayList<>();
    for (String[] c : cases) {
      String got = ToStringUtil.getRomanization(c[0]);
      if (!c[1].equals(got)) {
        wrong.add(c[0] + " -> " + got + " (expected " + c[1] + ")");
      }
    }
    assertTrue(wrong.size() + " of " + cases.length + " wrong: " + wrong, wrong.isEmpty());
  }

  /** Two different words must not collapse to the same romanization because the sokuon vanished. */
  public void testDistinctWordsCollide() {
    // bag vs bug
    assertFalse(
        "bag/bug both -> " + ToStringUtil.getRomanization("バッグ"),
        ToStringUtil.getRomanization("バッグ").equals(ToStringUtil.getRomanization("バグ")));
  }
}
