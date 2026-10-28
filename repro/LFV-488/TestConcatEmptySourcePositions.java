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
package verify.concat;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.ConcatenatingTokenStream;
import org.apache.lucene.analysis.miscellaneous.EmptyTokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.Document;
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
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: a source with no tokens inside a ConcatenatingTokenStream must not erase the
 * trailing position increment (a hole, e.g. a removed stop word) of the source before it.
 *
 * <p>Uses public API only (test is outside the org.apache.lucene packages).
 */
public class TestConcatEmptySourcePositions extends LuceneTestCase {

  /** Consumed view of a token stream: terms, position increments, end() position increment. */
  private static final class Consumed {
    final List<String> terms = new ArrayList<>();
    final List<Integer> posIncs = new ArrayList<>();
    int finalPosInc;

    @Override
    public String toString() {
      return "terms=" + terms + " posIncs=" + posIncs + " finalPosInc=" + finalPosInc;
    }
  }

  private static Consumed consume(TokenStream ts) throws IOException {
    Consumed c = new Consumed();
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    PositionIncrementAttribute posInc = ts.addAttribute(PositionIncrementAttribute.class);
    ts.reset();
    while (ts.incrementToken()) {
      c.terms.add(term.toString());
      c.posIncs.add(posInc.getPositionIncrement());
    }
    ts.end();
    c.finalPosInc = posInc.getPositionIncrement();
    ts.close();
    return c;
  }

  private static Token tok(String text, int start, int end) {
    return new Token(text, start, end);
  }

  // ---- controls (expected to pass on every version) ----

  /** Control: without an empty source the trailing hole of "foo" is added to "bar" (LUCENE-8650). */
  public void testControlHoleIsPropagatedWithoutEmptySource() throws IOException {
    Consumed c =
        consume(
            new ConcatenatingTokenStream(
                new CannedTokenStream(1, 4, tok("foo", 0, 3)),
                new CannedTokenStream(0, 3, tok("bar", 0, 3))));
    assertEquals(c.toString(), List.of("foo", "bar"), c.terms);
    assertEquals(c.toString(), List.of(1, 2), c.posIncs);
  }

  /** Control: a single source ending with a hole reports it from end(). */
  public void testControlFinalHoleOfLastSource() throws IOException {
    Consumed c =
        consume(new ConcatenatingTokenStream(new CannedTokenStream(1, 4, tok("foo", 0, 3))));
    assertEquals(c.toString(), 1, c.finalPosInc);
  }

  // ---- the claim ----

  /** An empty source (EmptyTokenStream, as in upstream testBasic) between "foo"+hole and "bar". */
  public void testEmptyTokenStreamInMiddleKeepsPendingHole() throws IOException {
    Consumed c =
        consume(
            new ConcatenatingTokenStream(
                new CannedTokenStream(1, 4, tok("foo", 0, 3)),
                new EmptyTokenStream(),
                new CannedTokenStream(0, 3, tok("bar", 0, 3))));
    // Same as the control: an empty source contributes no positions.
    assertEquals(c.toString(), List.of(1, 2), c.posIncs);
  }

  /** A source that only had a removed token (final posInc 1, no tokens) in the middle. */
  public void testStopwordOnlyMiddleSourceAccumulatesHoles() throws IOException {
    Consumed c =
        consume(
            new ConcatenatingTokenStream(
                new CannedTokenStream(1, 4, tok("foo", 0, 3)),
                new CannedTokenStream(1, 3),
                new CannedTokenStream(0, 3, tok("bar", 0, 3))));
    // one hole after foo + one hole from the middle source + bar's own increment 1
    assertEquals(c.toString(), List.of(1, 3), c.posIncs);
  }

  /** An empty last source must not erase the trailing hole of the source before it. */
  public void testEmptyLastSourceKeepsFinalHole() throws IOException {
    Consumed c =
        consume(
            new ConcatenatingTokenStream(
                new CannedTokenStream(1, 4, tok("foo", 0, 3)), new CannedTokenStream(0, 0)));
    assertEquals(c.toString(), 1, c.finalPosInc);
  }

  // ---- end to end through the index, with a real analysis chain ----

  private static TokenStream stopped(String text) {
    Tokenizer t = new WhitespaceTokenizer();
    t.setReader(new StringReader(text));
    return new StopFilter(t, new CharArraySet(List.of("the"), false));
  }

  private static int positionOf(LeafReader leaf, String field, String term) throws IOException {
    PostingsEnum pe = leaf.postings(new Term(field, term), PostingsEnum.POSITIONS);
    assertNotNull(field + ":" + term, pe);
    assertTrue(pe.nextDoc() != DocIdSetIterator.NO_MORE_DOCS);
    return pe.nextPosition();
  }

  /**
   * Values "alpha the", "" and "beta" concatenated must index like the single value "alpha the
   * beta": beta at position 2, so the exact phrase "alpha beta" does not match.
   */
  public void testIndexedPositionsMatchSingleValue() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new TextField("single", stopped("alpha the beta")));
        doc.add(
            new TextField(
                "concat",
                new ConcatenatingTokenStream(
                    stopped("alpha the"), stopped(""), stopped("beta"))));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReader leaf = getOnlyLeafReader(r);
        int singleBeta = positionOf(leaf, "single", "beta");
        int concatBeta = positionOf(leaf, "concat", "beta");
        IndexSearcher s = newSearcher(r);
        int singleHits = s.count(new PhraseQuery("single", "alpha", "beta"));
        int concatHits = s.count(new PhraseQuery("concat", "alpha", "beta"));
        String msg =
            "beta position single="
                + singleBeta
                + " concat="
                + concatBeta
                + "; exact phrase 'alpha beta' hits single="
                + singleHits
                + " concat="
                + concatHits;
        assertEquals(msg, 2, singleBeta); // sanity: the single-value control
        assertEquals(msg, 0, singleHits); // sanity
        assertEquals(msg, singleBeta, concatBeta);
        assertEquals(msg, singleHits, concatHits);
      }
    }
  }
}
