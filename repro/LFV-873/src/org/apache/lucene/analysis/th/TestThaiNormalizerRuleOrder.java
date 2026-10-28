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
package org.apache.lucene.analysis.th;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * ThaiNormalizer checks for a decomposed Sara Am (Nikhahit + optional tone mark + Sara Aa) before it converts a
 * following Lakkhangyao to Sara Aa and before it removes duplicate or extra tone marks. When one of those rules has to
 * run first, the output still contains the decomposed Sara Am the class javadoc says it recomposes, and normalizing
 * the output a second time changes it.
 */
public class TestThaiNormalizerRuleOrder extends BaseTokenStreamTestCase {

  private void check(String input, String output) throws IOException {
    Tokenizer tokenizer = whitespaceMockTokenizer(input);
    TokenFilter tf = new ThaiNormalizationFilter(tokenizer);
    assertTokenStreamContents(tf, new String[] {output});
  }

  /** Lakkhangyao -> Sara Aa, then Nikhahit + Sara Aa -> Sara Am. */
  public void testLakkhangyaoInsideDecomposedSaraAm() throws IOException {
    check("\u0E17\u0E4D\u0E45", "ทำ"); // ท + NIKHAHIT + LAKKHANGYAO
    check("\u0E19\u0E4D\u0E49\u0E45", "น้ำ"); // น + NIKHAHIT + MAI THO + LAKKHANGYAO
  }

  /** Duplicate tone mark removed, then Nikhahit + tone + Sara Aa -> tone + Sara Am. */
  public void testDuplicatedToneInsideDecomposedSaraAm() throws IOException {
    check("\u0E19\u0E4D\u0E49\u0E49\u0E32", "น้ำ"); // น + NIKHAHIT + MAI THO + MAI THO + SARA AA
  }

  /** Two different tone marks: the last one is kept, then Nikhahit + tone + Sara Aa -> tone + Sara Am. */
  public void testTwoTonesInsideDecomposedSaraAm() throws IOException {
    check("\u0E19\u0E4D\u0E48\u0E49\u0E32", "น้ำ"); // น + NIKHAHIT + MAI EK + MAI THO + SARA AA
  }

  /** The normalizer is not idempotent on these inputs. */
  public void testIdempotent() {
    ThaiNormalizer n = new ThaiNormalizer();
    for (String s : new String[] {"\u0E17\u0E4D\u0E45", "\u0E19\u0E4D\u0E49\u0E49\u0E32", "\u0E19\u0E4D\u0E48\u0E49\u0E32"}) {
      char[] b = s.toCharArray();
      String once = new String(b, 0, n.normalize(b, b.length));
      char[] b2 = once.toCharArray();
      String twice = new String(b2, 0, n.normalize(b2, b2.length));
      assertEquals("normalize(normalize(x)) != normalize(x) for " + s, once, twice);
    }
  }

  /**
   * Default ThaiAnalyzer, index path: "drink water cold" with "น้ำ" typed as Nikhahit + two tone marks + Sara Aa.
   * The word is indexed as the decomposed form, so a query for "น้ำ" does not match it.
   */
  public void testThaiAnalyzerIndexesDecomposedForm() throws IOException {
    Analyzer a = new ThaiAnalyzer();
    assertAnalyzesTo(a, "ดื่ม น้ำ เย็น", new String[] {"ดื่ม", "น้ำ", "เย็น"});
    assertAnalyzesTo(a, "ดื่ม \u0E19\u0E4D\u0E48\u0E49\u0E32 เย็น", new String[] {"ดื่ม", "น้ำ", "เย็น"});
    a.close();
  }

  /**
   * Default ThaiAnalyzer: its char filter does not document "keep the last of two tone marks", so the token filter
   * gets the input unchanged and returns the decomposed form. Analyzer.normalize is what query parsers use for
   * prefix/wildcard/fuzzy terms.
   */
  public void testThaiAnalyzerNormalize() {
    Analyzer a = new ThaiAnalyzer(CharArraySet.EMPTY_SET);
    assertEquals("น้ำ", a.normalize("f", "\u0E19\u0E4D\u0E48\u0E49\u0E32").utf8ToString());
    a.close();
  }
}
