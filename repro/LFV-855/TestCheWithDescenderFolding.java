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
import com.ibm.icu.text.Normalizer2;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.util.QueryBuilder;

/**
 * Independent check: ICUFoldingFilter documents "Diacritic removal (including stroke, hook,
 * descender)". CYRILLIC SMALL/CAPITAL LETTER CHE WITH DESCENDER (U+04B7 / U+04B6) should therefore
 * fold like CHE (U+0447), as all other Cyrillic "... WITH DESCENDER" letters fold like their base.
 * Only public API is used.
 */
public class TestCheWithDescenderFolding extends BaseTokenStreamTestCase {

  private Analyzer analyzer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new MockTokenizer(MockTokenizer.WHITESPACE, false);
            return new TokenStreamComponents(t, new ICUFoldingFilter(t));
          }
        };
  }

  @Override
  public void tearDown() throws Exception {
    analyzer.close();
    super.tearDown();
  }

  private static String fold(int cp) {
    return ICUFoldingFilter.NORMALIZER.normalize(new String(Character.toChars(cp)));
  }

  private static String hex(String s) {
    StringBuilder sb = new StringBuilder();
    s.codePoints().forEach(c -> sb.append(String.format("U+%04X ", c)));
    return sb.toString().trim();
  }

  /** Control: sibling descender letters fold to their base letter (ҳ->х, қ->к, ң->н, ҫ->с). */
  public void testControlSiblingDescenderLetters() throws Exception {
    assertAnalyzesTo(analyzer, "ҳ қ ң ҫ", new String[] {"х", "к", "н", "с"});
    assertAnalyzesTo(analyzer, "Ҳ Қ Ң Ҫ", new String[] {"х", "к", "н", "с"});
  }

  /** Small CHE WITH DESCENDER (U+04B7 ҷ) should fold to CHE (U+0447 ч). */
  public void testSmallCheWithDescender() throws Exception {
    System.out.println("fold(U+04B7) = " + hex(fold(0x04B7)));
    assertAnalyzesTo(analyzer, "ҷ", new String[] {"ч"});
  }

  /** Capital CHE WITH DESCENDER (U+04B6 Ҷ) should fold to CHE (U+0447 ч). */
  public void testCapitalCheWithDescender() throws Exception {
    System.out.println("fold(U+04B6) = " + hex(fold(0x04B6)));
    assertAnalyzesTo(analyzer, "Ҷ", new String[] {"ч"});
  }

  /** End to end: a Tajik document is found by the same words typed with plain Russian letters. */
  public void testSearchTajikWithPlainLetters() throws Exception {
    String docText = "Ҷумҳурии Тоҷикистон"; // "Republic of Tajikistan", Tajik spelling
    String queryText = "Чумхурии Точикистон"; // same words, ҷ->ч and ҳ->х
    try (Directory dir = newDirectory()) {
      try (RandomIndexWriter w = new RandomIndexWriter(random(), dir, analyzer)) {
        Document d = new Document();
        d.add(new TextField("body", docText, Field.Store.NO));
        w.addDocument(d);
        try (IndexReader r = w.getReader()) {
          IndexSearcher s = newSearcher(r);
          QueryBuilder qb = new QueryBuilder(analyzer);
          Query self = qb.createPhraseQuery("body", docText);
          assertEquals("sanity: exact spelling must match itself", 1, s.count(self));
          Query q = qb.createPhraseQuery("body", queryText);
          assertEquals(
              "query " + q + " should find the document; document 'Ҷумҳурии' folds to " + hex(fold(0x04B6)),
              1,
              s.count(q));
        }
      }
    }
  }

  /**
   * Survey (does not fail): every letter of the Cyrillic and Cyrillic Supplement blocks whose UCD
   * name is "&lt;base&gt; WITH DESCENDER" with an existing base letter, compared with the folding of
   * that base letter. Prints the mismatches.
   */
  public void testSurveyAllCyrillicWithDescender() {
    Normalizer2 n = ICUFoldingFilter.NORMALIZER;
    List<String> mismatches = new ArrayList<>();
    int checked = 0;
    for (int cp = 0x0400; cp <= 0x052F; cp++) {
      String name = UCharacter.getName(cp);
      if (name == null || !name.endsWith(" WITH DESCENDER")) {
        continue;
      }
      String baseName = name.substring(0, name.length() - " WITH DESCENDER".length());
      int base = UCharacter.getCharFromName(baseName);
      if (base < 0) {
        continue;
      }
      checked++;
      String got = n.normalize(new String(Character.toChars(cp)));
      String want = n.normalize(new String(Character.toChars(base)));
      if (!got.equals(want)) {
        mismatches.add(
            String.format(
                "U+%04X %s -> %s, base U+%04X folds to %s", cp, name, hex(got), base, hex(want)));
      }
    }
    System.out.println("SURVEY checked=" + checked + " mismatches=" + mismatches.size());
    for (String m : mismatches) {
      System.out.println("SURVEY " + m);
    }
    assertTrue("survey found no letters", checked > 10);
  }
}
