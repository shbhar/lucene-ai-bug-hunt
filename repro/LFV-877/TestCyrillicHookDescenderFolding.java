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
package org.apache.lucene.analysis.icu;

import com.ibm.icu.lang.UCharacter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;

/**
 * Independent verifier repro. ICUFoldingFilter's javadoc lists "Diacritic removal (including
 * stroke, hook, descender)". Older Cyrillic letters with a hook/descender/stroke fold to their
 * base letter; this checks whether the newer letters of the same kind do as well.
 */
public class TestCyrillicHookDescenderFolding extends BaseTokenStreamTestCase {

  private Analyzer keywordFolding() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new MockTokenizer(MockTokenizer.KEYWORD, false);
        return new TokenStreamComponents(t, new ICUFoldingFilter(t));
      }
    };
  }

  private Analyzer whitespaceFolding() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new MockTokenizer(MockTokenizer.WHITESPACE, false);
        return new TokenStreamComponents(t, new ICUFoldingFilter(t));
      }
    };
  }

  private static String fold(Analyzer a, String s) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (TokenStream ts = a.tokenStream("f", s)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      int n = 0;
      while (ts.incrementToken()) {
        if (n++ > 0) sb.append(' ');
        sb.append(term);
      }
      ts.end();
    }
    return sb.toString();
  }

  private static String cp(int c) {
    return new String(Character.toChars(c));
  }

  private static String desc(int c) {
    return String.format(Locale.ROOT, "U+%04X %s", c, UCharacter.getName(c));
  }

  // {letter, expected base letter (small)}: letters encoded in Unicode 4.1 .. 7.0
  private static final int[][] NEWER = {
    {0x04F6, 0x0433}, {0x04F7, 0x0433}, // GHE WITH DESCENDER
    {0x04FA, 0x0433}, {0x04FB, 0x0433}, // GHE WITH STROKE AND HOOK
    {0x04FC, 0x0445}, {0x04FD, 0x0445}, // HA WITH HOOK
    {0x04FE, 0x0445}, {0x04FF, 0x0445}, // HA WITH STROKE
    {0x0512, 0x043B}, {0x0513, 0x043B}, // EL WITH HOOK
    {0x0520, 0x043B}, {0x0521, 0x043B}, // EL WITH MIDDLE HOOK
    {0x0522, 0x043D}, {0x0523, 0x043D}, // EN WITH MIDDLE HOOK
    {0x0524, 0x043F}, {0x0525, 0x043F}, // PE WITH DESCENDER
    {0x0526, 0x04BB}, {0x0527, 0x04BB}, // SHHA WITH DESCENDER
    {0x0528, 0x043D}, {0x0529, 0x043D}, // EN WITH LEFT HOOK
    {0x052E, 0x043B}, {0x052F, 0x043B}, // EL WITH DESCENDER
    {0xA68A, 0x0442}, {0xA68B, 0x0442}, // TE WITH MIDDLE HOOK
  };

  // controls: older letters of the same kind (Unicode 1.1 .. 3.2)
  private static final int[][] OLDER = {
    {0x04A6, 0x043F}, {0x04A7, 0x043F}, // PE WITH MIDDLE HOOK
    {0x0492, 0x0433}, {0x0493, 0x0433}, // GHE WITH STROKE
    {0x0494, 0x0433}, {0x0495, 0x0433}, // GHE WITH MIDDLE HOOK
    {0x04B2, 0x0445}, {0x04B3, 0x0445}, // HA WITH DESCENDER
    {0x04C5, 0x043B}, {0x04C6, 0x043B}, // EL WITH TAIL
    {0x04A2, 0x043D}, {0x04A3, 0x043D}, // EN WITH DESCENDER
    {0x04C7, 0x043D}, {0x04C8, 0x043D}, // EN WITH HOOK
    {0x04AC, 0x0442}, {0x04AD, 0x0442}, // TE WITH DESCENDER
  };

  private List<String> mismatches(int[][] pairs) throws IOException {
    List<String> bad = new ArrayList<>();
    try (Analyzer a = keywordFolding()) {
      for (int[] p : pairs) {
        String got = fold(a, cp(p[0]));
        String want = fold(a, cp(p[1]));
        String viaNormalizer = ICUFoldingFilter.NORMALIZER.normalize(cp(p[0]));
        assertEquals("filter and NORMALIZER disagree", viaNormalizer, got);
        if (!got.equals(want)) {
          bad.add(
              String.format(
                  Locale.ROOT,
                  "%s (age %s) -> %s, base %s -> %s",
                  desc(p[0]),
                  UCharacter.getAge(p[0]),
                  got,
                  desc(p[1]),
                  want));
        }
      }
    }
    return bad;
  }

  public void testOlderLettersFoldToBase() throws IOException {
    List<String> bad = mismatches(OLDER);
    assertTrue("controls should fold: " + bad, bad.isEmpty());
  }

  public void testNewerLettersFoldToBase() throws IOException {
    List<String> bad = mismatches(NEWER);
    for (String s : bad) System.out.println("V1-UNFOLDED " + s);
    assertTrue(bad.size() + " of " + NEWER.length + " newer letters not folded: " + bad, bad.isEmpty());
  }

  /** User-visible effect: Abkhaz self-name in older (U+04A7) vs modern (U+0525) orthography. */
  public void testAbkhazSpellingsMatch() throws IOException {
    String older = "\u0430\u04A7\u0441\u0443\u0430"; // a-PE WITH MIDDLE HOOK-s-u-a
    String modern = "\u0430\u0525\u0441\u0443\u0430"; // a-PE WITH DESCENDER-s-u-a
    try (Analyzer a = whitespaceFolding();
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document d = new Document();
        d.add(new TextField("f", older, Field.Store.NO));
        w.addDocument(d);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        String olderTerm = fold(a, older);
        String modernTerm = fold(a, modern);
        assertEquals(1, s.count(new TermQuery(new Term("f", olderTerm))));
        assertEquals(
            "modern spelling term '" + modernTerm + "' vs indexed '" + olderTerm + "'",
            1,
            s.count(new TermQuery(new Term("f", modernTerm))));
      }
    }
  }

  /**
   * Diagnostic (always passes): every Cyrillic letter named "... LETTER X WITH <hook|descender|
   * stroke|tail|tick|upturn|bar ...>" whose base "CYRILLIC SMALL LETTER X" exists; prints
   * folded==base counts by Unicode age.
   */
  public void testDiagnosticAgeSplit() throws IOException {
    Pattern name = Pattern.compile("CYRILLIC (SMALL|CAPITAL) LETTER (.+?) WITH (.+)");
    Pattern kind = Pattern.compile(".*\\b(HOOK|DESCENDER|STROKE|TAIL|TICK|UPTURN|BAR)\\b.*");
    Map<String, int[]> byAge = new TreeMap<>();
    List<String> unfolded = new ArrayList<>();
    try (Analyzer a = keywordFolding()) {
      for (int c = 0; c <= 0x10FFFF; c++) {
        if (c >= 0xD800 && c <= 0xDFFF) continue;
        if (UCharacter.getIntPropertyValue(c, com.ibm.icu.lang.UProperty.SCRIPT)
            != com.ibm.icu.lang.UScript.CYRILLIC) continue;
        String n = UCharacter.getName(c);
        if (n == null) continue;
        Matcher m = name.matcher(n);
        if (!m.matches() || !kind.matcher(m.group(3)).matches()) continue;
        int base = UCharacter.getCharFromName("CYRILLIC SMALL LETTER " + m.group(2));
        if (base < 0) continue;
        boolean ok = fold(a, cp(c)).equals(fold(a, cp(base)));
        String age = UCharacter.getAge(c).toString();
        int[] cnt = byAge.computeIfAbsent(age, k -> new int[2]);
        cnt[ok ? 0 : 1]++;
        if (!ok) unfolded.add(String.format(Locale.ROOT, "U+%04X(%s)", c, age));
      }
    }
    for (Map.Entry<String, int[]> e : byAge.entrySet()) {
      System.out.println(
          "V1-AGE " + e.getKey() + " folded=" + e.getValue()[0] + " unfolded=" + e.getValue()[1]);
    }
    System.out.println("V1-UNFOLDED-LIST " + unfolded);
  }
}
