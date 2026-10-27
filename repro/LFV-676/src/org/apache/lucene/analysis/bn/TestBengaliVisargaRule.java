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
 * BengaliNormalizer replaces a non-final VISARGA by a copy of the next character before that next
 * character has been normalized, and never revisits the copy. The normalizer folds every sh
 * (U+09B6 SHA, U+09B7 SSA) to U+09B8 SA, but after a visarga the copy keeps the original sh, so the
 * output still contains SHA/SSA and spellings that differ only in sh normalize differently.
 */
public class TestBengaliVisargaRule extends BaseTokenStreamTestCase {

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

  // NIHSHABDA "silent": NA I VISARGA SHA BA HASANTA DA
  static final String WITH_SHA = "নিঃশব্দ";
  // the same word with SSA, and with SA, in place of SHA
  static final String WITH_SSA = "নিঃষব্দ";
  static final String WITH_SA = "নিঃসব্দ";

  /** "All sh" become SA, so the normalized word must not contain SHA (U+09B6) any more. */
  public void testNoShaSurvives() throws IOException {
    String out = normalizeWithFilter(WITH_SHA);
    assertTrue("output still contains U+09B6: " + hex(out), out.indexOf('শ') < 0);
  }

  /** Spellings that differ only in SHA / SSA / SA must normalize to the same term. */
  public void testShaSsaSaAgree() throws IOException {
    String sa = normalizeWithFilter(WITH_SA);
    assertEquals(hex(sa), hex(normalizeWithFilter(WITH_SHA)));
    assertEquals(hex(sa), hex(normalizeWithFilter(WITH_SSA)));
  }

  /** Query-side impact through BengaliAnalyzer.normalize. */
  public void testBengaliAnalyzerNormalize() throws IOException {
    try (BengaliAnalyzer a = new BengaliAnalyzer()) {
      String sa = a.normalize("f", WITH_SA).utf8ToString();
      assertEquals(hex(sa), hex(a.normalize("f", WITH_SHA).utf8ToString()));
    }
  }
}
