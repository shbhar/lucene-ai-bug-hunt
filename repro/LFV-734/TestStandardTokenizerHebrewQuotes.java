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
package org.apache.lucene.analysis.standard;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
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
 * UAX #29 rules WB7a-WB7c keep an ASCII apostrophe (U+0027) after a Hebrew letter, and an ASCII
 * quotation mark (U+0022) between two Hebrew letters, inside a word. They must combine with the
 * other word-break rules. Every expected value follows from UAX #29; ICU4J's word BreakIterator
 * gives the same boundaries. Letters: U+05D0 ALEF, U+05D1 BET, U+05D2 GIMEL, U+05D4 HE, U+05DC
 * LAMED, U+05E4 PE, U+05E6 TSADI, U+05E9 SHIN, U+05EA TAV.
 */
public class TestStandardTokenizerHebrewQuotes extends BaseTokenStreamTestCase {

  private Analyzer a;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new StandardTokenizer(newAttributeFactory());
            return new TokenStreamComponents(t);
          }
        };
  }

  @Override
  public void tearDown() throws Exception {
    a.close();
    super.tearDown();
  }

  /** Controls: these pass on affected versions too. */
  public void testControls() throws Exception {
    // ALEF ' : WB7a keeps the apostrophe
    assertAnalyzesTo(a, "א'", new String[] {"א'"});
    // TAV SHIN " PE : WB7b/WB7c keep the quotation mark of an acronym
    assertAnalyzesTo(a, "תש\"פ", new String[] {"תש\"פ"});
    // Latin letters: break after the apostrophe, before the digit
    assertAnalyzesTo(a, "abc'3", new String[] {"abc", "3"});
  }

  /** Shape 1: WB7a joins BET and the apostrophe; no rule joins the apostrophe and the digit. */
  public void testApostropheThenDigit() throws Exception {
    assertAnalyzesTo(a, "ב'2", new String[] {"ב'", "2"}, new String[] {"<ALPHANUM>", "<NUM>"});
  }

  /** Shape 2: HE . SHIN ' is one word (WB6/WB7, then WB7a); the apostrophe stays. */
  public void testApostropheAfterMidWordFullStop() throws Exception {
    assertAnalyzesTo(a, "ה.ש'", new String[] {"ה.ש'"});
  }

  /** Shape 3: ALEF . TSADI " LAMED and ALEF " BET " GIMEL are one word each. */
  public void testQuotationMarkAfterMidWordCharacter() throws Exception {
    assertAnalyzesTo(a, "א.צ\"ל", new String[] {"א.צ\"ל"});
    assertAnalyzesTo(a, "א\"ב\"ג", new String[] {"א\"ב\"ג"});
  }

  /** Shape 4: TAV " ALEF . BET is one word (WB7b/WB7c, then WB6/WB7). */
  public void testFullStopAfterAcronym() throws Exception {
    assertAnalyzesTo(a, "ת\"א.ב", new String[] {"ת\"א.ב"});
  }

  /** With the default StandardAnalyzer, the class name BET'2 must not hide the number 2. */
  public void testDigitAfterApostropheIsSearchable() throws Exception {
    try (Directory dir = newDirectory();
        StandardAnalyzer analyzer = new StandardAnalyzer()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc = new Document();
        // KAF YOD TAV HE (class) BET ' 2
        doc.add(new TextField("body", "כיתה ב'2", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        assertEquals("hits for body:2", 1, s.count(new TermQuery(new Term("body", "2"))));
      }
    }
  }
}
