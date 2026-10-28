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
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.suggest.document.CompletionAnalyzer;
import org.apache.lucene.search.suggest.document.PrefixCompletionQuery;
import org.apache.lucene.search.suggest.document.SuggestField;
import org.apache.lucene.search.suggest.document.SuggestIndexSearcher;
import org.apache.lucene.search.suggest.document.TopSuggestDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.CharsRef;

/**
 * Japanese completion, QUERY mode, mid-IME composition with a small tsu (sokuon) followed by a
 * pending romaji letter.
 *
 * <p>An input method shows a doubled consonant as small tsu + the pending letter: typing "sakk"
 * shows "さっｋ", which the existing test {@code TestJapaneseCompletionFilter#testCompletionQuery}
 * romanizes to "sakk". The romaji table also doubles c, j, f and v in its full rows (ッチャ "ccha",
 * ッジ "jji", ッフ "ffu", ッヴァ "vva"), so typing "maccha", "burijji", "sutaffu" shows "まっｃ",
 * "ぶりっｊ", "すたっｆ". This test checks that such a state is still romanized to a prefix of the
 * index-side romanization, as it is for k.
 *
 * <p>Only public API is used.
 */
public class TestSmallTsuPendingConsonant extends LuceneTestCase {

  private static List<String> romanize(String katakanaOrAlpha) {
    List<String> out = new ArrayList<>();
    for (CharsRef ref : KatakanaRomanizer.getInstance().romanize(new CharsRef(katakanaOrAlpha))) {
      out.add(ref.toString());
    }
    return out;
  }

