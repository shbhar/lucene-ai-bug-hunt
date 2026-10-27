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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;

/**
 * The unvoiced iteration marks ゝ (U+309D) and ヽ (U+30FD) repeat the previous kana without a
 * dakuten. JapaneseIterationMarkCharFilter decides "has a dakuten" by checking whether the kana maps
 * to itself in its voicing table, which is also true for kana that have no voiced form at all (っ,
 * な..の, ぱ ぴ ぷ ぺ and their katakana forms); for those it emits the code point minus one, a
 * different kana.
 */
public class TestIterationMarkUnvoicedKana extends BaseTokenStreamTestCase {

  private static String filter(String s) throws IOException {
    try (Reader r = new JapaneseIterationMarkCharFilter(new StringReader(s))) {
      StringBuilder sb = new StringBuilder();
      char[] buf = new char[16];
      int n;
      while ((n = r.read(buf, 0, buf.length)) != -1) {
        sb.append(buf, 0, n);
      }
      return sb.toString();
    }
  }

  /** Behaviour the upstream tests already assert: these pass. */
  public void testControls() throws IOException {
    assertEquals("じし", filter("じゝ")); // voiced source: the mark repeats it unvoiced
    assertEquals("ぽぽ", filter("ぽゝ")); // handakuten source: repeated unchanged
    assertEquals("ここ", filter("こゝ"));
    assertEquals("ココ", filter("コヽ"));
  }

  /** Real words in historical orthography. */
  public void testWords() throws IOException {
    Analyzer keyword =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new MockTokenizer(MockTokenizer.KEYWORD, false);
            return new TokenStreamComponents(tokenizer, tokenizer);
          }

          @Override
          protected Reader initReader(String fieldName, Reader reader) {
            return new JapaneseIterationMarkCharFilter(reader);
          }
        };
    // ななめ (diagonal) written なゝめ; got など..
    assertAnalyzesTo(keyword, "なゝめ", new String[] {"ななめ"});
    keyword.close();
  }

  /** Every kana that has neither a dakuten nor a voiced form, followed by the unvoiced mark. */
  public void testAllAffectedKana() throws IOException {
    String hiragana = "っなにぬねのぱぴぷぺ";
    String katakana = "ッナニヌネノパピプペ";
    List<String> wrong = new ArrayList<>();
    for (int i = 0; i < hiragana.length(); i++) {
      check(hiragana.charAt(i), 'ゝ', wrong);
      check(katakana.charAt(i), 'ヽ', wrong);
    }
    assertTrue(wrong.size() + " wrong expansions: " + wrong, wrong.isEmpty());
  }

  private static void check(char kana, char mark, List<String> wrong) throws IOException {
    String in = "" + kana + mark;
    String expected = "" + kana + kana;
    String got = filter(in);
    if (!expected.equals(got)) {
      wrong.add(in + " -> " + got + " (expected " + expected + ")");
    }
  }
}
