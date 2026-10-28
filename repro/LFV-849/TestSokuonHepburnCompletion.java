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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ja.completion.KatakanaRomanizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
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
 * JapaneseCompletionFilter documents that it supports (modified) Hepburn-shiki, Kunrei-shiki and
 * Wapuro (IME) romanization. A small tsu (sokuon) before shi/chi/tsu is written by doubling the
 * following consonant in Hepburn: ザッシ = zasshi, ケッシテ = kesshite, マッチ = matchi, ミッツ = mittsu,
 * マッチャ = matcha. Lucene's own Hepburn romanizer (JapaneseReadingFormFilter, useRomaji=true) gives
 * exactly these spellings. This test checks that the completion romanizer and the document
 * suggester accept them.
 */
public class TestSokuonHepburnCompletion extends LuceneTestCase {

  private static final String FIELD = "suggest";

  /** katakana reading -> Hepburn spelling (and, where different, the IME doubled spelling). */
  private static Map<String, String[]> hepburnReadings() {
    Map<String, String[]> m = new LinkedHashMap<>();
    m.put("ザッシ", new String[] {"zasshi"});
    m.put("ケッシテ", new String[] {"kesshite"});
    m.put("マッチ", new String[] {"matchi", "macchi"});
    m.put("ミッツ", new String[] {"mittsu"});
    m.put("マッチャ", new String[] {"matcha"});
    return m;
  }

  /** Controls: spellings the table does produce today. */
  private static Map<String, String[]> controlReadings() {
    Map<String, String[]> m = new LinkedHashMap<>();
    m.put("ザッシ", new String[] {"zassi"});
    m.put("マッチ", new String[] {"matti"});
    m.put("ミッツ", new String[] {"mittu"});
    m.put("マッチャ", new String[] {"maccha", "mattya"});
    m.put("イッショ", new String[] {"issho", "issyo"});
    m.put("シ", new String[] {"shi", "si"});
    m.put("ツ", new String[] {"tsu", "tu"});
    return m;
  }

  private static List<String> romanize(String katakana) {
    List<String> out = new ArrayList<>();
    for (CharsRef r : KatakanaRomanizer.getInstance().romanize(new CharsRef(katakana))) {
      out.add(r.toString());
    }
    return out;
  }

  /** Public API: KatakanaRomanizer#romanize, used by JapaneseCompletionFilter on the index side. */
  public void testRomanizerProducesHepburnSokuonSpellings() {
    for (Map.Entry<String, String[]> e : controlReadings().entrySet()) {
      List<String> got = romanize(e.getKey());
      for (String s : e.getValue()) {
        assertTrue("control " + e.getKey() + " -> " + got, got.contains(s));
      }
    }
    List<String> missing = new ArrayList<>();
    for (Map.Entry<String, String[]> e : hepburnReadings().entrySet()) {
      List<String> got = romanize(e.getKey());
      for (String s : e.getValue()) {
        if (got.contains(s) == false) {
          missing.add(e.getKey() + " -> " + got + " lacks '" + s + "'");
        }
      }
    }
    assertTrue("Hepburn/IME sokuon spellings missing:\n  " + String.join("\n  ", missing), missing.isEmpty());
  }

