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
package org.apache.lucene.analysis.ga;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
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

/**
 * IrishLowerCaseFilter should lower-case upper-case letters outside the BMP (stored as surrogate
 * pairs) as LowerCaseFilter does. Deseret, Osage and Adlam letters are used here.
 */
public class TestIrishLowerCaseFilterSupplementary extends BaseTokenStreamTestCase {

  private static String str(int... codePoints) {
    return new String(codePoints, 0, codePoints.length);
  }

  /** "U+XXXX" per code point, so that a failure message is readable. */
  private static String hex(String s) {
    StringBuilder sb = new StringBuilder();
    s.codePoints().forEach(cp -> sb.append(String.format(Locale.ROOT, " U+%04X", cp)));
    return sb.toString().trim();
  }

  /** KeywordTokenizer followed by IrishLowerCaseFilter, or by LowerCaseFilter. */
  private static Analyzer keyword(boolean irish) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new KeywordTokenizer();
        return new TokenStreamComponents(
            t, irish ? new IrishLowerCaseFilter(t) : new LowerCaseFilter(t));
      }
    };
  }

  private static List<String> terms(Analyzer a, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("body", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  private static final String DESERET_UPPER = str(0x10400); // capital long I
  private static final String DESERET_LOWER = str(0x10428);
  private static final String OSAGE_UPPER = str(0x104B0); // capital A
  private static final String OSAGE_LOWER = str(0x104D8);
  private static final String ADLAM_UPPER = str(0x1E900, 0x1E923); // capital alif, small daali
  private static final String ADLAM_LOWER = str(0x1E922, 0x1E923);

  /** Input and expected output of the filter after KeywordTokenizer. */
  private static final String[][] CASES = {
    {DESERET_UPPER, DESERET_LOWER},
    {OSAGE_UPPER, OSAGE_LOWER},
    {ADLAM_UPPER, ADLAM_LOWER},
    {"AB" + OSAGE_UPPER + "C", "ab" + OSAGE_LOWER + "c"},
    {"nA" + DESERET_UPPER, "n-a" + DESERET_LOWER},
  };

  /** Controls, which pass on stock Lucene. */
  public void testControls() throws IOException {
    try (Analyzer irish = keyword(true);
        Analyzer lower = keyword(false)) {
      checkOneTerm(irish, "nAthair", "n-athair");
      checkOneTerm(irish, "ÉIRE", "éire");
      for (int i = 0; i < 4; i++) {
        assertEquals(hex(CASES[i][1]), hex(terms(lower, CASES[i][0]).get(0)));
      }
    }
  }

  public void testFilterLowerCasesSupplementaryLetters() throws IOException {
    List<String> wrong = new ArrayList<>();
    try (Analyzer irish = keyword(true)) {
      for (String[] c : CASES) {
        String got = terms(irish, c[0]).get(0);
        if (got.equals(c[1]) == false) {
          wrong.add(hex(c[0]) + " -> " + hex(got) + ", expected " + hex(c[1]));
        }
      }
    }
    assertTrue(String.join("\n", wrong), wrong.isEmpty());
  }

  public void testIrishAnalyzerNormalize() throws IOException {
    try (Analyzer a = new IrishAnalyzer()) {
      assertEquals(hex(ADLAM_LOWER), hex(a.normalize("body", ADLAM_UPPER).utf8ToString()));
    }
  }

  /** An Irish sentence that quotes a capitalised Adlam word is not found by its lower-case form. */
  public void testIrishAnalyzerSearch() throws IOException {
    try (Analyzer a = new IrishAnalyzer();
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new TextField("body", "Tá " + ADLAM_UPPER + " anseo", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        // the query text goes through the same analyzer, as a query parser would do
        String upper = terms(a, ADLAM_UPPER).get(0);
        String lower = terms(a, ADLAM_LOWER).get(0);
        assertEquals("hits for " + hex(upper), 1, s.count(new TermQuery(new Term("body", upper))));
        assertEquals("hits for " + hex(lower), 1, s.count(new TermQuery(new Term("body", lower))));
      }
    }
  }
}
