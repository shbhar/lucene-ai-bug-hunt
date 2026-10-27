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
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/** ThaiAnalyzer drops a number that is glued to a leading '.', '#' or currency sign. */
public class TestThaiNumberAfterPrefix extends BaseTokenStreamTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    assumeTrue("needs the JRE's Thai BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
  }

  private static List<String> terms(String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (Analyzer a = new ThaiAnalyzer();
        TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  /** Controls: without the prefix character (or with a space) the number is kept. */
  public void testControls() throws IOException {
    assertEquals(List.of("ราคา", "1,500", "บาท"), terms("ราคา 1,500 บาท")); // price 1,500 baht
    assertEquals(List.of("พ", "ศ", "2567"), terms("พ.ศ. 2567")); // Buddhist Era 2567
  }

  /** A price written with the baht sign. */
  public void testPriceAfterBahtSign() throws IOException {
    assertEquals(List.of("ราคา", "1,500"), terms("ราคา ฿1,500"));
  }

  /** A year written right after the era abbreviation, without a space. */
  public void testYearAfterEraAbbreviation() throws IOException {
    assertEquals(List.of("พ", "ศ", "2567"), terms("พ.ศ.2567"));
  }

  /** End to end: a document dated "1 ม.ค.2567" (1 January 2567) is not found by its year. */
  public void testSearchByYear() throws IOException {
    try (Analyzer a = new ThaiAnalyzer();
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new TextField("f", "ประชุมวันที่ 1 ม.ค.2567", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, newSearcher(r).count(new TermQuery(new Term("f", "2567"))));
      }
    }
  }
}
