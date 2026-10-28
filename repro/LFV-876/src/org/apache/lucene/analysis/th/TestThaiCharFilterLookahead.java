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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
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
 * ThaiCharFilter matches its multi-character patterns (Sara Am recomposition, double Sara E, tone mark before an
 * above/below vowel) against the raw look-ahead characters, before its other documented rules (zero-width removal,
 * deduplication, Lakkhangyao to Sara Aa) have been applied to those characters. When one of those rules has to run
 * first, the pattern is left in the output, although the class javadoc says it is normalized.
 */
public class TestThaiCharFilterLookahead extends BaseTokenStreamTestCase {

  private static String charFilter(String s) throws IOException {
    Reader r = new ThaiCharFilter(new StringReader(s));
    StringBuilder b = new StringBuilder();
    int c;
    while ((c = r.read()) != -1) {
      b.append((char) c);
    }
    return b.toString();
  }

  /** Sara Am recomposition must see the Sara Aa that the Lakkhangyao rule produces. */
  public void testLakkhangyaoInsideDecomposedSaraAm() throws IOException {
    // ท + NIKHAHIT + LAKKHANGYAO: Lakkhangyao -> Sara Aa, then Nikhahit + Sara Aa -> Sara Am ("ทำ")
    assertEquals("ทำ", charFilter("\u0E17\u0E4D\u0E45"));
  }

  /** Sara Am recomposition with a tone mark must see the tone mark after deduplication. */
  public void testDuplicatedToneInsideDecomposedSaraAm() throws IOException {
    // น + NIKHAHIT + MAI THO + MAI THO + SARA AA: dedup, then Nikhahit + tone + Sara Aa -> tone + Sara Am ("น้ำ")
    assertEquals("น้ำ", charFilter("\u0E19\u0E4D\u0E49\u0E49\u0E32"));
  }

  /** A zero-width space inside a decomposed Sara Am is removed, but the pair is not recomposed. */
  public void testZeroWidthInsideDecomposedSaraAm() throws IOException {
    assertEquals("ทำ", charFilter("\u0E17\u0E4D\u200B\u0E32"));
  }

  /** Tone/vowel reordering must see the tone mark after deduplication. */
  public void testDuplicatedToneBeforeAboveVowel() throws IOException {
    // ท + MAI EK + MAI EK + SARA II: dedup, then tone before above vowel -> vowel + tone ("ที่")
    assertEquals("ที่", charFilter("\u0E17\u0E48\u0E48\u0E35"));
  }

  /** Double Sara E with a zero-width space between them. */
  public void testZeroWidthInsideDoubleSaraE() throws IOException {
    assertEquals("แมว", charFilter("\u0E40\u200B\u0E40\u0E21\u0E27"));
  }

  /**
   * ThaiAnalyzer: the un-normalized pattern reaches the dictionary BreakIterator, which then merges the word with
   * its neighbours (the failure the ThaiCharFilter javadoc says it exists to prevent).
   */
  public void testThaiAnalyzerMergesWords() throws IOException {
    assumeTrue("JRE does not support Thai dictionary-based BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
    Analyzer a = new ThaiAnalyzer(CharArraySet.EMPTY_SET);
    // canonical: "I work at home"
    assertAnalyzesTo(a, "ฉันทำงานที่บ้าน",
        new String[] {"ฉัน", "ทำ", "งาน", "ที่", "บ้าน"});
    // same text, "ที่" typed with its tone mark doubled and before the vowel (two documented typos)
    assertAnalyzesTo(a, "\u0E09\u0E31\u0E19\u0E17\u0E33\u0E07\u0E32\u0E19\u0E17\u0E48\u0E48\u0E35\u0E1A\u0E49\u0E32\u0E19",
        new String[] {"ฉัน", "ทำ", "งาน", "ที่", "บ้าน"});
    a.close();
  }

  public void testThaiAnalyzerMergesWordsSaraAm() throws IOException {
    assumeTrue("JRE does not support Thai dictionary-based BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
    Analyzer a = new ThaiAnalyzer(CharArraySet.EMPTY_SET);
    // canonical: "drink cold water"
    assertAnalyzesTo(a, "ดื่มน้ำเย็น", new String[] {"ดื่ม", "น้ำ", "เย็น"});
    // "น้ำ" written decomposed (Nikhahit + tone + Sara Aa) with the tone mark doubled
    assertAnalyzesTo(a, "\u0E14\u0E37\u0E48\u0E21\u0E19\u0E4D\u0E49\u0E49\u0E32\u0E40\u0E22\u0E47\u0E19", new String[] {"ดื่ม", "น้ำ", "เย็น"});
    a.close();
  }

  /**
   * The MIGRATE.md (10.5 to 10.6) example: decomposed Sara Am is now analyzed as two words. With the tone mark of
   * the decomposed Sara Am typed twice (repeated tone marks are also listed there), the 10.5-style merge comes back.
   */
  public void testMigrateGuideExampleWithRepeatedTone() throws IOException {
    assumeTrue("JRE does not support Thai dictionary-based BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
    Analyzer a = new ThaiAnalyzer();
    // the documented example: NO NU + NIKHAHIT + MAI THO + SARA AA + "ตาลทราย" -> [น้ำตาล, ทราย]
    assertAnalyzesTo(a, "\u0E19\u0E4D\u0E49\u0E32\u0E15\u0E32\u0E25\u0E17\u0E23\u0E32\u0E22", new String[] {"น้ำตาล", "ทราย"});
    // the same with MAI THO doubled
    assertAnalyzesTo(a, "\u0E19\u0E4D\u0E49\u0E49\u0E32\u0E15\u0E32\u0E25\u0E17\u0E23\u0E32\u0E22", new String[] {"น้ำตาล", "ทราย"});
    a.close();
  }

  /** The MIGRATE.md double Sara E example with a zero-width space between the two Sara E. */
  public void testMigrateGuideExampleWithZeroWidth() throws IOException {
    assumeTrue("JRE does not support Thai dictionary-based BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
    Analyzer a = new ThaiAnalyzer();
    // the documented example: SARA E + SARA E + "มวนอนหลับ" -> [แมว, นอน, หลับ]
    assertAnalyzesTo(a, "\u0E40\u0E40\u0E21\u0E27\u0E19\u0E2D\u0E19\u0E2B\u0E25\u0E31\u0E1A", new String[] {"แมว", "นอน", "หลับ"});
    // the same with ZERO WIDTH SPACE between the two Sara E
    assertAnalyzesTo(a, "\u0E40\u200B\u0E40\u0E21\u0E27\u0E19\u0E2D\u0E19\u0E2B\u0E25\u0E31\u0E1A", new String[] {"แมว", "นอน", "หลับ"});
    a.close();
  }

  /** Search impact: the document is not found by a word it contains. */
  public void testDocumentNotFoundByItsWord() throws IOException {
    assumeTrue("JRE does not support Thai dictionary-based BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
    Analyzer a = new ThaiAnalyzer(CharArraySet.EMPTY_SET);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
      Document d = new Document();
      d.add(new TextField("body", "\u0E09\u0E31\u0E19\u0E17\u0E33\u0E07\u0E32\u0E19\u0E17\u0E48\u0E48\u0E35\u0E1A\u0E49\u0E32\u0E19", Field.Store.NO));
      w.addDocument(d);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        // "บ้าน" (home), analyzed like any query term
        assertEquals(1, s.count(new TermQuery(new Term("body", "บ้าน"))));
      }
    }
    a.close();
  }
}
