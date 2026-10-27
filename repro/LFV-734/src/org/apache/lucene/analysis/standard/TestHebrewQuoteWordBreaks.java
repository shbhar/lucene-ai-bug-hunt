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
import org.apache.lucene.analysis.email.UAX29URLEmailTokenizer;
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
 * StandardTokenizer and UAX29URLEmailTokenizer implement the UAX #29 word break rules (Unicode 12.1). Rules WB7a
 * (Hebrew_Letter x Single_Quote) and WB7b/WB7c (Hebrew_Letter x Double_Quote Hebrew_Letter) are not applied
 * correctly when a Hebrew letter with an ASCII apostrophe or quotation mark sits next to other word characters.
 *
 * <p>Every expected value below follows from the UAX #29 12.1 rules; ICU4J's word BreakIterator gives the same
 * boundaries. Hebrew letters: U+05D0 ALEF, U+05D1 BET, U+05D4 HE, U+05EA TAV.
 */
public class TestHebrewQuoteWordBreaks extends BaseTokenStreamTestCase {

  private Analyzer standard;
  private Analyzer uax29UrlEmail;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    standard =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new StandardTokenizer(newAttributeFactory());
            return new TokenStreamComponents(t);
          }
        };
    uax29UrlEmail =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new UAX29URLEmailTokenizer(newAttributeFactory());
            return new TokenStreamComponents(t);
          }
        };
  }

  @Override
  public void tearDown() throws Exception {
    standard.close();
    uax29UrlEmail.close();
    super.tearDown();
  }

  /** Checks both tokenizers and reports every one that deviates. */
  private void check(String text, String[] tokens, int[] starts, int[] ends, String[] types) throws Exception {
    StringBuilder failures = new StringBuilder();
    for (Analyzer a : new Analyzer[] {standard, uax29UrlEmail}) {
      String name = a == standard ? "StandardTokenizer" : "UAX29URLEmailTokenizer";
      try {
        assertAnalyzesTo(a, text, tokens, starts, ends, types, null);
      } catch (AssertionError e) {
        failures.append(name).append(": ").append(e.getMessage()).append("; ");
      }
    }
    if (failures.length() > 0) {
      fail(failures.toString());
    }
  }

  /** Controls that already work: the pairs of the official conformance file and a word with two apostrophes. */
  public void testControlsThatPass() throws Exception {
    check("\u05D0'", new String[] {"\u05D0'"}, new int[] {0}, new int[] {2}, new String[] {"<ALPHANUM>"});
    check("\u05D0\"\u05D1", new String[] {"\u05D0\"\u05D1"}, new int[] {0}, new int[] {3},
        new String[] {"<ALPHANUM>"});
    // GIMEL ' VAV RESH GIMEL ' ("George")
    check("\u05D2'\u05D5\u05E8\u05D2'", new String[] {"\u05D2'\u05D5\u05E8\u05D2'"}, new int[] {0},
        new int[] {6}, new String[] {"<ALPHANUM>"});
  }

  /**
   * WB7a joins only the Hebrew letter and the apostrophe; nothing joins the apostrophe with a following digit (WB11
   * needs a digit before it), so there is a break after the apostrophe: BET ' 2 is BET' + 2.
   */
  public void testApostropheThenDigitMustBreak() throws Exception {
    check("\u05D1'2", new String[] {"\u05D1'", "2"}, new int[] {0, 2}, new int[] {2, 3},
        new String[] {"<ALPHANUM>", "<NUM>"});
  }

  /** WB13a needs a letter, digit, Katakana or ExtendNumLet before '_', not an apostrophe: ALEF ' _ a is ALEF' + _a. */
  public void testApostropheThenUnderscoreMustBreak() throws Exception {
    check("\u05D0'_a", new String[] {"\u05D0'", "_a"}, new int[] {0, 2}, new int[] {2, 4},
        new String[] {"<ALPHANUM>", "<ALPHANUM>"});
  }

  /** a . HE ' : WB6/WB7 join a.HE and WB7a joins HE', so the apostrophe belongs to the word (it is dropped). */
  public void testApostropheAfterMidLetterChainMustBeKept() throws Exception {
    check("a.\u05D4'", new String[] {"a.\u05D4'"}, new int[] {0}, new int[] {4}, new String[] {"<ALPHANUM>"});
  }

  /**
   * a . TAV " ALEF : WB6/WB7 join a.TAV and WB7b/WB7c join TAV"ALEF, so this is one word; Lucene splits it into a.TAV
   * and ALEF and drops the quotation mark.
   */
  public void testQuotationMarkAfterMidLetterChainMustNotSplit() throws Exception {
    check("a.\u05EA\"\u05D0", new String[] {"a.\u05EA\"\u05D0"}, new int[] {0}, new int[] {5},
        new String[] {"<ALPHANUM>"});
  }

  /**
   * TAV " ALEF . BET : WB7b/WB7c join TAV"ALEF and WB6/WB7 join ALEF.BET, so this is one word; Lucene splits it into
   * TAV"ALEF and BET and drops the full stop.
   */
  public void testFullStopAfterQuotationMarkWordMustNotSplit() throws Exception {
    check("\u05EA\"\u05D0.\u05D1", new String[] {"\u05EA\"\u05D0.\u05D1"}, new int[] {0}, new int[] {5},
        new String[] {"<ALPHANUM>"});
  }

  /**
   * Search-visible effect with the default StandardAnalyzer: the digit after BET ' is a word of its own (UAX #29),
   * so a TermQuery for it must find the document.
   */
  public void testDigitAfterApostropheIsSearchable() throws Exception {
    try (Directory dir = newDirectory();
        StandardAnalyzer analyzer = new StandardAnalyzer()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new TextField("body", "\u05DB\u05D9\u05EA\u05D4 \u05D1'2", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        assertEquals("hits for body:2", 1, s.count(new TermQuery(new Term("body", "2"))));
      }
    }
  }
}
