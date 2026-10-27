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
package org.apache.lucene.analysis.cn.smart;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * SmartChineseAnalyzer documents that its default stop list "is simply a list of punctuation" and SegTokenFilter
 * that "all punctuation is converted into" ','. Numbers and letters are not punctuation, yet Utility.getCharType
 * puts whole Unicode blocks (0x0021-0x00BB, 0x2010-0x2642, 0x3001-0x301E, 0xFE30-0xFF63) into DELIMITER, so
 * IDEOGRAPHIC NUMBER ZERO (the Chinese numeral zero), Roman numerals, circled digits, superscript digits and
 * a few letters are turned into ',' and then removed.
 */
public class TestNumbersAndLettersNotPunctuation extends LuceneTestCase {

  static List<String> terms(Analyzer a, String text) throws Exception {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      PositionIncrementAttribute posInc = ts.addAttribute(PositionIncrementAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term + (posInc.getPositionIncrement() != 1 ? "(+" + posInc.getPositionIncrement() + ")" : ""));
      }
      ts.end();
    }
    return out;
  }

  /** The year 2008 written with Chinese numerals: the two U+3007 zeros vanish from the token stream. */
  public void testIdeographicNumberZeroIsKept() throws Exception {
    try (Analyzer a = new SmartChineseAnalyzer()) {
      List<String> t = terms(a, "二\u3007\u3007八年");
      assertTrue("U+3007 IDEOGRAPHIC NUMBER ZERO was dropped: " + t, String.join("", t).contains("\u3007"));
    }
  }

  /** Each of these is a letter or a number (Unicode Nl, No, Lo, Ll), not punctuation. */
  public void testNumbersAndLettersAreNotTurnedIntoPunctuation() throws Exception {
    String[] chars = {"\u3007", "\u2162", "\u2460", "\u00B2", "\u00AA", "\u00B5"};
    List<String> dropped = new ArrayList<>();
    try (Analyzer a = new SmartChineseAnalyzer(false)) { // no stop words: shows the ',' placeholder
      for (String c : chars) {
        List<String> t = terms(a, "中" + c + "国");
        if (t.contains(",")) {
          dropped.add(c + " -> " + t);
        }
      }
    }
    assertTrue("non-punctuation characters converted to the punctuation placeholder: " + dropped, dropped.isEmpty());
  }

  /** A phrase search for chapter III also finds chapter IV, because both Roman numerals are removed. */
  public void testRomanNumeralChapterSearch() throws Exception {
    try (Analyzer a = new SmartChineseAnalyzer();
        Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        for (String body : new String[] {"第\u2162章", "第\u2163章"}) {
          Document d = new Document();
          d.add(new TextField("body", body, Field.Store.YES));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Query q = new QueryBuilder(a).createPhraseQuery("body", "第\u2162章");
        int hits = new IndexSearcher(r).count(q);
        assertEquals("query " + q + " should match only the chapter III document", 1, hits);
      }
    }
  }
}
