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
package org.apache.lucene.analysis.miscellaneous;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * Six entries of the ASCIIFoldingFilter table sit in the output group of the opposite case,
 * although each entry's own comment names its case: U+01E5 and U+01E7 (small g with stroke, small g
 * with caron) fold to 'G', U+A785 (small insular s) to 'S', U+A784 (capital insular s) to 's',
 * U+2C6F (capital turned a) to 'a' and U+A73E (capital reversed c with dot) to 'c'. The case
 * partners of the first two and of the last two sit in the right group; the insular s pair is
 * swapped. U+0297 (stretched c, folds to 'C') is left out: whether that one is intended is an open
 * question.
 */
public class TestASCIIFoldingOppositeCase extends BaseTokenStreamTestCase {

  /** WhitespaceTokenizer, optionally LowerCaseFilter, then ASCIIFoldingFilter. */
  private static Analyzer analyzer(boolean lowerCaseFirst) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new WhitespaceTokenizer();
        TokenStream stream = lowerCaseFirst ? new LowerCaseFilter(tokenizer) : tokenizer;
        return new TokenStreamComponents(tokenizer, new ASCIIFoldingFilter(stream));
      }
    };
  }

  private static List<String> terms(Analyzer analyzer, String text) throws IOException {
    List<String> terms = new ArrayList<>();
    try (TokenStream ts = analyzer.tokenStream("body", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        terms.add(term.toString());
      }
      ts.end();
    }
    return terms;
  }

  /** Without lower-casing, each letter folds to the ASCII letter of its own case. */
  public void testEachLetterKeepsItsCase() throws IOException {
    try (Analyzer a = analyzer(false)) {
      // controls, correct today: ğ U+011F, Ǧ U+01E6, ɐ U+0250, ꜿ U+A73F
      assertEquals(List.of("g", "G", "a", "c"), terms(a, "ğ Ǧ ɐ ꜿ"));
      // ǥ U+01E5, ǧ U+01E7, ꞅ U+A785, Ꞅ U+A784, Ɐ U+2C6F, Ꜿ U+A73E
      assertEquals(List.of("g", "g", "s", "S", "A", "C"), terms(a, "ǥ ǧ ꞅ Ꞅ Ɐ Ꜿ"));
    }
  }

  /** LowerCaseFilter, then ASCIIFoldingFilter: lower-case input gives lower-case ASCII terms. */
  public void testLowerCaseThenFold() throws IOException {
    try (Analyzer a = analyzer(true)) {
      // control, correct today: ağa
      assertEquals(List.of("aga"), terms(a, "ağa"));
      // naǧib NAǦIB aǥa ꞅet Ꞅet
      assertEquals(
          List.of("nagib", "nagib", "aga", "set", "set"), terms(a, "naǧib NAǦIB aǥa ꞅet Ꞅet"));
    }
  }

  /** The same chain at index and query time: the query typed in ASCII finds the document. */
  public void testAsciiQueryFindsDocument() throws IOException {
    try (Analyzer a = analyzer(true);
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(a))) {
        for (String text : new String[] {"ağa", "naǧib"}) { // ağa (control), naǧib
          Document doc = new Document();
          doc.add(new TextField("body", text, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        QueryBuilder builder = new QueryBuilder(a);
        assertEquals(1, searcher.count(builder.createBooleanQuery("body", "aga")));
        assertEquals(
            "hits for the query 'nagib' on the document 'naǧib'",
            1,
            searcher.count(builder.createBooleanQuery("body", "nagib")));
      }
    }
  }
}
