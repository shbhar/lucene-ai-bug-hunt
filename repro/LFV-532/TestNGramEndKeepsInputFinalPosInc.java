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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: NGramTokenFilter / EdgeNGramTokenFilter end() must not throw away the final
 * position increment reported by their input (for example the trailing hole a StopFilter leaves).
 * Uses public API only.
 */
public class TestNGramEndKeepsInputFinalPosInc extends LuceneTestCase {

  private static final CharArraySet STOP = new CharArraySet(Arrays.asList("the"), false);

  /** whitespace tokens -> StopFilter("the") -> optional n-gram filter with grams of exactly 3 chars */
  private static TokenStream chain(String text, String gramFilter) {
    Tokenizer tok = new MockTokenizer(MockTokenizer.WHITESPACE, false);
    tok.setReader(new StringReader(text));
    TokenStream ts = new StopFilter(tok, STOP);
    switch (gramFilter) {
      case "none":
        return ts;
      case "ngram":
        return new NGramTokenFilter(ts, 3, 3, false);
      case "edge":
        return new EdgeNGramTokenFilter(ts, 3, 3, false);
      default:
        throw new IllegalArgumentException(gramFilter);
    }
  }

  /** Consumes the stream with the normal workflow; returns "term/posInc ... | end=finalPosInc". */
  private static String consume(TokenStream ts) throws IOException {
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    PositionIncrementAttribute inc = ts.addAttribute(PositionIncrementAttribute.class);
    List<String> out = new ArrayList<>();
    ts.reset();
    while (ts.incrementToken()) {
      out.add(term.toString() + "/" + inc.getPositionIncrement());
    }
    ts.end();
    out.add("| end=" + inc.getPositionIncrement());
    ts.close();
    return String.join(" ", out);
  }

  /** Control: without an n-gram filter the trailing stop word is reported as final increment 1. */
  public void testControlStopFilterAloneReportsTrailingHole() throws IOException {
    assertEquals("abc/1 | end=1", consume(chain("abc the", "none")));
  }

  /** Control: a hole in the middle of the stream survives the n-gram filters. */
  public void testControlMidStreamHoleIsKept() throws IOException {
    assertEquals("abc/1 def/2 | end=0", consume(chain("abc the def", "none")));
    assertEquals("abc/1 def/2 | end=0", consume(chain("abc the def", "ngram")));
    assertEquals("abc/1 def/2 | end=0", consume(chain("abc the def", "edge")));
  }

  /** With 3-char tokens and grams of size 3 the filter emits exactly its input; end() must too. */
  public void testNGramTokenFilterKeepsTrailingHole() throws IOException {
    assertEquals("abc/1 | end=1", consume(chain("abc the", "ngram")));
  }

  public void testEdgeNGramTokenFilterKeepsTrailingHole() throws IOException {
    assertEquals("abc/1 | end=1", consume(chain("abc the", "edge")));
  }

  /**
   * The filter drops the too-short token "x" itself (its own hole, the LUCENE-8360 case) and the
   * StopFilter drops "the" (input's hole). Both must be in the final increment: abc@0, x@1, the@2,
   * so the stream ends 2 positions after "abc".
   */
  public void testOwnDroppedTokenPlusInputHoleAreBothCounted() throws IOException {
    assertEquals("abc/1 | end=2", consume(chain("abc x the", "ngram")));
    assertEquals("abc/1 | end=2", consume(chain("abc x the", "edge")));
  }

  /**
   * IndexWriter adds the end() increment to the field position, so the next value of a
   * multi-valued field is shifted. Field "plain" uses the chain without the n-gram filter, field
   * "ngram" adds NGramTokenFilter(3,3); both index the same values, and every token is 3 chars, so
   * the indexed positions must be the same.
   */
  public void testMultiValuedFieldPositions() throws IOException {
    // per-field reuse: the default global strategy would reuse the first field's chain for all
    Analyzer analyzer =
        new Analyzer(Analyzer.PER_FIELD_REUSE_STRATEGY) {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tok = new MockTokenizer(MockTokenizer.WHITESPACE, false);
            TokenStream ts = new StopFilter(tok, STOP);
            if (fieldName.equals("ngram")) {
              ts = new NGramTokenFilter(ts, 3, 3, false);
            } else if (fieldName.equals("edge")) {
              ts = new EdgeNGramTokenFilter(ts, 3, 3, false);
            }
            return new TokenStreamComponents(tok, ts);
          }
        };
    // sanity: the analyzer really builds the per-field chains (tokens equal, end() differs)
    assertEquals("abc/1 | end=1", consume(analyzer.tokenStream("plain", "abc the")));
    TokenStream ngramTs = analyzer.tokenStream("ngram", "abc");
    assertTrue(ngramTs instanceof NGramTokenFilter);
    assertEquals("abc/1 | end=0", consume(ngramTs));
    TokenStream edgeTs = analyzer.tokenStream("edge", "abc");
    assertTrue(edgeTs instanceof EdgeNGramTokenFilter);
    assertEquals("abc/1 | end=0", consume(edgeTs));
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc = new Document();
        for (String f : new String[] {"plain", "ngram", "edge"}) {
          doc.add(new Field(f, "abc the", TextField.TYPE_NOT_STORED));
          doc.add(new Field(f, "def", TextField.TYPE_NOT_STORED));
        }
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        LeafReader leaf = r.leaves().get(0).reader();
        int plain = positionOf(leaf, "plain", "def");
        assertEquals("control: position of def without n-gram filter", 2, plain);

        IndexSearcher searcher = newSearcher(r);
        int plainPhrase = searcher.count(new PhraseQuery("plain", "abc", "def"));
        assertEquals("control: phrase abc def must not match across the stop-word hole", 0, plainPhrase);

        String actual =
            "ngram def@"
                + positionOf(leaf, "ngram", "def")
                + " phrase="
                + searcher.count(new PhraseQuery("ngram", "abc", "def"))
                + "; edge def@"
                + positionOf(leaf, "edge", "def")
                + " phrase="
                + searcher.count(new PhraseQuery("edge", "abc", "def"));
        assertEquals("ngram def@2 phrase=0; edge def@2 phrase=0", actual);
      }
    }
    analyzer.close();
  }

  private static int positionOf(LeafReader leaf, String field, String text) throws IOException {
    PostingsEnum pe = leaf.postings(new Term(field, text), PostingsEnum.POSITIONS);
    assertNotNull(field + ":" + text, pe);
    assertNotEquals(DocIdSetIterator.NO_MORE_DOCS, pe.nextDoc());
    assertEquals(1, pe.freq());
    return pe.nextPosition();
  }
}
