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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Independent check: words listed in TeluguAnalyzer's default stop set should not be emitted by
 * the default TeluguAnalyzer. Uses only public API (TeluguAnalyzer, getDefaultStopSet,
 * Analyzer#normalize, StandardTokenizer).
 */
public class TestTeluguStopSetVerifyV1 extends BaseTokenStreamTestCase {

  private static List<String> tokens(Analyzer a, String text) throws IOException {
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

  private static int standardTokenCount(String text) throws IOException {
    int n = 0;
    try (StandardTokenizer tok = new StandardTokenizer()) {
      tok.setReader(new StringReader(text));
      tok.reset();
      while (tok.incrementToken()) {
        n++;
      }
      tok.end();
    }
    return n;
  }

  private static List<String> defaultStopWords() {
    List<String> words = new ArrayList<>();
    for (Object o : TeluguAnalyzer.getDefaultStopSet()) {
      words.add(new String((char[]) o));
    }
    Collections.sort(words);
    return words;
  }

  /** Control: a default stop word whose spelling the normalizers leave unchanged is removed. */
  public void testControlShortVowelStopwordIsRemoved() throws Exception {
    Analyzer a = new TeluguAnalyzer();
    assertTrue(TeluguAnalyzer.getDefaultStopSet().contains("గురించి"));
    assertAnalyzesTo(a, "గురించి", new String[0]);
    a.close();
  }

  /** Mechanism (expected to pass): the pre-stop chain rewrites కూడా to a form not in the set. */
  public void testMechanismNormalizedFormNotInDefaultSet() throws Exception {
    Analyzer a = new TeluguAnalyzer();
    CharArraySet stop = TeluguAnalyzer.getDefaultStopSet();
    assertTrue("raw form is in the default list", stop.contains("కూడా"));
    BytesRef normalized = a.normalize("f", "కూడా");
    String norm = normalized.utf8ToString();
    assertEquals("కుడా", norm); // long u vowel sign folded to short u
    assertFalse("normalized form is not in the default list", stop.contains(norm));
    a.close();
  }

  /** Control (expected to pass): a stop set holding the normalized form does remove the word. */
  public void testControlNormalizedCustomStopSetRemovesWord() throws Exception {
    CharArraySet custom = new CharArraySet(List.of("కుడా"), false);
    Analyzer a = new TeluguAnalyzer(custom);
    assertAnalyzesTo(a, "కూడా", new String[0]);
    a.close();
  }

  /** Bug: a single, very common default stop word is indexed by the default analyzer. */
  public void testDefaultStopwordKudaaIsRemoved() throws Exception {
    Analyzer a = new TeluguAnalyzer();
    assertEquals(1, standardTokenCount("కూడా"));
    assertEquals(
        "default stop word కూడా should produce no token", List.of(), tokens(a, "కూడా"));
    a.close();
  }

  /** Bug: sweep over every single-token entry of the default stop set. */
  public void testEveryDefaultStopwordIsRemoved() throws Exception {
    Analyzer a = new TeluguAnalyzer();
    List<String> survivors = new ArrayList<>();
    int checked = 0;
    for (String w : defaultStopWords()) {
      if (standardTokenCount(w) != 1) {
        continue; // multi-token entries cannot match a single token; not part of this claim
      }
      checked++;
      List<String> out = tokens(a, w);
      if (!out.isEmpty()) {
        survivors.add(
            w + " -> normalize=" + a.normalize("f", w).utf8ToString() + " emitted=" + out);
      }
    }
    System.out.println("checked single-token default stop words: " + checked);
    System.out.println("survivors (" + survivors.size() + "): " + survivors);
    assertEquals("default stop words emitted as tokens: " + survivors, 0, survivors.size());
    a.close();
  }
}
