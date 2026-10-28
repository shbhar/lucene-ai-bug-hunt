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
package org.apache.lucene.analysis.th;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
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
 * Independent check: ThaiCharFilter documents that it removes zero-width characters, deduplicates
 * repeated tone marks, and rewrites decomposed Sara Am, double Sara E and a tone mark placed before
 * an above/below vowel. When one of the deleting rules (zero-width removal, deduplication) applies
 * to a character that sits inside one of the multi-character patterns, the pattern is left in the
 * output.
 *
 * <p>Oracle: removing a zero-width character, or one copy of a doubled tone mark, from the input
 * must not change the char filter output, because the filter itself documents removing it. Each
 * "clean" twin below is handled correctly by the filter (see testControls).
 */
public class TestThaiCharFilterCombinedAnomalies extends BaseTokenStreamTestCase {

  private static String filter(String s) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (Reader r = new ThaiCharFilter(new StringReader(s))) {
      int c;
      while ((c = r.read()) != -1) {
        sb.append((char) c);
      }
    }
    return sb.toString();
  }

  private static String hex(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      if (i > 0) sb.append(' ');
      sb.append(String.format("U+%04X", (int) s.charAt(i)));
    }
    return sb.toString();
  }

  private static void assertSameOutput(String dirty, String clean) throws IOException {
    String expected = filter(clean);
    String actual = filter(dirty);
    assertEquals(
        "input ["
            + hex(dirty)
            + "] should normalize like ["
            + hex(clean)
            + "] = ["
            + hex(expected)
            + "] but gave ["
            + hex(actual)
            + "]",
        expected,
        actual);
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

  /** Each single documented anomaly is handled: these must pass (sanity check of the oracle). */
  public void testControls() throws IOException {
    assertEquals("ทำ", filter("ทํา")); // ท ํ า -> ทำ
    assertEquals("น้ำ", filter("นํ้า")); // น ํ ้ า -> น้ำ
    assertEquals("แมว", filter("เเมว")); // เเมว -> แมว
    assertEquals("ที่", filter("ท่ี")); // ท ่ ี -> ที่
    assertEquals("ที่", filter("ที่่")); // ที่่ -> ที่
    assertEquals("สดี", filter("ส\u200Bดี")); // ส ZWSP ดี -> สดี
  }

  /** ท ํ ZWSP า: zero-width removal leaves ํ า, which the filter documents recomposing to ำ. */
  public void testZeroWidthInsideDecomposedSaraAm() throws IOException {
    assertSameOutput("ทํ\u200Bา", "ทํา");
  }

  /** เ ZWJ เ มว: zero-width removal leaves เเ, which the filter documents replacing with แ. */
  public void testZeroWidthInsideDoubleSaraE() throws IOException {
    assertSameOutput("เ\u200Dเมว", "เเมว");
  }

  /** ท ่ ZWSP ี: zero-width removal leaves tone-before-vowel, which the filter reorders. */
  public void testZeroWidthBetweenToneAndAboveVowel() throws IOException {
    assertSameOutput("ท่\u200Bี", "ท่ี");
  }

  /** ท ่ ่ ี: deduplication leaves tone-before-vowel, which the filter reorders. */
  public void testRepeatedToneBeforeAboveVowel() throws IOException {
    assertSameOutput("ท่่ี", "ท่ี");
  }

  /**
   * น ํ ้ ้ า: deduplication leaves ํ ้ า, which the filter recomposes to ้ ำ. (Weaker case: if the
   * javadoc list were read as ordered global passes, Sara Am recomposition would run before
   * deduplication and this input would stay decomposed.)
   */
  public void testRepeatedToneInsideDecomposedSaraAm() throws IOException {
    assertSameOutput("นํ้้า", "นํ้า");
  }

  /**
   * ThaiAnalyzer on a sentence with a ZWJ between the two Sara E of the upstream test sentence
   * ฉันรักเเมวมาก: it should give the same terms as without the invisible character.
   */
  public void testThaiAnalyzerZeroWidthInsideDoubleSaraE() throws IOException {
    assertTrue("needs the dictionary BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
    try (Analyzer a = new ThaiAnalyzer()) {
      String clean = "ฉันรักเเมวมาก";
      String dirty =
          "ฉันรักเ\u200Dเมวมาก";
      assertEquals(terms(a, clean), terms(a, dirty));
    }
  }

  /** ThaiAnalyzer: ฉันทำงานที่บ้าน with the tone of ที่ typed twice and before the vowel. */
  public void testThaiAnalyzerRepeatedToneBeforeVowel() throws IOException {
    assertTrue("needs the dictionary BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
    try (Analyzer a = new ThaiAnalyzer()) {
      String clean = "ฉันทำงานที่บ้าน";
      String dirty =
          "ฉันทำงานท่่ีบ้าน";
      assertEquals(terms(a, clean), terms(a, dirty));
    }
  }

  /** User-visible effect: the indexed sentence is not found by a word it contains (แมว, cat). */
  public void testSearchByWord() throws IOException {
    assertTrue("needs the dictionary BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
    try (Directory dir = newDirectory();
        Analyzer a = new ThaiAnalyzer()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(
            new TextField(
                "body",
                "ฉันรักเ\u200Dเมวมาก",
                Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        assertEquals(1, s.count(new TermQuery(new Term("body", "แมว"))));
      }
    }
  }
}
