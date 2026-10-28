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
package org.apache.lucene.analysis.charfilter;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * HTMLStripCharFilter outputs an unclosed tag-like construct verbatim when any character follows it (upstream
 * TestHTMLStripCharFilter#testMalformedHTML: "&lt;ahref=http:.." strips to itself), but drops it when the input ends
 * right after its name. So the visible word after '&lt;' is lost only at the very end of the input, and appending one
 * space changes the indexed terms.
 */
public class TestHTMLStripUnclosedTagAtEndOfInput extends BaseTokenStreamTestCase {

  private static Analyzer htmlStripAnalyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new StandardTokenizer();
        return new TokenStreamComponents(tokenizer);
      }

      @Override
      protected Reader initReader(String fieldName, Reader reader) {
        return new HTMLStripCharFilter(reader);
      }
    };
  }

  private static String strip(String input) throws IOException {
    try (Reader r = new HTMLStripCharFilter(new StringReader(input))) {
      StringBuilder b = new StringBuilder();
      int c;
      while ((c = r.read()) != -1) {
        b.append((char) c);
      }
      return b.toString();
    }
  }

  /** The same unclosed construct strips the same way whether or not one space follows it. */
  public void testTrailingSpaceDoesNotChangeOutput() throws Exception {
    List<String> failures = new ArrayList<>();
    for (String input : new String[] {"a < b", "a<b", "<ahref", "a </b", "x <p"}) {
      String atEnd = strip(input);
      String withSpace = strip(input + " ");
      if (!withSpace.equals(atEnd + " ")) {
        failures.add("'" + input + "' -> '" + atEnd + "' but '" + input + " ' -> '" + withSpace + "'");
      }
    }
    assertTrue(String.join("; ", failures), failures.isEmpty());
  }

  /** A '<' followed by a space is not a tag in HTML; the word after it must be indexed. */
  public void testWordAfterLessThanAtEndOfInputIsIndexed() throws Exception {
    Analyzer analyzer = htmlStripAnalyzer();
    // mid-text the word is kept ...
    assertAnalyzesTo(analyzer, "a < b c", new String[] {"a", "b", "c"});
    // ... and at the end of the input it must be kept too
    assertAnalyzesTo(analyzer, "a < b", new String[] {"a", "b"});
    analyzer.close();
  }

  /** End to end: a TermQuery for the last word finds the document whether or not it ends in a space. */
  public void testSearchFindsLastWord() throws Exception {
    Analyzer analyzer = htmlStripAnalyzer();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      String[] values = {"price a < b", "price a < b "};
      for (String value : values) {
        Document doc = new Document();
        doc.add(new TextField("body", value, Field.Store.NO));
        w.addDocument(doc);
      }
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        assertEquals(2, s.count(new TermQuery(new Term("body", "price"))));
        assertEquals(
            "both 'price a < b' and 'price a < b ' contain the word b",
            2,
            s.count(new TermQuery(new Term("body", "b"))));
      }
    }
    analyzer.close();
  }
}
