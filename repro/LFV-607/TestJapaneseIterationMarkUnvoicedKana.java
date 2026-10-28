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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;

/**
 * The unvoiced iteration mark ゝ (ヽ in katakana) repeats the preceding kana without a dakuten. For
 * kana that have no voiced form (っ, な に ぬ ね の, ぱ ぴ ぷ ぺ and their katakana forms) the filter emits
 * the preceding code point instead, a different kana: なゝめ becomes などめ, not ななめ.
 */
public class TestJapaneseIterationMarkUnvoicedKana extends BaseTokenStreamTestCase {

  /** Already asserted by TestJapaneseIterationMarkCharFilter, or the voiced mark: these pass. */
  public void testControls() throws IOException {
    assertExpansions("おゝの", "おおの", "じゝ", "じし", "ぽゝ", "ぽぽ", "こゝろ", "こころ", "なゞ", "なな");
  }

  /** Words in older orthography: なゝめ (ななめ, "diagonal"), のゝしる (ののしる, "to revile"). */
  public void testWords() throws IOException {
    assertExpansions("なゝめ", "ななめ", "のゝしる", "ののしる", "ナヽメ", "ナナメ");
  }

  /** Every kana without a voiced form, followed by the unvoiced mark, is repeated unchanged. */
  public void testAllKanaWithoutVoicedForm() throws IOException {
    String hiragana = "っなにぬねのぱぴぷぺ";
    String katakana = "ッナニヌネノパピプペ";
    List<String> pairs = new ArrayList<>();
    for (int i = 0; i < hiragana.length(); i++) {
      char h = hiragana.charAt(i);
      char k = katakana.charAt(i);
      pairs.add("" + h + 'ゝ');
      pairs.add("" + h + h);
      pairs.add("" + k + 'ヽ');
      pairs.add("" + k + k);
    }
    assertExpansions(pairs.toArray(new String[0]));
  }

  /** A document written なゝめ is found by the expanded spelling ななめ. */
  public void testSearchByExpandedSpelling() throws IOException {
    try (Analyzer analyzer = keywordAnalyzer();
        Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new TextField("body", "なゝめ", Field.Store.NO));
      writer.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(writer)) {
        IndexSearcher searcher = newSearcher(reader);
        int expanded = searcher.count(new TermQuery(new Term("body", "ななめ")));
        int other = searcher.count(new TermQuery(new Term("body", "などめ")));
        assertEquals("hits for ななめ (hits for などめ: " + other + ")", 1, expanded);
      }
    }
  }

  /** Arguments are pairs {input, expected}; every wrong expansion is reported. */
  private static void assertExpansions(String... inputAndExpected) throws IOException {
    List<String> wrong = new ArrayList<>();
    for (int i = 0; i < inputAndExpected.length; i += 2) {
      String input = inputAndExpected[i];
      String expected = inputAndExpected[i + 1];
      String actual = filter(input);
      if (expected.equals(actual) == false) {
        wrong.add(input + " -> " + actual + " (expected " + expected + ")");
      }
    }
    assertTrue(wrong.size() + " wrong expansions: " + wrong, wrong.isEmpty());
  }

  private static String filter(String input) throws IOException {
    StringBuilder out = new StringBuilder();
    try (Reader reader = new JapaneseIterationMarkCharFilter(new StringReader(input))) {
      int ch;
      while ((ch = reader.read()) != -1) {
        out.append((char) ch);
      }
    }
    return out.toString();
  }

  private static Analyzer keywordAnalyzer() {
    return new Analyzer() {
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
  }
}
