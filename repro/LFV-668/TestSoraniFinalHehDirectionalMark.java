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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.util.QueryBuilder;

/**
 * A word-final HEH (U+0647) must become AE (U+06D5) also when an invisible directional mark follows
 * it: SoraniNormalizer removes directional controls, so they must not hide the end of the word.
 */
public class TestSoraniFinalHehDirectionalMark extends BaseTokenStreamTestCase {

  /** KHAH ALEF NOON HEH: "house", with the final vowel typed as HEH. */
  private static final String HOUSE_HEH = "\u062E\u0627\u0646\u0647";

  /** The same word spelled with AE. */
  private static final String HOUSE_AE = "\u062E\u0627\u0646\u06D5";

  /** GAF AE WAW REH AE YEH AE: "is big". */
  private static final String IS_BIG = "\u06AF\u06D5\u0648\u0631\u06D5\u06CC\u06D5";

  private Analyzer filter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    filter =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new KeywordTokenizer();
            return new TokenStreamComponents(tokenizer, new SoraniNormalizationFilter(tokenizer));
          }
        };
  }

  @Override
  public void tearDown() throws Exception {
    filter.close();
    super.tearDown();
  }

  /** Control: a bare word-final HEH becomes AE. */
  public void testControlFinalHeh() throws IOException {
    checkOneTerm(filter, HOUSE_HEH, HOUSE_AE);
  }

  /** Control: HEH + TATWEEL stays HEH, as the upstream vocabulary test expects. */
  public void testControlHehTatweelStaysHeh() throws IOException {
    checkOneTerm(filter, HOUSE_HEH + "\u0640", HOUSE_HEH);
  }

  public void testFinalHehBeforeRightToLeftMark() throws IOException {
    checkOneTerm(filter, HOUSE_HEH + "\u200F", HOUSE_AE);
  }

  public void testFinalHehBeforeLeftToRightMark() throws IOException {
    checkOneTerm(filter, HOUSE_HEH + "\u200E", HOUSE_AE);
  }

  public void testFinalHehBeforeArabicLetterMark() throws IOException {
    checkOneTerm(filter, HOUSE_HEH + "\u061C", HOUSE_AE);
  }

  /** Query side: SoraniAnalyzer.normalize. */
  public void testAnalyzerNormalize() throws IOException {
    try (Analyzer a = new SoraniAnalyzer()) {
      assertEquals(
          a.normalize("body", HOUSE_AE).utf8ToString(),
          a.normalize("body", HOUSE_HEH + "\u200F").utf8ToString());
    }
  }

  /** End to end: a search for the AE spelling finds all three spellings of the word. */
  public void testSearchFindsAllThreeSpellings() throws IOException {
    try (Analyzer a = new SoraniAnalyzer();
        Directory dir = newDirectory()) {
      try (RandomIndexWriter w = new RandomIndexWriter(random(), dir, newIndexWriterConfig(a))) {
        for (String house : new String[] {HOUSE_AE, HOUSE_HEH, HOUSE_HEH + "\u200F"}) {
          Document doc = new Document();
          doc.add(newTextField("body", house + " " + IS_BIG, Field.Store.NO));
          w.addDocument(doc);
        }
        try (IndexReader r = w.getReader()) {
          IndexSearcher s = newSearcher(r);
          assertEquals(3, s.count(new QueryBuilder(a).createBooleanQuery("body", HOUSE_AE)));
        }
      }
    }
  }
}
