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
import java.util.List;
import java.util.function.Function;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;

/**
 * NGramTokenFilter and EdgeNGramTokenFilter must keep the final position increment that their input
 * reports in end(), such as the trailing hole StopFilter leaves for a removed last word.
 */
public class TestNGramFiltersEndPositionIncrement extends BaseTokenStreamTestCase {

  private static final CharArraySet STOP = new CharArraySet(List.of("the"), false);

  private static TokenStream stop(String text) throws IOException {
    return new StopFilter(whitespaceMockTokenizer(text), STOP);
  }

  /** Consumes the stream; returns its terms followed by the final position increment. */
  private static String termsAndEnd(TokenStream ts) throws IOException {
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    PositionIncrementAttribute posInc = ts.addAttribute(PositionIncrementAttribute.class);
    StringBuilder sb = new StringBuilder();
    ts.reset();
    while (ts.incrementToken()) {
      sb.append(term).append(' ');
    }
    ts.end();
    sb.append("| end=").append(posInc.getPositionIncrement());
    ts.close();
    return sb.toString();
  }

  public void testInputTrailingHoleIsKept() throws IOException {
    // control: StopFilter reports the removed trailing "the" as a final increment of 1
    assertEquals("abc | end=1", termsAndEnd(stop("abc the")));
    // the n-gram filters drop nothing here, so they must report the same final increment
    assertEquals(
        "ngram: abc | end=1, edge: a ab abc | end=1",
        "ngram: "
            + termsAndEnd(new NGramTokenFilter(stop("abc the"), 3, 3, false))
            + ", edge: "
            + termsAndEnd(new EdgeNGramTokenFilter(stop("abc the"), 1, 3, false)));
  }

  public void testOwnAndInputTrailingHolesAreAdded() throws IOException {
    // the n-gram filter drops the too-short "x" itself, StopFilter drops "the": both count
    assertEquals(
        "ngram: abc | end=2, edge: ab abc | end=2",
        "ngram: "
            + termsAndEnd(new NGramTokenFilter(stop("abc x the"), 3, 3, false))
            + ", edge: "
            + termsAndEnd(new EdgeNGramTokenFilter(stop("abc x the"), 2, 3, false)));
  }

  /**
   * Field "f" gets the values "abc the" and "def" with the default position increment gap of 0:
   * "abc" is at position 0 and "def" at position 2, after the hole of the removed "the".
   */
  public void testPhraseAcrossValuesOfMultiValuedField() throws IOException {
    // control: the same chain without an n-gram filter
    assertEquals("adjacent=0 withHole=1", phraseHits(ts -> ts));
    assertEquals(
        "ngram: adjacent=0 withHole=1, edge: adjacent=0 withHole=1",
        "ngram: "
            + phraseHits(ts -> new NGramTokenFilter(ts, 3, 3, false))
            + ", edge: "
            + phraseHits(ts -> new EdgeNGramTokenFilter(ts, 1, 3, false)));
  }

  /** Hits of the phrases "abc def" (adjacent) and "abc _ def" (one hole between them). */
  private String phraseHits(Function<TokenStream, TokenStream> gramFilter) throws IOException {
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new MockTokenizer(MockTokenizer.WHITESPACE, false);
            return new TokenStreamComponents(
                tokenizer, gramFilter.apply(new StopFilter(tokenizer, STOP)));
          }
        };
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new TextField("f", "abc the", Field.Store.NO));
      doc.add(new TextField("f", "def", Field.Store.NO));
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = newSearcher(reader);
        int adjacent = searcher.count(new PhraseQuery("f", "abc", "def"));
        int withHole =
            searcher.count(
                new PhraseQuery.Builder()
                    .add(new Term("f", "abc"), 0)
                    .add(new Term("f", "def"), 2)
                    .build());
        return "adjacent=" + adjacent + " withHole=" + withHole;
      }
    } finally {
      analyzer.close();
    }
  }
}
