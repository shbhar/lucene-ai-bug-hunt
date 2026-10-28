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
import java.io.StringReader;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;

/** A source with no tokens must not erase the trailing hole of the source before it. */
public class TestConcatenatingTokenStreamEmptySources extends BaseTokenStreamTestCase {

  private static final CharArraySet STOP = new CharArraySet(List.of("the"), false);

  /** "foo" followed by one removed token: final position increment 1. */
  private static TokenStream fooThenHole() {
    return new CannedTokenStream(1, 7, new Token("foo", 0, 3));
  }

  private static TokenStream bar() {
    return new CannedTokenStream(0, 3, new Token("bar", 0, 3));
  }

  /** Control (passes today): the hole after "foo" is added to "bar". */
  public void testHoleIsCarriedToNextSource() throws IOException {
    assertTokenStreamContents(
        new ConcatenatingTokenStream(fooThenHole(), bar()),
        new String[] {"foo", "bar"},
        new int[] {0, 7},
        new int[] {3, 10},
        null,
        new int[] {1, 2},
        null,
        10,
        0,
        null,
        false,
        null);
  }

  /** The same with an empty source in between: "bar" must still get increment 2. */
  public void testEmptySourceInTheMiddleKeepsHole() throws IOException {
    assertTokenStreamContents(
        new ConcatenatingTokenStream(fooThenHole(), new EmptyTokenStream(), bar()),
        new String[] {"foo", "bar"},
        new int[] {0, 7},
        new int[] {3, 10},
        null,
        new int[] {1, 2},
        null,
        10,
        0,
        null,
        false,
        null);
  }

  /** An empty last source: end() must still report the hole after "foo". */
  public void testEmptyLastSourceKeepsFinalHole() throws IOException {
    assertTokenStreamContents(
        new ConcatenatingTokenStream(fooThenHole(), new EmptyTokenStream()),
        new String[] {"foo"},
        new int[] {0},
        new int[] {3},
        null,
        new int[] {1},
        null,
        7,
        1,
        null,
        false,
        null);
  }

  /**
   * The values "alpha the", "" and "beta" with a stop filter on "the": as a multi-valued field
   * "beta" is at position 2, and the concatenation of the same three streams must agree.
   */
  public void testSamePositionsAsMultiValuedField() throws IOException {
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new WhitespaceTokenizer();
            return new TokenStreamComponents(t, new StopFilter(t, STOP));
          }
        };
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc = new Document();
        for (String value : new String[] {"alpha the", "", "beta"}) {
          doc.add(new TextField("multi", value, Field.Store.NO));
        }
        doc.add(
            new TextField(
                "concat", new ConcatenatingTokenStream(ws("alpha the"), ws(""), ws("beta"))));
        doc.add(
            new TextField(
                "concatNoEmpty", new ConcatenatingTokenStream(ws("alpha the"), ws("beta"))));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReader leaf = getOnlyLeafReader(r);
        IndexSearcher s = newSearcher(r);
        int multi = position(leaf, "multi", "beta");
        int concat = position(leaf, "concat", "beta");
        int concatNoEmpty = position(leaf, "concatNoEmpty", "beta");
        int multiHits = s.count(new PhraseQuery("multi", "alpha", "beta"));
        int concatHits = s.count(new PhraseQuery("concat", "alpha", "beta"));
        String msg =
            "position of beta: multi="
                + multi
                + " concat="
                + concat
                + " concatNoEmpty="
                + concatNoEmpty
                + "; hits for the exact phrase \"alpha beta\": multi="
                + multiHits
                + " concat="
                + concatHits;
        assertEquals(msg, 2, multi);
        assertEquals(msg, 2, concatNoEmpty);
        assertEquals(msg, 0, multiHits);
        assertEquals(msg, multi, concat);
        assertEquals(msg, multiHits, concatHits);
      }
    }
  }

  private static TokenStream ws(String text) {
    Tokenizer t = new WhitespaceTokenizer();
    t.setReader(new StringReader(text));
    return new StopFilter(t, STOP);
  }

  private static int position(LeafReader leaf, String field, String term) throws IOException {
    PostingsEnum pe = leaf.postings(new Term(field, term), PostingsEnum.POSITIONS);
    assertNotNull(field + ":" + term, pe);
    assertEquals(0, pe.nextDoc());
    return pe.nextPosition();
  }
}
