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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * IrishLowerCaseFilter ("Normalises token text to lower case") lower-cases one UTF-16 unit at a
 * time: Character.toLowerCase(chArray[i]) maps a surrogate unit to itself, so upper-case letters
 * outside the BMP (Adlam, Osage, Deseret, ...) are not lower-cased, unlike LowerCaseFilter.
 */
public class TestIrishLowerCaseSupplementary extends BaseTokenStreamTestCase {
  // Adlam (a living script, used for Fulani): capital alif U+1E900, small alif U+1E922,
  // small dha U+1E923
  static final String ADLAM_UPPER = new String(Character.toChars(0x1E900)) + new String(Character.toChars(0x1E923));
  static final String ADLAM_LOWER = new String(Character.toChars(0x1E922)) + new String(Character.toChars(0x1E923));
  // Deseret capital long I U+10400 -> small U+10428
  static final String DESERET_UPPER = new String(Character.toChars(0x10400));
  static final String DESERET_LOWER = new String(Character.toChars(0x10428));

  private static Analyzer filter(boolean irish) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new KeywordTokenizer();
        return new TokenStreamComponents(t, irish ? new IrishLowerCaseFilter(t) : new LowerCaseFilter(t));
      }
    };
  }

  /** Control: BMP behaviour (javadoc example) and LowerCaseFilter's result for the same input. */
  public void testControls() throws IOException {
    try (Analyzer ga = filter(true);
        Analyzer lc = filter(false)) {
      checkOneTerm(ga, "nAthair", "n-athair");
      checkOneTerm(ga, "ÉIRE", "éire");
      checkOneTerm(lc, ADLAM_UPPER, ADLAM_LOWER);
      checkOneTerm(lc, DESERET_UPPER, DESERET_LOWER);
    }
  }

  private static String hex(String s) {
    StringBuilder b = new StringBuilder();
    s.codePoints().forEach(cp -> b.append(String.format(java.util.Locale.ROOT, "U+%04X ", cp)));
    return b.toString().trim();
  }

  private static String analyze(Analyzer a, String text) throws IOException {
    try (org.apache.lucene.analysis.TokenStream ts = a.tokenStream("f", text)) {
      org.apache.lucene.analysis.tokenattributes.CharTermAttribute t =
          ts.addAttribute(org.apache.lucene.analysis.tokenattributes.CharTermAttribute.class);
      ts.reset();
      assertTrue(ts.incrementToken());
      String out = t.toString();
      assertFalse(ts.incrementToken());
      ts.end();
      return out;
    }
  }

  public void testSupplementaryUpperCaseIsLowerCased() throws IOException {
    try (Analyzer ga = filter(true)) {
      assertEquals(hex(ADLAM_LOWER), hex(analyze(ga, ADLAM_UPPER)));
    }
  }

  public void testSupplementaryAfterNEclipsis() throws IOException {
    try (Analyzer ga = filter(true)) {
      assertEquals(hex("n-a" + DESERET_LOWER), hex(analyze(ga, "nA" + DESERET_UPPER)));
    }
  }

  /** End to end with IrishAnalyzer: a capitalised word is not found by its lower-case form. */
  public void testIrishAnalyzerCaseInsensitiveMatch() throws IOException {
    try (Analyzer a = new IrishAnalyzer();
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new TextField("body", ADLAM_UPPER, Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        // what IrishAnalyzer produces for the lower-case query text
        String q = a.normalize("body", ADLAM_LOWER).utf8ToString();
        assertEquals(1, s.count(new TermQuery(new Term("body", q))));
      }
    }
  }
}
