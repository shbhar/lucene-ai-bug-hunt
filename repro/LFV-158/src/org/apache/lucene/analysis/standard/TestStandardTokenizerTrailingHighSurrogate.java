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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.index.RandomIndexWriter;

/**
 * A text that ends with an unpaired high surrogate (for example a string cut in the middle of an emoji) is
 * mis-tokenized by StandardAnalyzer with default settings: the JFlex refill looks ahead for the low surrogate,
 * hits the end of the input and reports end of input for the chars it has just read.
 */
public class TestStandardTokenizerTrailingHighSurrogate extends BaseTokenStreamTestCase {

  /** The first half of U+1F600 GRINNING FACE, left over when a string is truncated inside the emoji. */
  private static final String HALF_EMOJI = "\uD83D";

  static List<String> terms(Analyzer a, String text, int[] finalOffset) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      OffsetAttribute off = ts.addAttribute(OffsetAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
      finalOffset[0] = off.endOffset();
    }
    return out;
  }

  /** Control: the same text without the trailing high surrogate. */
  public void testControlWithoutTrailingSurrogate() throws IOException {
    try (StandardAnalyzer a = new StandardAnalyzer()) {
      int[] fo = new int[1];
      assertEquals(List.of("hello", "world"), terms(a, "hello world", fo));
      assertEquals(11, fo[0]);
    }
  }

  /** Short text (fits into one 255-char refill): every token is lost. */
  public void testShortTextKeepsItsTokens() throws IOException {
    try (StandardAnalyzer a = new StandardAnalyzer()) {
      String text = "hello world" + HALF_EMOJI;
      int[] fo = new int[1];
      List<String> got = terms(a, text, fo);
      assertTrue("tokens lost: " + got, got.containsAll(List.of("hello", "world")));
      assertEquals("final offset must be the input length", text.length(), fo[0]);
    }
  }

  /** Long text, last refill at a token start: the tail after the 255-char boundary is lost. */
  public void testTailAfterBufferBoundaryIsKept() throws IOException {
    try (StandardAnalyzer a = new StandardAnalyzer()) {
      String head = "abcd ".repeat(51); // 255 chars, the scanner buffer size; ends with a space
      String text = head + "tail" + HALF_EMOJI;
      int[] fo = new int[1];
      List<String> got = terms(a, text, fo);
      assertEquals(52, terms(a, head + "tail", new int[1]).size()); // control: 51 x abcd + tail
      assertTrue("token 'tail' lost: last=" + got.get(got.size() - 1), got.contains("tail"));
      assertEquals(text.length(), fo[0]);
    }
  }

  /** Long text, last refill inside a word: the word is split into two tokens. */
  public void testWordAcrossBufferBoundaryIsNotSplit() throws IOException {
    try (StandardAnalyzer a = new StandardAnalyzer()) {
      String head = "abcd ".repeat(50); // 250 chars
      String text = head + "helloworld" + HALF_EMOJI; // "helloworld" crosses the 255-char refill boundary
      int[] fo = new int[1];
      List<String> got = terms(a, text, fo);
      assertEquals("helloworld", terms(a, head + "helloworld", new int[1]).get(50)); // control
      assertEquals("tokens after the 50 x abcd: " + got.subList(50, got.size()), "helloworld", got.get(50));
      assertEquals(51, got.size());
    }
  }

  /** End to end: a document whose field value ends with the half emoji cannot be found by its words. */
  public void testDocumentIsSearchable() throws IOException {
    try (Directory dir = newDirectory();
        StandardAnalyzer a = new StandardAnalyzer()) {
      try (RandomIndexWriter w = new RandomIndexWriter(random(), dir, newIndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new TextField("title", "hello world", Field.Store.NO));
        w.addDocument(doc);
        doc = new Document();
        doc.add(new TextField("title", "hello world" + HALF_EMOJI, Field.Store.NO));
        w.addDocument(doc);
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        assertEquals("both documents contain 'hello'", 2, s.count(new TermQuery(new Term("title", "hello"))));
      }
    }
  }
}
