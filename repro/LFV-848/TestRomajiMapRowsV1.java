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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ja.completion.CharSequenceUtils;
import org.apache.lucene.analysis.ja.completion.KatakanaRomanizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.search.suggest.analyzing.AnalyzingSuggester;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CharsRef;

/**
 * Independent check of three rows of the Japanese completion romanization table: a sokuon + he row
 * whose key is written with a hiragana letter, and two sokuon rows whose second spelling lacks the
 * doubled consonant or names a different mora. Each test asserts the behaviour the table row and its
 * sibling rows describe. Uses only public API.
 */
public class TestRomajiMapRowsV1 extends BaseTokenStreamTestCase {

  private static List<String> romanize(String katakana) {
    List<String> out = new ArrayList<>();
    for (CharsRef r : KatakanaRomanizer.getInstance().romanize(new CharsRef(katakana))) {
      out.add(r.toString());
    }
    return out;
  }

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

  /** Controls: the sibling sokuon + h rows romanize with a doubled h. Must pass. */
  public void testControlSiblingRows() {
    assertTrue(romanize("ッハ").toString(), romanize("ッハ").contains("hha"));
    assertTrue(romanize("ッヒ").toString(), romanize("ッヒ").contains("hhi"));
    assertTrue(romanize("ッホ").toString(), romanize("ッホ").contains("hho"));
    assertTrue(romanize("ッスェ").toString(), romanize("ッスェ").contains("ssuxe"));
    assertTrue(romanize("ッドェ").toString(), romanize("ッドェ").contains("ddoxe"));
    assertTrue(romanize("スォ").toString(), romanize("スォ").contains("suxo"));
    assertTrue(romanize("ドゥ").toString(), romanize("ドゥ").contains("doxu"));
  }

  /**
   * Every key of the table must be reachable: romanize() only accepts katakana or a-z (it asserts
   * it), and JapaneseCompletionFilter converts hiragana readings to katakana before romanizing.
   */
  public void testEveryTableKeyIsInRomanizerInputDomain() throws IOException {
    List<String> bad = new ArrayList<>();
    try (InputStream is = KatakanaRomanizer.class.getResourceAsStream("romaji_map.txt")) {
      assertNotNull("romaji_map.txt resource not found", is);
      BufferedReader r = new BufferedReader(new InputStreamReader(is, UTF_8));
      String line;
      int no = 0;
      while ((line = r.readLine()) != null) {
        no++;
        if (line.startsWith("#")) continue;
        String[] cols = line.trim().split(",");
        if (cols.length < 2) continue;
        if (!CharSequenceUtils.isKatakanaOrHWAlphabets(cols[0])) {
          StringBuilder cps = new StringBuilder();
          cols[0].codePoints().forEach(cp -> cps.append(String.format(" U+%04X", cp)));
          bad.add("line " + no + " '" + line + "' key code points:" + cps);
        }
      }
    }
    assertTrue("table keys outside romanize()'s input domain: " + bad, bad.isEmpty());
  }

  /** ッヘ should romanize like its siblings ッハ/ッヒ/ッホ: the table row says 'hhe'. */
  public void testSokuonHe() {
    List<String> got = romanize("ッヘ");
    assertTrue("romanize(ッヘ)=" + got + " lacks hhe", got.contains("hhe"));
  }

  /** ッスォ: siblings ッスァ..ッスェ give ssuxa..ssuxe; 'suxo' is the spelling of スォ (no sokuon). */
  public void testSokuonSuxo() {
    List<String> got = romanize("ッスォ");
    assertTrue(
        "romanize(ッスォ)=" + got + " should contain ssuxo and not suxo",
        got.contains("ssuxo") && !got.contains("suxo"));
  }

  /** ッドゥ: siblings give ddoxa/ddoxe/ddoxo, base ドゥ gives doxu; 'ddoxi' is ッドィ's spelling. */
  public void testSokuonDdoxu() {
    List<String> got = romanize("ッドゥ");
    assertTrue(
        "romanize(ッドゥ)=" + got + " should contain ddoxu and not ddoxi",
        got.contains("ddoxu") && !got.contains("ddoxi"));
  }

  /** Index-side analyzer output for a word with ッヘ, compared with a word with ッハ. */
  public void testIndexAnalyzerOutput() throws IOException {
    try (Analyzer a = new JapaneseCompletionAnalyzer()) {
      List<String> control = tokens(a, "ヨッハン");
      List<String> got = tokens(a, "ヨッヘン");
      List<String> got2 = tokens(a, "えっへん");
      System.out.println("V1 index tokens: ヨッハン=" + control + " ヨッヘン=" + got + " えっへん=" + got2);
      assertTrue("control ヨッハン=" + control, control.contains("yohhan"));
      assertTrue("ヨッヘン index tokens " + got + " lack yohhen", got.contains("yohhen"));
    }
  }

  private static final class Entries implements InputIterator {
    private final Iterator<String> it;

    Entries(String... words) {
      this.it = List.of(words).iterator();
    }

    @Override
    public BytesRef next() {
      return it.hasNext() ? new BytesRef(it.next()) : null;
    }

    @Override
    public long weight() {
      return 1;
    }

    @Override
    public BytesRef payload() {
      return null;
    }

    @Override
    public boolean hasPayloads() {
      return false;
    }

    @Override
    public Set<BytesRef> contexts() {
      return null;
    }

    @Override
    public boolean hasContexts() {
      return false;
    }
  }

  private static List<String> lookup(AnalyzingSuggester s, String q) throws IOException {
    List<String> out = new ArrayList<>();
    for (LookupResult r : s.lookup(q, false, 10)) {
      out.add(r.key.toString());
    }
    return out;
  }

  /**
   * End to end with a suggester: index analyzer in INDEX mode, query analyzer in QUERY mode (the
   * filter's documented usage). Typing the romaji of ヨッヘン, or the IME mid-composition prefix
   * "よっh", should suggest it, as the same inputs do for ヨッハン.
   */
  public void testSuggesterEndToEnd() throws IOException {
    try (Directory dir = newDirectory();
        Analyzer index = new JapaneseCompletionAnalyzer();
        Analyzer query =
            new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.QUERY)) {
      AnalyzingSuggester s = new AnalyzingSuggester(dir, "v1", index, query);
      s.build(new Entries("ヨッハン", "ヨッヘン"));
      List<String> hhan = lookup(s, "yohhan");
      List<String> hhen = lookup(s, "yohhen");
      List<String> midHa = lookup(s, "よっh");
      List<String> kana = lookup(s, "よっへ");
      System.out.println(
          "V1 lookups: yohhan="
              + hhan
              + " yohhen="
              + hhen
              + " よっh="
              + midHa
              + " よっへ="
              + kana
              + " query tokens(よっh)="
              + tokens(query, "よっh"));
      assertTrue("control yohhan -> " + hhan, hhan.contains("ヨッハン"));
      List<String> problems = new ArrayList<>();
      if (!hhen.contains("ヨッヘン")) problems.add("yohhen -> " + hhen);
      if (!midHa.contains("ヨッヘン")) problems.add("よっh -> " + midHa);
      assertTrue("ヨッヘン not suggested: " + problems, problems.isEmpty());
    }
  }
}
