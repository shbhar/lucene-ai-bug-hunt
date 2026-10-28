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
package verifyv1;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.intervals.IntervalIterator;
import org.apache.lucene.queries.intervals.IntervalQuery;
import org.apache.lucene.queries.intervals.Intervals;
import org.apache.lucene.queries.intervals.IntervalsSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check (public API only): Intervals.ordered / Intervals.unordered with two EQUAL
 * sub-sources that span more than one position (here: phrases). Such sources are deduplicated
 * into a repeating source whose width/gaps arithmetic is under test.
 *
 * <p>Contract used: IntervalIterator#gaps "returns the number of gaps between the immediate
 * sub-intervals of this interval, and does not include the gaps inside those sub-intervals";
 * Intervals#maxgaps filters by that number; Weight#matches returns null iff the document does not
 * match.
 */
public class TestRepeatedSourceGapsV1 extends LuceneTestCase {

  private static final String F = "f";

  private interface Body {
    void run(IndexSearcher searcher, LeafReaderContext leaf) throws IOException;
  }

  /** Index a single document with whitespace tokenization (position i = i-th token). */
  private void withDoc(String text, Body body) throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        doc.add(new TextField(F, text, Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        body.run(new IndexSearcher(r), r.leaves().get(0));
      }
    }
  }

  /** Returns {start, end, gaps, width} of the first interval of doc 0, or null if none. */
  private static int[] firstInterval(IntervalsSource src, LeafReaderContext leaf)
      throws IOException {
    IntervalIterator it = src.intervals(F, leaf);
    if (it == null || it.nextDoc() != 0) {
      return null;
    }
    if (it.nextInterval() == IntervalIterator.NO_MORE_INTERVALS) {
      return null;
    }
    return new int[] {it.start(), it.end(), it.gaps(), it.width()};
  }

  private static int count(IndexSearcher s, IntervalsSource src) throws IOException {
    return s.count(new IntervalQuery(F, src));
  }

  private static IntervalsSource newYork() {
    return Intervals.phrase("new", "york");
  }

  /** Control: equal single-term sub-sources are handled correctly (start == end). */
  public void testControlSingleTermRepeat() throws IOException {
    withDoc(
        "a a",
        (s, leaf) -> {
          IntervalsSource src = Intervals.ordered(Intervals.term("a"), Intervals.term("a"));
          int[] iv = firstInterval(src, leaf);
          assertNotNull(iv);
          assertEquals("start", 0, iv[0]);
          assertEquals("end", 1, iv[1]);
          assertEquals("gaps", 0, iv[2]);
          assertEquals(1, count(s, Intervals.maxgaps(0, src)));
        });
  }

  /** Control: two different but equivalent sub-sources (no deduplication) give 0 gaps. */
  public void testControlEquivalentNonEqualSubSources() throws IOException {
    withDoc(
        "new york new york",
        (s, leaf) -> {
          // maxwidth(2, phrase) never filters a 2-token phrase, but is not equal() to the phrase
          IntervalsSource src = Intervals.ordered(newYork(), Intervals.maxwidth(2, newYork()));
          int[] iv = firstInterval(src, leaf);
          assertNotNull(iv);
          assertEquals("start", 0, iv[0]);
          assertEquals("end", 3, iv[1]);
          assertEquals("gaps", 0, iv[2]);
          assertEquals(1, count(s, Intervals.maxgaps(0, src)));
        });
  }

  /** ordered(P, P) with P a two-token phrase: the interval [0,3] has no gap. */
  public void testOrderedRepeatedPhraseIteratorGaps() throws IOException {
    withDoc(
        "new york new york",
        (s, leaf) -> {
          IntervalsSource src = Intervals.ordered(newYork(), newYork());
          int[] iv = firstInterval(src, leaf);
          assertNotNull(iv);
          assertEquals("start", 0, iv[0]);
          assertEquals("end", 3, iv[1]);
          assertEquals("gaps of ordered(P,P) on [0,1][2,3]", 0, iv[2]);
          assertEquals("width = sum of sub widths 2+2", 4, iv[3]);
        });
  }

  /** Three-token phrase: the sign error makes width() negative. */
  public void testOrderedRepeatedThreeTokenPhraseIteratorGaps() throws IOException {
    withDoc(
        "a b c a b c",
        (s, leaf) -> {
          IntervalsSource p = Intervals.phrase("a", "b", "c");
          IntervalsSource src = Intervals.ordered(p, p);
          int[] iv = firstInterval(src, leaf);
          assertNotNull(iv);
          assertEquals("start", 0, iv[0]);
          assertEquals("end", 5, iv[1]);
          assertEquals("gaps|width=" + iv[2] + "|" + iv[3], 0, iv[2]);
          assertEquals("width = 3+3", 6, iv[3]);
        });
  }

  /** maxgaps(0, ordered(P,P)) must find the doc, like the equivalent non-equal form. */
  public void testMaxGapsOrderedRepeatedPhraseCount() throws IOException {
    withDoc(
        "new york new york",
        (s, leaf) -> {
          int reference =
              count(
                  s,
                  Intervals.maxgaps(
                      0, Intervals.ordered(newYork(), Intervals.maxwidth(2, newYork()))));
          int dedup = count(s, Intervals.maxgaps(0, Intervals.ordered(newYork(), newYork())));
          assertEquals("reference (non-deduplicated) count", 1, reference);
          assertEquals("maxgaps(0, ordered(P,P)) count", reference, dedup);
        });
  }

  /** Same for maxgaps(3): the reported gap count exceeds 3 although the true value is 0. */
  public void testMaxGapsThreeOrderedRepeatedPhraseCount() throws IOException {
    withDoc(
        "new york new york",
        (s, leaf) ->
            assertEquals(1, count(s, Intervals.maxgaps(3, Intervals.ordered(newYork(), newYork())))));
  }

  /** unordered(P, P): same arithmetic, same expectation. */
  public void testMaxGapsUnorderedRepeatedPhraseCount() throws IOException {
    withDoc(
        "new york new york",
        (s, leaf) -> {
          int reference =
              count(
                  s,
                  Intervals.maxgaps(
                      0, Intervals.unordered(newYork(), Intervals.maxwidth(2, newYork()))));
          int dedup = count(s, Intervals.maxgaps(0, Intervals.unordered(newYork(), newYork())));
          assertEquals("reference (non-deduplicated) count", 1, reference);
          assertEquals("maxgaps(0, unordered(P,P)) count", reference, dedup);
        });
  }

  /**
   * The repeated source as ONE child of a larger ordered: ordered(P, P, city). Its width is summed
   * into the parent's gaps (the documented purpose of the summed width), so the sign error leaks.
   */
  public void testMaxGapsRepeatedPhraseInsideLargerOrdered() throws IOException {
    withDoc(
        "new york new york city",
        (s, leaf) -> {
          IntervalsSource src = Intervals.ordered(newYork(), newYork(), Intervals.term("city"));
          int[] iv = firstInterval(src, leaf);
          assertNotNull(iv);
          assertEquals("start", 0, iv[0]);
          assertEquals("end", 4, iv[1]);
          assertEquals("gaps of ordered(P,P,city) on [0,1][2,3][4,4]", 0, iv[2]);
          assertEquals(1, count(s, Intervals.maxgaps(0, src)));
        });
  }

  /** Weight#matches must be null iff the scorer does not match the document. */
  public void testMatchesAgreesWithScorer() throws IOException {
    withDoc(
        "new york new york",
        (s, leaf) -> {
          Query q =
              new IntervalQuery(F, Intervals.maxgaps(0, Intervals.ordered(newYork(), newYork())));
          Weight w = s.createWeight(s.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1f);
          Matches m = w.matches(leaf, 0);
          var sc = w.scorer(leaf);
          // Scorer#iterator() of an IntervalScorer already applies the two-phase match check
          boolean scorerMatches = sc != null && sc.iterator().advance(0) == 0;
          assertTrue(
              "Weight#matches non-null=" + (m != null) + " but scorer matches=" + scorerMatches,
              (m != null) == scorerMatches);
        });
  }
}
