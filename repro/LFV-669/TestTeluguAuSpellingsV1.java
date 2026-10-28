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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.in.IndicNormalizationFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * Independent check: TeluguNormalizationFilter composes the two-code-point spelling of Telugu
 * letter AU (U+0C12 letter O + U+0C4C vowel sign AU) into U+0C14, but the precomposed U+0C14 is
 * itself folded to U+0C13 (OO). The two spellings of AU therefore end up as different terms.
 *
 * <p>Word used: "aushadham" (medicine), precomposed U+0C14 U+0C37 U+0C27 U+0C02 versus the
 * decomposed U+0C12 U+0C4C U+0C37 U+0C27 U+0C02.
 */
public class TestTeluguAuSpellingsV1 extends BaseTokenStreamTestCase {

  private static final String AU_PRECOMPOSED = "ఔషధం";
  private static final String AU_DECOMPOSED = "ఒౌషధం";
  private static final String OO_FOLDED = "ఓషధం";

  /** Analyzer: keyword tokenizer + TeluguNormalizationFilter only (whole input is one term). */
  private static Analyzer filterOnly() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new KeywordTokenizer();
        return new TokenStreamComponents(t, new TeluguNormalizationFilter(t));
      }
    };
  }

  private static List<String> terms(Analyzer a, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  private static String hex(List<String> terms) {
    StringBuilder sb = new StringBuilder("[");
    for (String t : terms) {
      if (sb.length() > 1) sb.append(", ");
      t.codePoints().forEach(cp -> sb.append(String.format("U+%04X ", cp)));
      if (sb.charAt(sb.length() - 1) == ' ') sb.setLength(sb.length() - 1);
    }
    return sb.append("]").toString();
  }

  /** Control (upstream TestTeluguNormalizer#testVowels pins this rule): AU folds to OO. */
  public void testControlPrecomposedAuFoldsToOo() throws IOException {
    try (Analyzer a = filterOnly()) {
      assertEquals(hex(List.of(OO_FOLDED)), hex(terms(a, AU_PRECOMPOSED)));
    }
  }

  /** Control: the sibling composition (O + length mark U+0C55 -> OO) reaches the normal form. */
  public void testControlDecomposedOoComposes() throws IOException {
    try (Analyzer a = filterOnly()) {
      assertEquals(hex(List.of("ఓ")), hex(terms(a, "ఒౕ")));
    }
  }

  /** Claim: both spellings of AU must give the same term (the composition rule's purpose). */
  public void testDecomposedAuSameTermAsPrecomposed() throws IOException {
    try (Analyzer a = filterOnly()) {
      assertEquals(
          "decomposed AU must normalize like precomposed AU",
          hex(terms(a, AU_PRECOMPOSED)),
          hex(terms(a, AU_DECOMPOSED)));
    }
  }

  /** Claim via SPI, exactly the chain in TeluguNormalizationFilterFactory's javadoc. */
  public void testFactoryJavadocChainConflatesBothSpellings() throws IOException {
    try (Analyzer a =
        CustomAnalyzer.builder()
            .withTokenizer("standard")
            .addTokenFilter("teluguNormalization")
            .build()) {
      List<String> pre = terms(a, AU_PRECOMPOSED);
      List<String> dec = terms(a, AU_DECOMPOSED);
      assertEquals(1, pre.size());
      assertEquals(1, dec.size());
      assertEquals("factory chain: decomposed vs precomposed AU", hex(pre), hex(dec));
    }
  }

  /**
   * Supporting observation only (idempotence is not documented): the output of one pass still
   * contains a character (U+0C14) that the same normalizer maps away on a second pass.
   */
  public void testSupportingOutputIsNotAFixedPoint() throws IOException {
    TeluguNormalizer n = new TeluguNormalizer();
    char[] buf = AU_DECOMPOSED.toCharArray();
    int len1 = n.normalize(buf, buf.length);
    String once = new String(buf, 0, len1);
    char[] buf2 = once.toCharArray();
    int len2 = n.normalize(buf2, buf2.length);
    String twice = new String(buf2, 0, len2);
    assertEquals("normalize(normalize(x)) vs normalize(x)", hex(List.of(twice)), hex(List.of(once)));
  }

  /** Scope control: the stock TeluguAnalyzer composes first (IndicNormalizationFilter). */
  public void testControlTeluguAnalyzerConflatesBothSpellings() throws IOException {
    try (Analyzer a = new TeluguAnalyzer()) {
      assertEquals(hex(terms(a, AU_PRECOMPOSED)), hex(terms(a, AU_DECOMPOSED)));
    }
  }

  /** Scope control: Indic normalization before Telugu normalization conflates both spellings. */
  public void testControlIndicThenTeluguConflatesBothSpellings() throws IOException {
    try (Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new KeywordTokenizer();
            return new TokenStreamComponents(
                t, new TeluguNormalizationFilter(new IndicNormalizationFilter(t)));
          }
        }) {
      assertEquals(hex(terms(a, AU_PRECOMPOSED)), hex(terms(a, AU_DECOMPOSED)));
      assertEquals(hex(List.of(OO_FOLDED)), hex(terms(a, AU_DECOMPOSED)));
    }
  }
}
