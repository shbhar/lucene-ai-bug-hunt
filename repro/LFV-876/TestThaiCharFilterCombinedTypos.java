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
import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharFilter;
import org.apache.lucene.analysis.TokenStream;
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
 * Two of the typos that ThaiCharFilter documents fixing, inside one character cluster: a zero-width
 * character or a repeated tone mark sits inside a pattern that a multi-character rule rewrites.
 */
public class TestThaiCharFilterCombinedTypos extends BaseTokenStreamTestCase {

  private static void assertCharFilter(String input, String expected) throws IOException {
    CharFilter reader = new ThaiCharFilter(new StringReader(input));
    TokenStream ts = whitespaceMockTokenizer(reader);
    assertTokenStreamContents(
        ts, new String[] {expected}, new int[] {0}, new int[] {input.length()}, input.length());
  }

  /** Each typo on its own is fixed (this passes today). */
  public void testSingleTypos() throws IOException {
    assertCharFilter("ท\u0E48\u0E35", "ที่"); // tone mark before its vowel
    assertCharFilter("ท\u0E35\u0E48\u0E48", "ที่"); // tone mark typed twice
    assertCharFilter("\u0E40\u0E40มว", "แมว"); // double Sara E
    assertCharFilter("ท\u0E4D\u0E32", "ทำ"); // decomposed Sara Am
    assertCharFilter("ส\u200Bดี", "สดี"); // zero-width space
  }

  /** Tone mark typed twice and before its vowel: ท + Mai Ek + Mai Ek + Sara Ii. */
  public void testRepeatedToneBeforeVowel() throws IOException {
    assertCharFilter("ท\u0E48\u0E48\u0E35", "ที่");
  }

  /** Tone mark before its vowel, with a zero-width space between them. */
  public void testZeroWidthBetweenToneAndVowel() throws IOException {
    assertCharFilter("ท\u0E48\u200B\u0E35", "ที่");
  }

  /** Double Sara E with a zero-width joiner between the two Sara E. */
  public void testZeroWidthInsideDoubleSaraE() throws IOException {
    assertCharFilter("\u0E40\u200D\u0E40มว", "แมว");
  }

  /** Decomposed Sara Am with a zero-width space between Nikhahit and Sara Aa. */
  public void testZeroWidthInsideDecomposedSaraAm() throws IOException {
    assertCharFilter("ท\u0E4D\u200B\u0E32", "ทำ");
  }

  /** The default ThaiAnalyzer must keep the word boundaries of the clean text. */
  public void testThaiAnalyzerWordBoundaries() throws IOException {
    assumeTrue(
        "JRE does not support Thai dictionary-based BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
    Analyzer a = new ThaiAnalyzer();
    // "ฉันทำงานที่บ้าน" (I work at home), with the tone mark of ที่ typed twice and before its
    // vowel; ที่ itself is a stop word
    assertAnalyzesTo(
        a,
        "ฉันทำงานท\u0E48\u0E48\u0E35บ้าน",
        new String[] {"ฉัน", "ทำ", "งาน", "บ้าน"},
        new int[] {0, 3, 5, 12},
        new int[] {3, 5, 8, 16});
    // "ฉันรักแมวมาก" (I love cats a lot), แ typed as Sara E + ZWJ + Sara E; มาก is a stop word
    assertAnalyzesTo(
        a,
        "ฉันรัก\u0E40\u200D\u0E40มวมาก",
        new String[] {"ฉัน", "รัก", "แมว"},
        new int[] {0, 3, 6},
        new int[] {3, 6, 11});
    a.close();
  }

  /** A document is found by the words it contains. */
  public void testTermQueryFindsWord() throws IOException {
    assumeTrue(
        "JRE does not support Thai dictionary-based BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
    Analyzer a = new ThaiAnalyzer();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
      Document home = new Document();
      home.add(new TextField("body", "ฉันทำงานท\u0E48\u0E48\u0E35บ้าน", Field.Store.NO));
      w.addDocument(home);
      Document cat = new Document();
      cat.add(new TextField("body", "ฉันรัก\u0E40\u200D\u0E40มวมาก", Field.Store.NO));
      w.addDocument(cat);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        assertEquals(1, s.count(new TermQuery(new Term("body", "บ้าน"))));
        assertEquals(1, s.count(new TermQuery(new Term("body", "แมว"))));
      }
    }
    a.close();
  }
}
