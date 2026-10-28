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
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;

/**
 * ICUFoldingFilter documents "Diacritic removal (including stroke, hook, descender)". In the
 * Cyrillic block of DiacriticFolding.txt the "X WITH DESCENDER" letters fold to X, but CHE WITH
 * DESCENDER (U+04B6/U+04B7, Tajik and Abkhaz) folds to ABKHASIAN CHE (U+04BD), a different letter.
 */
public class TestICUFoldingCheWithDescender extends BaseTokenStreamTestCase {
  private Analyzer a;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    a =
        new Analyzer() {
          @Override
          public TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new MockTokenizer(MockTokenizer.WHITESPACE, false);
            return new TokenStreamComponents(tokenizer, new ICUFoldingFilter(tokenizer));
          }
        };
  }

  @Override
  public void tearDown() throws Exception {
    a.close();
    super.tearDown();
  }

  /** Control (passes): selected sibling letters WITH DESCENDER of the block lose the descender. */
  public void testOtherDescenderLettersFoldToBase() throws IOException {
    // small ZHE, ZE, KA, EN, ES, TE, HA WITH DESCENDER, then capital ZHE and HA WITH DESCENDER
    assertAnalyzesTo(
        a, "җ ҙ қ ң ҫ ҭ ҳ Җ Ҳ", new String[] {"ж", "з", "к", "н", "с", "т", "х", "ж", "х"});
    // CHE WITH VERTICAL STROKE folds to CHE
    assertAnalyzesTo(a, "ҹ Ҹ", new String[] {"ч", "ч"});
  }

  /** CHE WITH DESCENDER must fold to CHE (U+0447), like CHE WITH VERTICAL STROKE does. */
  public void testCheWithDescenderFoldsToChe() throws IOException {
    assertAnalyzesTo(a, "ҷ Ҷ", new String[] {"ч", "ч"});
  }

  /**
   * End to end: the Tajik document "Ҷумҳурии Тоҷикистон" (Republic of Tajikistan) must be found by
   * the same words typed without the Tajik diacritic letters ("Чумхурии Точикистон"), as it is for
   * every other diacritic letter in the words (ҳ folds to х).
   */
  public void testTajikQueryWithoutDescenderFindsDocument() throws IOException {
    String doc = "Ҷумҳурии Тоҷикистон";
    String withDiacritics = doc;
    String plain = "Чумхурии Точикистон";
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document d = new Document();
        d.add(new TextField("body", doc, Field.Store.NO));
        w.addDocument(d);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        // control: the exact spelling matches
        assertEquals(1, s.count(allTerms("body", withDiacritics)));
        assertEquals(
            "query '"
                + plain
                + "' analyzed to "
                + analyzed(plain)
                + ", document to "
                + analyzed(doc),
            1,
            s.count(allTerms("body", plain)));
      }
    }
  }

  private BooleanQuery allTerms(String field, String text) throws IOException {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (String t : analyzed(text).split(" ")) {
      b.add(new TermQuery(new Term(field, t)), BooleanClause.Occur.MUST);
    }
    return b.build();
  }

  private String analyzed(String text) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (TokenStream ts = a.tokenStream("body", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        if (sb.length() > 0) sb.append(' ');
        sb.append(term);
      }
      ts.end();
    }
    return sb.toString();
  }
}
