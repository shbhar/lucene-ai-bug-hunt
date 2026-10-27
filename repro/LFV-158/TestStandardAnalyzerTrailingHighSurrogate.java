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

import java.util.Arrays;
import org.apache.lucene.analysis.Analyzer;
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
 * A text value that ends with an unpaired high surrogate, for example a String cut in the middle of
 * an emoji, must keep its tokens and report its full length as the final offset.
 */
public class TestStandardAnalyzerTrailingHighSurrogate extends BaseTokenStreamTestCase {

  /** The first half of U+1F600 (GRINNING FACE), which is "😀" in UTF-16. */
  private static final String HALF_EMOJI = "\uD83D";

  public void testShortValue() throws Exception {
    try (Analyzer a = new StandardAnalyzer()) {
      assertAnalyzesTo(a, "hello world", new String[] {"hello", "world"}); // control
      assertAnalyzesTo(a, "hello world" + HALF_EMOJI, new String[] {"hello", "world"});
    }
  }

  public void testStringCutInsideAnEmoji() throws Exception {
    String text = "hello world 😀 more words".substring(0, 13);
    assertEquals("hello world " + HALF_EMOJI, text);
    try (Analyzer a = new StandardAnalyzer()) {
      assertAnalyzesTo(a, text, new String[] {"hello", "world"});
    }
  }

  public void testDocumentIsFound() throws Exception {
    try (Directory dir = newDirectory();
        Analyzer a = new StandardAnalyzer()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(a))) {
        for (String title : new String[] {"hello world", "hello world" + HALF_EMOJI}) {
          Document doc = new Document();
          doc.add(new TextField("title", title, Field.Store.YES));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(2, newSearcher(r).count(new TermQuery(new Term("title", "hello"))));
      }
    }
  }

  /**
   * 51 x "abcd " is 255 chars, the scanner's buffer size, so "tail" is in the last buffer window.
   */
  public void testLongValueKeepsItsLastWord() throws Exception {
    String[] expected = new String[52];
    Arrays.fill(expected, "abcd");
    expected[51] = "tail";
    try (Analyzer a = new StandardAnalyzer()) {
      assertAnalyzesTo(a, "abcd ".repeat(51) + "tail" + HALF_EMOJI, expected);
    }
  }

  /** "helloworld" starts at char 250, so it crosses the 255-char buffer boundary. */
  public void testLongValueDoesNotSplitAWord() throws Exception {
    String[] expected = new String[51];
    Arrays.fill(expected, "abcd");
    expected[50] = "helloworld";
    try (Analyzer a = new StandardAnalyzer()) {
      assertAnalyzesTo(a, "abcd ".repeat(50) + "helloworld" + HALF_EMOJI, expected);
    }
  }
}
