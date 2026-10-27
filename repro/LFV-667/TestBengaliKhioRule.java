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
package org.apache.lucene.analysis.bn;

import java.io.IOException;
import java.io.StringReader;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * BengaliNormalizer's "Khio (Ka + Hoshonto + Murdorno Sh)" rule tests the third character against
 * U+09BF BENGALI VOWEL SIGN I instead of U+09B7 BENGALI LETTER SSA (murdhanya sha), so the rule never
 * fires on the khio conjunct KA + HASANTA + SSA. Per the rule, a word-initial khio becomes KHA and a
 * medial khio becomes KA + KHA.
 */
public class TestBengaliKhioRule extends BaseTokenStreamTestCase {

  private static String hex(String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) b.append(String.format("U+%04X ", (int) s.charAt(i)));
    return b.toString().trim();
  }

  /** Runs the filter alone on a single keyword token and returns the term. */
  private static String normalizeWithFilter(String in) throws IOException {
    Tokenizer t = new KeywordTokenizer();
    t.setReader(new StringReader(in));
    try (TokenStream ts = new BengaliNormalizationFilter(t)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      assertTrue(ts.incrementToken());
      String s = term.toString();
      assertFalse(ts.incrementToken());
      ts.end();
      return s;
    }
  }

  /** Word-initial khio: KSHOMA (KA HASANTA SSA MA AA) must become KHA MA AA. */
  public void testWordInitialKhio() throws IOException {
    String kshoma = "ক্ষমা";
    String expected = "খমা";
    assertEquals(hex(expected), hex(normalizeWithFilter(kshoma)));
  }

  /** Medial khio: PORIKSHA (PA RA II KA HASANTA SSA AA) must become PA RA I KA KHA AA. */
  public void testMedialKhio() throws IOException {
    String poriksha = "পরীক্ষা";
    String expected = "পরিকখা";
    assertEquals(hex(expected), hex(normalizeWithFilter(poriksha)));
  }

  /** Query-side impact through BengaliAnalyzer: the khio spelling and the KHA spelling must agree. */
  public void testBengaliAnalyzerNormalize() throws IOException {
    try (BengaliAnalyzer a = new BengaliAnalyzer()) {
      String khio = a.normalize("f", "ক্ষমা").utf8ToString();
      String kha = a.normalize("f", "খমা").utf8ToString();
      assertEquals(hex(kha), hex(khio));
    }
  }
}