  private static boolean anyPrefix(List<String> prefixes, List<String> full) {
    for (String p : prefixes) {
      for (String f : full) {
        if (f.startsWith(p)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Table level. Each row: pending letter, a full katakana unit, a spelling of that unit that the
   * table itself lists (it doubles the letter), the mid-composition form (small tsu + pending
   * letter, after a leading mora).
   */
  public void testRomanizerSmallTsuPlusPendingLetter() {
    String[][] cases = {
      // controls: letters that have a partial-input row (ッk .. ッw)
      {"k", "サッカ", "sakka", "サッk"},
      {"t", "マッテ", "matte", "マッt"},
      {"s", "マッサ", "massa", "マッs"},
      {"h", "マッホ", "mahho", "マッh"},
      // letters the full rows double but which have no partial-input row
      {"c", "マッチャ", "maccha", "マッc"},
      {"j", "マッジ", "majji", "マッj"},
      {"f", "マッフ", "maffu", "マッf"},
      {"v", "マッヴァ", "mavva", "マッv"},
    };
    StringBuilder failures = new StringBuilder();
    for (String[] c : cases) {
      List<String> full = romanize(c[1]);
      // precondition: the table lists the doubled spelling for the full unit
      assertTrue(c[1] + " -> " + full + " should contain " + c[2], full.contains(c[2]));
      List<String> mid = romanize(c[3]);
      boolean ok = anyPrefix(mid, full);
      System.out.println(
          "letter " + c[0] + ": " + c[3] + " -> " + mid + " ; full " + c[1] + " -> " + full
              + (ok ? "  OK" : "  NOT A PREFIX"));
      if (!ok) {
        failures
            .append("\n  ")
            .append(c[3])
            .append(" -> ")
            .append(mid)
            .append(", none is a prefix of ")
            .append(full);
      }
    }
    if (failures.length() > 0) {
      fail("small tsu + pending letter is not romanized to a prefix of the full unit:" + failures);
    }
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

  /** word, composition before the doubling, composition at small tsu + letter, full kana */
  private static final String[][] WORDS = {
    // control: k has a partial-input row
    {"サッカー", "さｋ", "さっｋ", "さっかー"},
    {"抹茶", "まｃ", "まっｃ", "まっちゃ"},
    {"キャッチャー", "きゃｃ", "きゃっｃ", "きゃっちゃー"},
    {"ブリッジ", "ぶりｊ", "ぶりっｊ", "ぶりっじ"},
    {"スタッフ", "すたｆ", "すたっｆ", "すたっふ"},
  };

  /** Analyzer level: QUERY-mode tokens of the composition vs INDEX-mode tokens of the word. */
  public void testAnalyzerQueryModeMidComposition() throws IOException {
    StringBuilder failures = new StringBuilder();
    try (Analyzer index = new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.INDEX);
        Analyzer query = new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.QUERY)) {
      for (String[] w : WORDS) {
        List<String> idx = tokens(index, w[0]);
        for (int i = 1; i <= 3; i++) {
          // full-width pending letter (as an input method shows it) and its half-width form
          String[] forms = {w[i], toHalfWidthAlpha(w[i])};
          for (String form : forms) {
            List<String> q = tokens(query, form);
            boolean ok = anyPrefix(q, idx);
            System.out.println(
                "word " + w[0] + " index " + idx + " | query '" + form + "' -> " + q
                    + (ok ? "  OK" : "  NO PREFIX"));
            if (!ok) {
              failures
                  .append("\n  '")
                  .append(form)
                  .append("' -> ")
                  .append(q)
                  .append(", none is a prefix of ")
                  .append(w[0])
                  .append(" ")
                  .append(idx);
            }
          }
        }
      }
    }
    if (failures.length() > 0) {
      fail("QUERY-mode tokens of a mid-composition state do not prefix the word:" + failures);
    }
  }

  private static String toHalfWidthAlpha(String s) {
    StringBuilder sb = new StringBuilder();
    for (char ch : s.toCharArray()) {
      if (ch >= 'ａ' && ch <= 'ｚ') {
        sb.append((char) (ch - 'ａ' + 'a'));
      } else {
        sb.append(ch);
      }
    }
    return sb.toString();
  }

  private static PostingsFormat completionPostingsFormat() {
    for (String name :
        new String[] {"Completion104", "Completion101", "Completion912", "Completion99"}) {
      if (PostingsFormat.availablePostingsFormats().contains(name)) {
        return PostingsFormat.forName(name);
      }
    }
    throw new AssertionError("no completion postings format found");
  }

  /** End to end: document-based completion suggester (SuggestField + PrefixCompletionQuery). */
  public void testSuggesterMidComposition() throws IOException {
    final String field = "suggest";
    final Codec base = TestUtil.getDefaultCodec();
    final PostingsFormat cpf = completionPostingsFormat();
    final PostingsFormat perField =
        new PerFieldPostingsFormat() {
          @Override
          public PostingsFormat getPostingsFormatForField(String f) {
            return cpf;
          }
        };
    Codec codec =
        new FilterCodec(base.getName(), base) {
          @Override
          public PostingsFormat postingsFormat() {
            return perField;
          }
        };
    StringBuilder failures = new StringBuilder();
    try (Analyzer index = new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.INDEX);
        Analyzer query = new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.QUERY);
        Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new CompletionAnalyzer(index)).setCodec(codec);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (String[] word : WORDS) {
          Document doc = new Document();
          doc.add(new SuggestField(field, word[0], 1));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
        CompletionAnalyzer queryAnalyzer = new CompletionAnalyzer(query);
        for (String[] word : WORDS) {
          for (int i = 1; i <= 3; i++) {
            PrefixCompletionQuery q =
                new PrefixCompletionQuery(queryAnalyzer, new Term(field, word[i]));
            TopSuggestDocs top = searcher.suggest(q, 10, false);
            List<String> keys = new ArrayList<>();
            for (TopSuggestDocs.SuggestScoreDoc sd : top.scoreLookupDocs()) {
              keys.add(sd.key.toString());
            }
            boolean ok = keys.contains(word[0]);
            System.out.println(
                "suggest '" + word[i] + "' -> " + keys + (ok ? "" : "   (missing " + word[0] + ")"));
            if (!ok) {
              failures
                  .append("\n  '")
                  .append(word[i])
                  .append("' -> ")
                  .append(keys)
                  .append(", expected ")
                  .append(word[0]);
            }
          }
        }
      }
    }
    if (failures.length() > 0) {
      fail("mid-composition state loses its suggestion:" + failures);
    }
  }
}
