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
package org.apache.lucene.analysis.ckb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * SoraniNormalizationFilter removes formatting characters (general category Cf). It should remove
 * the ones outside the BMP, which are stored as two chars (a surrogate pair), as it removes the BMP
 * ones.
 */
public class TestSoraniSupplementaryFormatRemoval extends BaseTokenStreamTestCase {

  private static final String LEFT = "کورد"; // "kurd"
  private static final String RIGHT = "ستان"; // "stan"
  private static final String WORD = LEFT + RIGHT; // "kurdistan"

  private static final int RLM = 0x200F; // RIGHT-TO-LEFT MARK (BMP)
  private static final int LANGUAGE_TAG = 0xE0001; // LANGUAGE TAG (supplementary)

  private static String str(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  /** "U+XXXX" per code point, so that a failure message is readable. */
  private static String hex(String s) {
    StringBuilder sb = new StringBuilder();
    s.codePoints().forEach(cp -> sb.append(String.format(Locale.ROOT, " U+%04X", cp)));
    return sb.toString().trim();
  }

  private static Analyzer normalizerOnly() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new KeywordTokenizer();
        return new TokenStreamComponents(tokenizer, new SoraniNormalizationFilter(tokenizer));
      }
    };
  }

  private static String normalizeOne(Analyzer a, String text) throws IOException {
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

  /** Control, passes on stock Lucene: BMP formatting characters inside the word are removed. */
  public void testBmpFormatCharactersRemoved() throws IOException {
    try (Analyzer a = normalizerOnly()) {
      for (int cp : new int[] {RLM, 0x200E, 0x2066, 0x00AD, 0xFEFF}) {
        assertEquals(Character.FORMAT, Character.getType(cp));
        checkOneTerm(a, LEFT + str(cp) + RIGHT, WORD);
      }
    }
  }

  public void testSupplementaryFormatCharactersRemoved() throws IOException {
    List<String> wrong = new ArrayList<>();
    try (Analyzer a = normalizerOnly()) {
      for (int cp : new int[] {LANGUAGE_TAG, 0xE0067, 0x1BCA0, 0x1D173}) {
        assertEquals(Character.FORMAT, Character.getType(cp));
        String got = normalizeOne(a, LEFT + str(cp) + RIGHT);
        if (got.equals(WORD) == false) {
          wrong.add(String.format(Locale.ROOT, "U+%04X kept: %s", cp, hex(got)));
        }
      }
    }
    assertTrue("expected " + hex(WORD) + "\n" + String.join("\n", wrong), wrong.isEmpty());
  }

  /** The plain word should find all three documents; each has one invisible Cf character. */
  public void testSoraniAnalyzerSearch() throws IOException {
    String[] docs = {
      LEFT + str(RLM) + RIGHT, // control
      LEFT + str(LANGUAGE_TAG) + RIGHT, // inside the word
      WORD + str(LANGUAGE_TAG), // at the end of the word
    };
    try (Analyzer a = new SoraniAnalyzer();
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(a))) {
        for (String text : docs) {
          Document doc = new Document();
          doc.add(new TextField("body", text, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Query q = new QueryBuilder(a).createBooleanQuery("body", WORD);
        assertEquals("hits for " + q, docs.length, newSearcher(r).count(q));
      }
    }
  }

  /** Analyzer.normalize, which query parsers use for wildcard, prefix, fuzzy and range terms. */
  public void testSoraniAnalyzerNormalize() throws IOException {
    try (Analyzer a = new SoraniAnalyzer()) {
      String plain = hex(a.normalize("body", WORD).utf8ToString());
      for (int cp : new int[] {RLM, LANGUAGE_TAG}) {
        assertEquals(plain, hex(a.normalize("body", LEFT + str(cp) + RIGHT).utf8ToString()));
      }
    }
  }
}
