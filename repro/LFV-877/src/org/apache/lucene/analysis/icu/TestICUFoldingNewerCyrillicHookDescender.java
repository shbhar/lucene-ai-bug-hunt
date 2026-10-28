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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer;
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

/**
 * ICUFoldingFilter documents "Diacritic removal (including stroke, hook, descender)". It removes the
 * hook, stroke or descender from every Cyrillic letter of that kind encoded up to Unicode 3.2 (for
 * example U+04A7 PE WITH MIDDLE HOOK, U+04C6 EL WITH TAIL, U+0493 GHE WITH STROKE), but from none of
 * the Cyrillic letters of the same kind added in Unicode 4.1 to 7.0.
 */
public class TestICUFoldingNewerCyrillicHookDescender extends BaseTokenStreamTestCase {

  private static String fold(String s) throws IOException {
    Tokenizer t = new KeywordTokenizer();
    t.setReader(new java.io.StringReader(s));
    try (TokenStream ts = new ICUFoldingFilter(t)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      String out = ts.incrementToken() ? term.toString() : "";
      ts.end();
      return out;
    }
  }

  /** {letter, base letter}: older letters of the same kind (controls, folded today). */
  private static final String[][] OLDER = {
    {"ҧ", "п"}, // PE WITH MIDDLE HOOK (older Abkhaz orthography) -> pe
    {"ғ", "г"}, // GHE WITH STROKE -> ghe
    {"ҕ", "г"}, // GHE WITH MIDDLE HOOK -> ghe
    {"ҳ", "х"}, // HA WITH DESCENDER -> ha
    {"ӆ", "л"}, // EL WITH TAIL -> el
    {"ң", "н"}, // EN WITH DESCENDER -> en
    {"ӈ", "н"}, // EN WITH HOOK -> en
    {"ҭ", "т"}, // TE WITH DESCENDER -> te
  };

  /** {letter, base letter}: Cyrillic letters with a hook, stroke or descender added in Unicode 4.1-7.0. */
  private static final String[][] NEWER = {
    {"ӷ", "г"}, {"Ӷ", "Г"}, // GHE WITH DESCENDER (4.1)
    {"ӻ", "г"}, {"Ӻ", "Г"}, // GHE WITH STROKE AND HOOK (5.0)
    {"ӽ", "х"}, {"Ӽ", "Х"}, // HA WITH HOOK (5.0)
    {"ӿ", "х"}, {"Ӿ", "Х"}, // HA WITH STROKE (5.0)
    {"ԓ", "л"}, {"Ԓ", "Л"}, // EL WITH HOOK (5.0)
    {"ԡ", "л"}, {"Ԡ", "Л"}, // EL WITH MIDDLE HOOK (5.1)
    {"ԣ", "н"}, {"Ԣ", "Н"}, // EN WITH MIDDLE HOOK (5.1)
    {"ꚋ", "т"}, {"Ꚋ", "Т"}, // TE WITH MIDDLE HOOK (5.1)
    {"ԥ", "п"}, {"Ԥ", "П"}, // PE WITH DESCENDER (5.2, modern Abkhaz orthography)
    {"ԧ", "һ"}, {"Ԧ", "Һ"}, // SHHA WITH DESCENDER (6.0)
    {"ԩ", "н"}, {"Ԩ", "Н"}, // EN WITH LEFT HOOK (7.0)
    {"ԯ", "л"}, {"Ԯ", "Л"}, // EL WITH DESCENDER (7.0)
  };

  public void testNewerLettersWithHookStrokeDescenderAreFolded() throws Exception {
    for (String[] p : OLDER) {
      assertEquals("control " + p[0], fold(p[1]), fold(p[0]));
    }
    List<String> unfolded = new ArrayList<>();
    for (String[] p : NEWER) {
      String got = fold(p[0]), want = fold(p[1]);
      if (!got.equals(want)) {
        unfolded.add(
            String.format(
                java.util.Locale.ROOT, "U+%04X %s -> %s (base %s)", p[0].codePointAt(0), p[0], got, want));
      }
    }
    assertTrue(
        unfolded.size() + " of " + NEWER.length + " letters keep their hook/stroke/descender: " + unfolded,
        unfolded.isEmpty());
  }

  private static String firstToken(Analyzer a, String text) throws IOException {
    try (TokenStream ts = a.tokenStream("body", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      assertTrue(ts.incrementToken());
      String out = term.toString();
      assertFalse(ts.incrementToken());
      ts.end();
      return out;
    }
  }

  /**
   * The same Abkhaz word in the older orthography (U+04A7) and in the modern orthography (U+0525)
   * should give the same indexed term once the hook or descender is removed.
   */
  public void testAbkhazOlderAndModernOrthographyMatch() throws Exception {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new ICUTokenizer();
            return new TokenStreamComponents(t, new ICUFoldingFilter(t));
          }
        };
    String older = "\u0430\u04A7\u0441\u0443\u0430"; // older orthography, PE WITH MIDDLE HOOK
    String modern = "\u0430\u0525\u0441\u0443\u0430"; // modern orthography, PE WITH DESCENDER
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new TextField("body", older, Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        String olderTerm = firstToken(a, older);
        String modernTerm = firstToken(a, modern);
        assertEquals(1, s.count(new TermQuery(new Term("body", olderTerm))));
        assertEquals(
            "query '" + modern + "' analyzed to '" + modernTerm + "', index holds '" + olderTerm + "'",
            1,
            s.count(new TermQuery(new Term("body", modernTerm))));
      }
    }
    a.close();
  }
}
