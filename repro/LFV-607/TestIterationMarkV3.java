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
package org.apache.lucene.analysis.ja;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
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
import org.apache.lucene.tests.analysis.MockTokenizer;

/** Independent check: unvoiced kana iteration mark after a kana that has no voiced form. */
public class TestIterationMarkV3 extends BaseTokenStreamTestCase {

  private static String viaFactory(String s) throws IOException {
    // Same path the SPI name "japaneseIterationMark" uses, default parameters.
    JapaneseIterationMarkCharFilterFactory f =
        new JapaneseIterationMarkCharFilterFactory(new HashMap<>());
    StringBuilder sb = new StringBuilder();
    try (Reader r = f.create(new StringReader(s))) {
      int c;
      while ((c = r.read()) != -1) {
        sb.append((char) c);
      }
    }
    return sb.toString();
  }

  /** Documented and tested behaviour, must pass on stock code. */
  public void testControls() throws IOException {
    assertEquals("おおの", viaFactory("おゝの")); // upstream testHiragana
    assertEquals("じし", viaFactory("じゝ")); // upstream testHiragana
    assertEquals("ぽぽ", viaFactory("ぽゝ")); // upstream testMalformed
    assertEquals("みすず", viaFactory("みすゞ")); // upstream testHiragana
    assertEquals("なな", viaFactory("なゞ")); // voiced mark after な: no voiced form, kept
  }

  /** ゝ after の must repeat の; a voiced mark after な already keeps な. */
  public void testUnvoicedMarkAfterNaRow() throws IOException {
    assertEquals("ののしる", viaFactory("のゝしる"));
    assertEquals("ナナツ", viaFactory("ナヽツ"));
  }

  /** The unvoiced mark must never yield a voiced kana. */
  public void testUnvoicedMarkNeverAddsDakuten() throws IOException {
    assertEquals("ぱぱ", viaFactory("ぱゝ"));
  }

  /** End to end: a document written with the mark is not found by the expanded spelling. */
  public void testSearchMiss() throws IOException {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new MockTokenizer(MockTokenizer.KEYWORD, false);
            return new TokenStreamComponents(t, t);
          }

          @Override
          protected Reader initReader(String fieldName, Reader reader) {
            return new JapaneseIterationMarkCharFilter(reader);
          }
        };
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document d = new Document();
        d.add(new TextField("f", "なゝめ", Field.Store.NO));
        w.addDocument(d);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        int expanded = s.count(new TermQuery(new Term("f", "ななめ")));
        int wrong = s.count(new TermQuery(new Term("f", "などめ")));
        assertEquals("hits for expanded form ななめ (hits for などめ: " + wrong + ")", 1, expanded);
      }
    }
    a.close();
  }
}
