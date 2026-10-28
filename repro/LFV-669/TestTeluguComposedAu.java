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
package org.apache.lucene.analysis.te;

import java.io.IOException;
import java.io.StringReader;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * TeluguNormalizer composes O (U+0C12) + AU length sign (U+0C4C) into AU (U+0C14), and separately maps
 * AU (U+0C14) to OO (U+0C13). The composed AU is not normalized again, so the decomposed spelling of AU
 * normalizes to AU while the precomposed AU normalizes to OO.
 */
public class TestTeluguComposedAu extends BaseTokenStreamTestCase {

  private static String hex(String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) b.append(String.format("U+%04X ", (int) s.charAt(i)));
    return b.toString().trim();
  }

  /** Runs the filter alone on a single keyword token and returns the term. */
  private static String normalizeWithFilter(String in) throws IOException {
    Tokenizer t = new KeywordTokenizer();
    t.setReader(new StringReader(in));
    try (TokenStream ts = new TeluguNormalizationFilter(t)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      assertTrue(ts.incrementToken());
      String s = term.toString();
      assertFalse(ts.incrementToken());
      ts.end();
      return s;
    }
  }

  public void testPrecomposedAu() throws IOException {
    assertEquals(hex("ఓ"), hex(normalizeWithFilter("ఔ"))); // passes (upstream test too)
  }

  public void testDecomposedAuNormalizesLikePrecomposed() throws IOException {
    assertEquals(hex(normalizeWithFilter("ఔ")), hex(normalizeWithFilter("ఒౌ")));
  }

  /** The output must not contain AU (U+0C14), which the same normalizer maps to OO. */
  public void testNoAuSurvives() throws IOException {
    String out = normalizeWithFilter("ఒౌక"); // AU KA, AU decomposed
    assertTrue("output still contains U+0C14: " + hex(out), out.indexOf('ఔ') < 0);
  }

  /**
   * The factory's documented example chain (standard tokenizer + teluguNormalization only): the two
   * spellings of AU index as different terms.
   */
  public void testFactoryExampleChain() throws IOException {
    try (CustomAnalyzer a =
        CustomAnalyzer.builder()
            .withTokenizer("standard")
            .addTokenFilter(TeluguNormalizationFilterFactory.NAME)
            .build()) {
      assertEquals(
          hex(a.normalize("f", "ఔ").utf8ToString()),
          hex(a.normalize("f", "ఒౌ").utf8ToString()));
      assertAnalyzesTo(a, "ఒౌ", new String[] {"ఓ"});
    }
  }
}