  /**
   * Oracle check (expected to pass): Lucene's own Hepburn romanizer spells the same words with a
   * doubled sh / tch / ts.
   */
  public void testReadingFormFilterHepburnOracle() throws IOException {
    Map<String, String> words = new LinkedHashMap<>();
    words.put("雑誌", "zasshi");
    words.put("決して", "kesshite");
    words.put("マッチ", "matchi");
    words.put("三つ", "mittsu");
    words.put("抹茶", "matcha");
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new JapaneseTokenizer(null, true, JapaneseTokenizer.Mode.NORMAL);
            return new TokenStreamComponents(t, new JapaneseReadingFormFilter(t, true));
          }
        };
    List<String> diffs = new ArrayList<>();
    for (Map.Entry<String, String> e : words.entrySet()) {
      List<String> toks = tokens(a, e.getKey());
      String joined = String.join("", toks);
      System.out.println("reading-form romaji " + e.getKey() + " -> " + toks);
      if (joined.equals(e.getValue()) == false) {
        diffs.add(e.getKey() + " -> " + toks + " (expected " + e.getValue() + ")");
      }
    }
    a.close();
    assertTrue("reading-form Hepburn differs:\n  " + String.join("\n  ", diffs), diffs.isEmpty());
  }

  /** End to end: index with JapaneseCompletionAnalyzer, query typed romaji with Mode.QUERY. */
  public void testSuggesterFindsHepburnSokuonSpellings() throws IOException {
    String[] docs = {"雑誌", "決して", "マッチ", "三つ", "抹茶", "一緒"};
    // typed query -> expected suggestion
    Map<String, String> controls = new LinkedHashMap<>();
    controls.put("zassi", "雑誌");
    controls.put("zass", "雑誌");
    controls.put("kessite", "決して");
    controls.put("matti", "マッチ");
    controls.put("mittu", "三つ");
    controls.put("maccha", "抹茶");
    controls.put("issho", "一緒");
    Map<String, String> hepburn = new LinkedHashMap<>();
    hepburn.put("zasshi", "雑誌");
    hepburn.put("zassh", "雑誌"); // prefix of zasshi while the user is still typing
    hepburn.put("kesshite", "決して");
    hepburn.put("matchi", "マッチ");
    hepburn.put("macchi", "マッチ");
    hepburn.put("mittsu", "三つ");
    hepburn.put("matcha", "抹茶");
    runSuggest(docs, controls, hepburn);
  }

  /**
   * Mid-IME-composition queries (full-width pending romaji after kana), the second design goal of
   * the filter: typing z-a-s-s-h in an IME shows "ざっｓｈ".
   */
  public void testSuggesterMidCompositionSokuon() throws IOException {
    String[] docs = {"雑誌", "三つ"};
    Map<String, String> controls = new LinkedHashMap<>();
    controls.put("ざっｓ", "雑誌");
    controls.put("みっｔ", "三つ");
    Map<String, String> pending = new LinkedHashMap<>();
    pending.put("ざっｓｈ", "雑誌");
    pending.put("みっｔｓ", "三つ");
    runSuggest(docs, controls, pending);
  }

  private void runSuggest(String[] docs, Map<String, String> controls, Map<String, String> hepburn)
      throws IOException {
    Analyzer indexAnalyzer = new CompletionAnalyzer(new JapaneseCompletionAnalyzer());
    Analyzer queryAnalyzer =
        new CompletionAnalyzer(
            new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.QUERY));
    StringBuilder indexSide = new StringBuilder();
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(indexAnalyzer);
      iwc.setCodec(completionCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (String d : docs) {
          Document doc = new Document();
          doc.add(new SuggestField(FIELD, d, 1));
          doc.add(new StoredField("surface", d));
          w.addDocument(doc);
          indexSide.append("\n  ").append(d).append(" -> ").append(tokens(new JapaneseCompletionAnalyzer(), d));
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        SuggestIndexSearcher s = new SuggestIndexSearcher(r);
        for (Map.Entry<String, String> e : controls.entrySet()) {
          List<String> got = suggest(s, r, queryAnalyzer, e.getKey());
          assertTrue("control '" + e.getKey() + "' -> " + got, got.contains(e.getValue()));
        }
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> e : hepburn.entrySet()) {
          List<String> got = suggest(s, r, queryAnalyzer, e.getKey());
          if (got.contains(e.getValue()) == false) {
            missing.add(
                "typed '"
                    + e.getKey()
                    + "' (query tokens "
                    + tokens(new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.QUERY), e.getKey())
                    + ") -> suggestions "
                    + got
                    + ", expected "
                    + e.getValue());
          }
        }
        assertTrue(
            "Hepburn/IME spellings with a sokuon find no suggestion:\n  "
                + String.join("\n  ", missing)
                + "\nindex-side tokens:"
                + indexSide,
            missing.isEmpty());
      }
    } finally {
      indexAnalyzer.close();
      queryAnalyzer.close();
    }
  }

  private static List<String> suggest(
      SuggestIndexSearcher s, DirectoryReader r, Analyzer qa, String typed) throws IOException {
    PrefixCompletionQuery q = new PrefixCompletionQuery(qa, new Term(FIELD, typed));
    TopSuggestDocs td = s.suggest(q, 10, false);
    List<String> out = new ArrayList<>();
    for (TopSuggestDocs.SuggestScoreDoc sd : td.scoreLookupDocs()) {
      out.add(r.storedFields().document(sd.doc).get("surface"));
    }
    return out;
  }

  private static List<String> tokens(Analyzer a, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(t.toString());
      }
      ts.end();
    }
    return out;
  }

  /** The completion postings format that matches this version's default postings format. */
  private static Codec completionCodec() {
    Codec base = TestUtil.getDefaultCodec();
    String pfName = TestUtil.getDefaultPostingsFormat().getName(); // e.g. "Lucene104"
    String suffix = pfName.startsWith("Lucene") ? pfName.substring("Lucene".length()) : pfName;
    final PostingsFormat completion = PostingsFormat.forName("Completion" + suffix);
    return new FilterCodec(base.getName(), base) {
      @Override
      public PostingsFormat postingsFormat() {
        final PostingsFormat delegatePf = base.postingsFormat();
        return new PerFieldPostingsFormat() {
          @Override
          public PostingsFormat getPostingsFormatForField(String field) {
            if (FIELD.equals(field)) {
              return completion;
            }
            return delegatePf instanceof PerFieldPostingsFormat
                ? ((PerFieldPostingsFormat) delegatePf).getPostingsFormatForField(field)
                : delegatePf;
          }
        };
      }
    };
  }
}
