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
package org.apache.lucene.analysis.ngram;

import java.io.IOException;
import java.io.StringReader;
import java.util.function.Function;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;

/**
 * NGramTokenFilter and EdgeNGramTokenFilter overwrite the final position increment that their
 * input reports in end() (for example the hole StopFilter records for a removed trailing stop
 * word) with the increment of the tokens they dropped themselves, so the hole is lost.
 */
public class TestNGramFiltersFinalPositionIncrement extends BaseTokenStreamTestCase {

  private static final CharArraySet STOP = new CharArraySet(java.util.List.of("the"), false);

  private static TokenStream stop(String text) {
    Tokenizer tok = new MockTokenizer(MockTokenizer.WHITESPACE, false);
    tok.setReader(new StringReader(text));
    return new StopFilter(tok, STOP);
  }

  private static void assertFinalPosInc(TokenStream ts, String[] terms, int[] posIncs, int finalPosInc)
      throws IOException {
    assertTokenStreamContents(
        ts, terms, null, null, null, posIncs, null, 7, finalPosInc, null, true, null, null);
  }

  /** Control: StopFilter reports the removed trailing "the" as final position increment 1. */
  public void testStopFilterAloneReportsTrailingHole() throws IOException {
    assertFinalPosInc(stop("abc the"), new String[] {"abc"}, new int[] {1}, 1);
  }

  /** A filter that drops nothing must keep its input's final position increment. */
  public void testNGramTokenFilterKeepsTrailingHole() throws IOException {
    assertFinalPosInc(
        new NGramTokenFilter(stop("abc the"), 3, 3, false), new String[] {"abc"}, new int[] {1}, 1);
  }

  public void testEdgeNGramTokenFilterKeepsTrailingHole() throws IOException {
    assertFinalPosInc(
        new EdgeNGramTokenFilter(stop("abc the"), 1, 3, false),
        new String[] {"a", "ab", "abc"},
        new int[] {1, 0, 0},
        1);
  }

  /**
   * Index-level effect on a multi-valued field: the second value starts one position earlier
   * with the n-gram filter than without it, although the filter emits that value's tokens
   * unchanged ("def" is its own 3-gram) and is documented to keep every gram at its token's
   * position.
   */
  public void testSecondValueOfMultiValuedFieldKeepsItsPosition() throws IOException {
    int stopOnly = positionOfSecondValue(ts -> ts);
    int withEdgeNGrams = positionOfSecondValue(ts -> new EdgeNGramTokenFilter(ts, 1, 3, false));
    int withNGrams = positionOfSecondValue(ts -> new NGramTokenFilter(ts, 3, 3, false));
    assertEquals("control: StopFilter only", 2, stopOnly);
    assertEquals("EdgeNGramTokenFilter", stopOnly, withEdgeNGrams);
    assertEquals("NGramTokenFilter", stopOnly, withNGrams);
  }

  private int positionOfSecondValue(Function<TokenStream, TokenStream> filter) throws IOException {
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tok = new MockTokenizer(MockTokenizer.WHITESPACE, false);
            return new TokenStreamComponents(tok, filter.apply(new StopFilter(tok, STOP)));
          }
        };
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new TextField("f", "abc the", Field.Store.NO));
      doc.add(new TextField("f", "def", Field.Store.NO));
      w.addDocument(doc);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReader leaf = getOnlyLeafReader(r);
        PostingsEnum pe = leaf.postings(new Term("f", "def"), PostingsEnum.POSITIONS);
        assertNotNull(pe);
        assertTrue(pe.nextDoc() != DocIdSetIterator.NO_MORE_DOCS);
        assertEquals(1, pe.freq());
        return pe.nextPosition();
      }
    } finally {
      analyzer.close();
    }
  }
}
