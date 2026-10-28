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
package verify.wdgf;

import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.GENERATE_WORD_PARTS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.SPLIT_ON_NUMERICS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.STEM_ENGLISH_POSSESSIVE;

import java.io.IOException;
import java.util.Arrays;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;

/**
 * Independent check: WordDelimiterGraphFilter removes a token that consists only of delimiters
 * (or whose parts are all disabled by the flags) and leaves a hole for it when another token
 * follows. When the removed token is the last one, the hole should be reported through the final
 * position increment in end(), as StopFilter does (LUCENE-3849). Uses public API only.
 */
public class TestWdgfFinalPosIncV1 extends BaseTokenStreamTestCase {

  private static final int FLAGS =
      GENERATE_WORD_PARTS
          | GENERATE_NUMBER_PARTS
          | SPLIT_ON_CASE_CHANGE
          | SPLIT_ON_NUMERICS
          | STEM_ENGLISH_POSSESSIVE;

  private static final CharArraySet STOP = new CharArraySet(Arrays.asList("the"), false);

  private static Analyzer wdgf(int flags, boolean stopFirst) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tok = new MockTokenizer(MockTokenizer.WHITESPACE, false);
        TokenStream ts = tok;
        if (stopFirst) {
          ts = new StopFilter(ts, STOP);
        }
        return new TokenStreamComponents(tok, new WordDelimiterGraphFilter(ts, flags, null));
      }
    };
  }

  private static Analyzer stopOnly() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tok = new MockTokenizer(MockTokenizer.WHITESPACE, false);
        return new TokenStreamComponents(tok, new StopFilter(tok, STOP));
      }
    };
  }

  /** Asserts terms, position increments and the final position increment. */
  private static void check(Analyzer a, String text, String[] terms, int[] posIncs, int finalPosInc)
      throws IOException {
    try (TokenStream ts = a.tokenStream("f", text)) {
      assertTokenStreamContents(
          ts, terms, null, null, null, posIncs, null, null, finalPosInc, null, true, null);
    }
  }

  // ---- controls (expected to pass) ----

  /** Mid-stream: the delimiter-only token leaves a hole of 1 (matches upstream "LUCENE / SOLR"). */
  public void testControlMidStreamHole() throws Exception {
    try (Analyzer a = wdgf(FLAGS, false)) {
      check(a, "foo -- bar", new String[] {"foo", "bar"}, new int[] {1, 2}, 0);
    }
  }

  /** StopFilter reports a trailing removed token through end(). */
  public void testControlStopFilterTrailingHole() throws Exception {
    try (Analyzer a = stopOnly()) {
      check(a, "foo the", new String[] {"foo"}, new int[] {1}, 1);
    }
  }

  /** Mid-stream: a token whose only parts are disabled by the flags also leaves a hole. */
  public void testControlMidStreamSuppressedParts() throws Exception {
    try (Analyzer a = wdgf(GENERATE_WORD_PARTS, false)) {
      check(a, "foo 12-34 bar", new String[] {"foo", "bar"}, new int[] {1, 2}, 0);
    }
  }

  /** Mid-stream: an incoming hole carried by a removed token is passed on (2 = 1 + StopFilter gap). */
  public void testControlMidStreamIncomingHole() throws Exception {
    try (Analyzer a = wdgf(FLAGS, true)) {
      check(a, "foo the -- bar", new String[] {"foo", "bar"}, new int[] {1, 3}, 0);
    }
  }

  // ---- claim (expected to fail on affected versions) ----

  /** Trailing delimiter-only token: the hole should show up as final position increment 1. */
  public void testTrailingDelimiterOnlyToken() throws Exception {
    try (Analyzer a = wdgf(FLAGS, false)) {
      check(a, "foo --", new String[] {"foo"}, new int[] {1}, 1);
    }
  }

  /** Trailing token whose parts are all disabled by the flags: final position increment 1. */
  public void testTrailingSuppressedPartsToken() throws Exception {
    try (Analyzer a = wdgf(GENERATE_WORD_PARTS, false)) {
      check(a, "foo 12-34", new String[] {"foo"}, new int[] {1}, 1);
    }
  }

  /** Incoming StopFilter hole carried by a trailing removed token: final position increment 2. */
  public void testTrailingIncomingHole() throws Exception {
    try (Analyzer a = wdgf(FLAGS, true)) {
      check(a, "foo the --", new String[] {"foo"}, new int[] {1}, 2);
    }
  }

  // ---- index-level effect: multi-valued field, position increment gap 0 ----

  private static int positionOfBar(Analyzer a, String... values) throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(a))) {
      Document doc = new Document();
      for (String v : values) {
        doc.add(new Field("f", v, TextField.TYPE_NOT_STORED));
      }
      w.addDocument(doc);
      w.forceMerge(1);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReader leaf = getOnlyLeafReader(r);
        PostingsEnum pe = leaf.postings(new Term("f", "bar"), PostingsEnum.POSITIONS);
        assertNotNull(pe);
        assertEquals(0, pe.nextDoc());
        int pos = pe.nextPosition();
        int phraseHits = new IndexSearcher(r).count(new PhraseQuery("f", "foo", "bar"));
        // a phrase match exists iff "bar" is right after "foo" (foo is at position 0)
        assertEquals(pos == 1 ? 1 : 0, phraseHits);
        return pos;
      }
    }
  }

  /** Controls: single value with a mid-stream hole, and StopFilter with a trailing hole. */
  public void testControlIndexPositions() throws Exception {
    try (Analyzer a = wdgf(FLAGS, false)) {
      assertEquals(2, positionOfBar(a, "foo -- bar"));
    }
    try (Analyzer a = stopOnly()) {
      assertEquals(2, positionOfBar(a, "foo the", "bar"));
    }
  }

  /** Claim: "foo --" + "bar" should put "bar" at position 2 (no phrase match), like StopFilter. */
  public void testMultiValuedTrailingHole() throws Exception {
    try (Analyzer a = wdgf(FLAGS, false)) {
      assertEquals(
          "position of bar (1 means the trailing hole was dropped and PhraseQuery foo bar matches)",
          2,
          positionOfBar(a, "foo --", "bar"));
    }
  }
}
