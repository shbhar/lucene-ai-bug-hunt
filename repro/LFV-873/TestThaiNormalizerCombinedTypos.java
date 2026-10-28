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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * ThaiNormalizer recomposes a decomposed Sara Am (Nikhahit U+0E4D + optional tone mark + Sara Aa
 * U+0E32), but only if no other typo sits inside it: a Lakkhangyao instead of Sara Aa, or a
 * repeated or extra tone mark, is repaired after the recomposition test and leaves the decomposed
 * form in the output.
 */
public class TestThaiNormalizerCombinedTypos extends BaseTokenStreamTestCase {

  /** น้ำ ("water"): No Nu, Mai Tho, Sara Am. */
  private static final String WATER = "\u0E19\u0E49\u0E33";

  /** น + Nikhahit + Mai Ek + Mai Tho + Sara Aa: a wrong tone mark, then the right one. */
  private static final String WATER_TWO_TONES = "\u0E19\u0E4D\u0E48\u0E49\u0E32";

  private static String normalize(String s) {
    char[] buf = s.toCharArray();
    return new String(buf, 0, new ThaiNormalizer().normalize(buf, buf.length));
  }

  private static void checkFilter(String input, String expected) throws IOException {
    Tokenizer tokenizer = whitespaceMockTokenizer(input);
    assertTokenStreamContents(new ThaiNormalizationFilter(tokenizer), new String[] {expected});
  }

  /** The same five characters in another order are normalized; this order is not. */
  public void testSameMarksInEitherOrder() {
    assertEquals(WATER, normalize("\u0E19\u0E48\u0E4D\u0E49\u0E32")); // Mai Ek before Nikhahit
    assertEquals(WATER, normalize(WATER_TWO_TONES)); // Mai Ek after Nikhahit
  }

  /** ThaiNormalizationFilter without ThaiCharFilter, after a whitespace tokenizer. */
  public void testSecondTypoInsideDecomposedSaraAm() throws IOException {
    checkFilter("\u0E17\u0E4D\u0E32", "\u0E17\u0E33"); // control: Tho Thahan + Nikhahit + Sara Aa
    checkFilter("\u0E19\u0E4D\u0E49\u0E32", WATER); // control: No Nu + Nikhahit + Mai Tho + Sara Aa
    checkFilter("\u0E17\u0E4D\u0E45", "\u0E17\u0E33"); // Lakkhangyao instead of Sara Aa
    checkFilter("\u0E19\u0E4D\u0E49\u0E49\u0E32", WATER); // Mai Tho typed twice
    checkFilter(WATER_TWO_TONES, WATER); // Mai Ek, then Mai Tho
  }

  /** Default ThaiAnalyzer: a TermQuery for the clean word should find both documents. */
  public void testThaiAnalyzerTermQuery() throws IOException {
    assumeTrue(
        "JRE does not support Thai dictionary-based BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
    try (Analyzer analyzer = new ThaiAnalyzer();
        Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        for (String water : new String[] {WATER, WATER_TWO_TONES}) {
          Document doc = new Document();
          // "drink water cold", space separated
          String text = "\u0E14\u0E37\u0E48\u0E21 " + water + " \u0E40\u0E22\u0E47\u0E19";
          doc.add(new TextField("body", text, Field.Store.NO));
          writer.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        int hits = newSearcher(reader).count(new TermQuery(new Term("body", WATER)));
        assertEquals("documents found by a TermQuery for " + WATER, 2, hits);
      }
    }
  }

  /** Analyzer.normalize, which query parsers use for prefix, wildcard and fuzzy terms. */
  public void testThaiAnalyzerNormalize() throws IOException {
    try (Analyzer analyzer = new ThaiAnalyzer()) {
      assertEquals(WATER, analyzer.normalize("body", WATER_TWO_TONES).utf8ToString());
    }
  }
}
