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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ja.completion.KatakanaRomanizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.CharsRef;

/**
 * Independent check: romaji keystrokes for extended katakana that have no row in romaji_map.txt,
 * while their sibling kana do have rows.
 *
 * <p>Public API only: JapaneseCompletionAnalyzer (INDEX for the indexed text, QUERY for the typed
 * text), PrefixQuery over the indexed tokens, and KatakanaRomanizer.romanize.
 */
public class TestCompletionExtendedKatakana extends LuceneTestCase {

  // {indexed word, typed romaji}
  private static final String[][] GAPS = {
    {"チェック", "chekku"}, // チェ: Hepburn "che", IME che/tye/cye
    {"チェス", "chesu"},
    {"ディスコ", "dhisuko"}, // ディ: IME dhi
    {"デュエット", "dhuetto"}, // デュ: IME dhu
    {"ウォッカ", "whokka"}, // ウォ: IME who
    {"イェメン", "yemen"}, // イェ: Hepburn "ye", IME ye
    {"ツァー", "tsa"}, // ツァ: Hepburn "tsa", IME tsa
  };

  // sibling rows that exist in the table: these must work, otherwise the harness is wrong
  private static final String[][] CONTROLS = {
    {"シェア", "shea"}, // シェ,sye,she
    {"ジェット", "jetto"}, // ジェ,zye,je
    {"ティッシュ", "thisshu"}, // ティ,thi,texi
    {"ウィキ", "wiki"}, // ウィ,wi
    {"ウェブ", "webu"}, // ウェ,we
    {"チャット", "chatto"}, // チャ,tya,cha,cya
    {"チェック", "chixekku"}, // fallback チ + ェ(xe) + ック
    {"ディスコ", "dexisuko"}, // fallback デ + ィ(xi)
    // IME-composed query text (kana, or kana plus a pending letter): both sides use the fallback
    {"チェック", "ちぇっく"},
    {"チェック", "ちぇっk"},
    {"ウォッカ", "うぉっk"},
    {"イェメン", "いぇm"},
    {"ディスコ", "でぃs"},
  };

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

  /** Returns a description of each {word, typed} pair whose typed text does not find the word. */
  private List<String> misses(String[][] pairs) throws IOException {
    List<String> misses = new ArrayList<>();
    Analyzer indexAnalyzer = new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.INDEX);
    Analyzer queryAnalyzer = new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.QUERY);
    try (Directory dir = newDirectory()) {
      RandomIndexWriter w = new RandomIndexWriter(random(), dir, indexAnalyzer);
      for (String[] p : pairs) {
        Document doc = new Document();
        doc.add(new StringField("id", p[0], Field.Store.YES));
        doc.add(new TextField("name", p[0], Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader r = w.getReader()) {
        w.close();
        IndexSearcher s = newSearcher(r);
        for (String[] p : pairs) {
          List<String> indexTokens = tokens(indexAnalyzer, p[0]);
          List<String> queryTokens = tokens(queryAnalyzer, p[1]);
          // token-level oracle: some query token is a prefix of some indexed token
          boolean tokenMatch = false;
          for (String q : queryTokens) {
            for (String i : indexTokens) {
              tokenMatch |= i.startsWith(q);
            }
          }
          // search-level oracle: prefix query on the analyzed index finds the word
          BooleanQuery.Builder b = new BooleanQuery.Builder();
          for (String q : queryTokens) {
            b.add(new PrefixQuery(new Term("name", q)), BooleanClause.Occur.SHOULD);
          }
          TopDocs td = s.search(b.build(), 100);
          boolean searchMatch = false;
          for (ScoreDoc sd : td.scoreDocs) {
            searchMatch |= p[0].equals(s.storedFields().document(sd.doc).get("id"));
          }
          assertEquals(
              "token-level and search-level oracles disagree for " + p[0] + "/" + p[1],
              tokenMatch,
              searchMatch);
          if (searchMatch == false) {
            misses.add(
                "typed '"
                    + p[1]
                    + "' (query tokens "
                    + queryTokens
                    + ") does not find "
                    + p[0]
                    + " (index tokens "
                    + indexTokens
                    + ")");
          }
        }
      }
    } finally {
      indexAnalyzer.close();
      queryAnalyzer.close();
    }
    return misses;
  }

  public void testControlsFindTheirWord() throws IOException {
    List<String> m = misses(CONTROLS);
    assertTrue("controls must all match: " + m, m.isEmpty());
  }

  public void testExtendedKatakanaKeystrokesFindTheirWord() throws IOException {
    List<String> m = misses(GAPS);
    assertTrue(
        m.size() + " of " + GAPS.length + " typed romaji find nothing:\n  " + String.join("\n  ", m),
        m.isEmpty());
  }

  public void testRomanizerTableRows() {
    KatakanaRomanizer rz = KatakanaRomanizer.getInstance();
    // {kana, keystroke}; first block: siblings present in the table (controls)
    String[][] present = {
      {"シェ", "she"}, {"ジェ", "je"}, {"ティ", "thi"}, {"テュ", "thu"},
      {"ウィ", "wi"}, {"ウェ", "we"}, {"ッイェ", "yye"},
    };
    String[][] expected = {
      {"チェ", "che"}, {"ディ", "dhi"}, {"デュ", "dhu"}, {"ウォ", "who"},
      {"イェ", "ye"}, {"ツァ", "tsa"}, {"ツィ", "tsi"}, {"ツェ", "tse"}, {"ツォ", "tso"},
    };
    for (String[] p : present) {
      List<String> got = rz.romanize(new CharsRef(p[0])).stream().map(CharsRef::toString).toList();
      assertTrue("control " + p[0] + " -> " + got, got.contains(p[1]));
    }
    List<String> missing = new ArrayList<>();
    for (String[] p : expected) {
      List<String> got = rz.romanize(new CharsRef(p[0])).stream().map(CharsRef::toString).toList();
      if (got.contains(p[1]) == false) {
        missing.add(p[0] + " -> " + got + " (no '" + p[1] + "')");
      }
    }
    assertTrue("romanizations missing:\n  " + String.join("\n  ", missing), missing.isEmpty());
  }
}
