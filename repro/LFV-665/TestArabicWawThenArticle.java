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
package org.apache.lucene.analysis.ar;

import java.io.IOException;
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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;

/**
 * Light10 ("Light Stemming for Arabic Information Retrieval", section 2.2) runs its steps in order:
 * (1) remove the conjunction waw if 3 or more characters remain, (2) remove a definite article if 2
 * or more characters remain, (3) remove suffixes. A word with waw in front of an article other than
 * "wal-" should therefore lose both and stem like the article form.
 */
public class TestArabicWawThenArticle extends BaseTokenStreamTestCase {

  private static void checkStem(String input, String expected) throws IOException {
    MockTokenizer tok = whitespaceMockTokenizer(input);
    assertTokenStreamContents(new ArabicStemFilter(tok), new String[] {expected});
  }

  /** Control: each single prefix is removed (passes today). */
  public void testSinglePrefixControls() throws IOException {
    checkStem("الكتاب", "كتاب"); // al-kitab, "the book"
    checkStem("بالكتاب", "كتاب"); // bi-al-kitab, "with the book"
    checkStem("والكتاب", "كتاب"); // wa-al-kitab: "wal-" is itself in the prefix list
    checkStem("وكتاب", "كتاب"); // wa-kitab, "and a book"
    checkStem("للطلاب", "طلاب"); // li-l-tullab, "for the students"
    checkStem("كالبحر", "بحر"); // ka-al-bahr, "like the sea"
  }

  /** wa + bi-al-: "and with the book". */
  public void testWawBiAl() throws IOException {
    checkStem("وبالكتاب", "كتاب");
  }

  /** wa + li-l-: "and for the students". */
  public void testWawLil() throws IOException {
    checkStem("وللطلاب", "طلاب");
  }

  /** wa + ka-al-: "and like the sea". */
  public void testWawKaAl() throws IOException {
    checkStem("وكالبحر", "بحر");
  }

  /** End to end through the default ArabicAnalyzer: the three forms should conflate. */
  public void testAnalyzerConflation() throws IOException {
    try (Analyzer a = new ArabicAnalyzer()) {
      assertAnalyzesTo(a, "الكتاب بالكتاب وبالكتاب", new String[] {"كتاب", "كتاب", "كتاب"});
    }
  }

  /**
   * User-visible effect: a query for "the book" misses a document that says "and with the book".
   */
  public void testSearchMiss() throws IOException {
    try (Analyzer a = new ArabicAnalyzer();
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document d = new Document();
        d.add(new TextField("body", "وبالكتاب", Field.Store.NO));
        w.addDocument(d);
      }
      String queryTerm;
      try (TokenStream ts = a.tokenStream("body", "الكتاب")) {
        CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
        ts.reset();
        assertTrue(ts.incrementToken());
        queryTerm = t.toString();
        assertFalse(ts.incrementToken());
        ts.end();
      }
      assertEquals("كتاب", queryTerm);
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        assertEquals(
            "a query for 'the book' should match 'and with the book'",
            1,
            s.count(new TermQuery(new Term("body", queryTerm))));
      }
    }
  }
}
