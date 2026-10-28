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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * ThaiNormalizer documents that it recomposes a decomposed Sara Am (Nikhahit U+0E4D + optional
 * tone mark + Sara Aa U+0E32) into Sara Am (U+0E33). When the decomposed Sara Am only appears after
 * another documented rule has run (Lakkhangyao to Sara Aa, deduplication of a repeated tone mark,
 * keep-last of two different tone marks), the output still contains the decomposed form.
 */
public class TestThaiNormalizerSaraAmCombined extends BaseTokenStreamTestCase {

  // Code points used below.
  private static final char THO = 'ท'; // ท
  private static final char NO = 'น'; // น
  private static final char NIKHAHIT = 'ํ';
  private static final char SARA_AA = 'า';
  private static final char SARA_AM = 'ำ';
  private static final char LAKKHANGYAO = 'ๅ';
  private static final char MAI_EK = '่';
  private static final char MAI_THO = '้';

  private static String s(char... cs) {
    return new String(cs);
  }

  private static String normalize(String in) {
    char[] buf = in.toCharArray();
    int len = new ThaiNormalizer().normalize(buf, buf.length);
    return new String(buf, 0, len);
  }

  private static String hex(String x) {
    StringBuilder sb = new StringBuilder();
    for (char c : x.toCharArray()) {
      sb.append(String.format("U+%04X ", (int) c));
    }
    return sb.toString().trim();
  }

  /** True if the text still contains Nikhahit + optional tone mark + Sara Aa. */
  private static boolean hasDecomposedSaraAm(String x) {
    for (int i = 0; i < x.length(); i++) {
      if (x.charAt(i) != NIKHAHIT) continue;
      if (i + 1 < x.length() && x.charAt(i + 1) == SARA_AA) return true;
      if (i + 2 < x.length()
          && x.charAt(i + 1) >= '่'
          && x.charAt(i + 1) <= '๋'
          && x.charAt(i + 2) == SARA_AA) return true;
    }
    return false;
  }

  private void checkNormalize(String in, String expected) {
    String out = normalize(in);
    assertFalse(
        "output of normalize(" + hex(in) + ") = " + hex(out) + " still has a decomposed Sara Am",
        hasDecomposedSaraAm(out));
    assertEquals("normalize(" + hex(in) + ") = " + hex(out), hex(expected), hex(out));
  }

  // Controls: each documented rule on its own works.
  public void testControlsSingleAnomaly() {
    checkNormalize(s(THO, NIKHAHIT, SARA_AA), s(THO, SARA_AM)); // ทํา -> ทำ
    checkNormalize(s(NO, NIKHAHIT, MAI_THO, SARA_AA), s(NO, MAI_THO, SARA_AM)); // นํ้า -> น้ำ
    checkNormalize(s(THO, LAKKHANGYAO), s(THO, SARA_AA)); // ทๅ -> ทา
    checkNormalize(s(NO, MAI_THO, MAI_THO), s(NO, MAI_THO)); // repeated tone
    checkNormalize(s(NO, MAI_EK, MAI_THO), s(NO, MAI_THO)); // keep last tone
  }

  // Lakkhangyao in place of Sara Aa inside a decomposed Sara Am: ท ํ ๅ -> ทำ
  public void testLakkhangyaoAfterNikhahit() {
    checkNormalize(s(THO, NIKHAHIT, LAKKHANGYAO), s(THO, SARA_AM));
  }

  // Repeated tone inside a decomposed Sara Am: น ํ ้ ้ า -> น้ำ
  public void testRepeatedToneAfterNikhahit() {
    checkNormalize(s(NO, NIKHAHIT, MAI_THO, MAI_THO, SARA_AA), s(NO, MAI_THO, SARA_AM));
  }

  // Two different tones inside a decomposed Sara Am (keep the last): น ํ ่ ้ า -> น้ำ
  public void testTwoTonesAfterNikhahit() {
    checkNormalize(s(NO, NIKHAHIT, MAI_EK, MAI_THO, SARA_AA), s(NO, MAI_THO, SARA_AM));
  }

  // Tone + Lakkhangyao inside a decomposed Sara Am: น ํ ้ ๅ -> น้ำ
  public void testToneAndLakkhangyaoAfterNikhahit() {
    checkNormalize(s(NO, NIKHAHIT, MAI_THO, LAKKHANGYAO), s(NO, MAI_THO, SARA_AM));
  }

  // A second application changes the output (not a fixed point).
  public void testSecondPassChangesOutput() {
    String[] inputs = {
      s(THO, NIKHAHIT, LAKKHANGYAO),
      s(NO, NIKHAHIT, MAI_THO, MAI_THO, SARA_AA),
      s(NO, NIKHAHIT, MAI_EK, MAI_THO, SARA_AA),
    };
    for (String in : inputs) {
      String once = normalize(in);
      String twice = normalize(once);
      assertEquals("normalize is not stable on " + hex(in), hex(once), hex(twice));
    }
  }

  private static List<String> terms(Analyzer a, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) out.add(t.toString());
      ts.end();
    }
    return out;
  }

  // Default ThaiAnalyzer: the typo form must index like the clean word "water" (น้ำ).
  public void testThaiAnalyzerIndexesTypoLikeCleanWord() throws IOException {
    try (Analyzer a = new ThaiAnalyzer()) {
      String water = s(NO, MAI_THO, SARA_AM);
      String typo = s(NO, NIKHAHIT, MAI_EK, MAI_THO, SARA_AA);
      List<String> clean = terms(a, "ดื่ม " + water + " เย็น");
      List<String> typoTerms =
          terms(a, "ดื่ม " + typo + " เย็น");
      assertTrue("clean sentence must contain the term for water: " + clean, clean.contains(water));
      List<String> hc = new ArrayList<>(), ht = new ArrayList<>();
      for (String x : clean) hc.add(hex(x));
      for (String x : typoTerms) ht.add(hex(x));
      assertEquals("ThaiAnalyzer terms, clean vs typo", hc, ht);
    }
  }

  // Analyzer.normalize (used for multi-term queries) on the same typo form.
  public void testThaiAnalyzerNormalizeTypo() {
    try (Analyzer a = new ThaiAnalyzer()) {
      BytesRef got = a.normalize("f", s(NO, NIKHAHIT, MAI_EK, MAI_THO, SARA_AA));
      assertEquals(hex(s(NO, MAI_THO, SARA_AM)), hex(got.utf8ToString()));
    }
  }

  // Token filter alone (no char filter), after a whitespace tokenizer.
  public void testFilterAloneLakkhangyaoForm() throws IOException {
    Tokenizer tok = whitespaceMockTokenizer(s(THO, NIKHAHIT, LAKKHANGYAO));
    assertTokenStreamContents(new ThaiNormalizationFilter(tok), new String[] {s(THO, SARA_AM)});
  }
}
