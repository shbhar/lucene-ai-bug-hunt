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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
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
 * ThaiTokenizer skips every word-BreakIterator segment whose FIRST character is not a letter or
 * digit. The JDK word BreakIterator returns a number written directly after a period as one
 * segment with a leading period (".2567"), so the whole number is dropped. Thai dates are commonly
 * written this way: "พ.ศ.2567" (Buddhist Era 2567), "ม.ค.2567" (January 2567).
 */
public class TestThaiTokenizerNumberAfterPeriod extends BaseTokenStreamTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    assumeTrue("needs the Thai BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
  }

  private static List<String> terms(Analyzer a, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) out.add(t.toString());
      ts.end();
    }
    return out;
  }

  /** Control: with a space after the abbreviation the year is a token. */
  public void testControlWithSpace() throws IOException {
    try (Analyzer a = new ThaiAnalyzer()) {
      assertTrue(terms(a, "พ.ศ. 2567").contains("2567"));
    }
  }

  /** ThaiAnalyzer as shipped: the Buddhist Era year after "พ.ศ." must be a token. */
  public void testThaiAnalyzerBuddhistEraYear() throws IOException {
    try (Analyzer a = new ThaiAnalyzer()) {
      List<String> terms = terms(a, "พ.ศ.2567");
      assertTrue("terms=" + terms, terms.contains("2567"));
    }
  }

  /** The plain tokenizer drops the digits after any period, Latin text included. */
  public void testThaiTokenizerDigitsAfterPeriod() throws IOException {
    Tokenizer t = new ThaiTokenizer();
    t.setReader(new StringReader("abc.123"));
    assertTokenStreamContents(t, new String[] {"abc", "123"});
  }

  /** Index level: a document dated "1 ม.ค.2567" is not found by the year. */
  public void testSearchByYear() throws IOException {
    try (Analyzer a = new ThaiAnalyzer();
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new TextField("f", "ประชุมวันที่ 1 ม.ค.2567", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        assertEquals(1, s.count(new TermQuery(new Term("f", "2567"))));
      }
    }
  }
}
