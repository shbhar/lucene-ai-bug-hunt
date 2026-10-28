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
package org.apache.lucene.queries.intervals;

import static org.apache.lucene.queries.intervals.Intervals.maxgaps;
import static org.apache.lucene.queries.intervals.Intervals.or;
import static org.apache.lucene.queries.intervals.Intervals.ordered;
import static org.apache.lucene.queries.intervals.Intervals.phrase;
import static org.apache.lucene.queries.intervals.Intervals.term;
import static org.apache.lucene.queries.intervals.Intervals.unordered;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check of the claim: when the same Intervals.or(...) source is passed twice to
 * Intervals.ordered/unordered, the gap-sensitive disjunction rewrite promised by the
 * Intervals.or javadoc ("Automatically rewrites if wrapped by an interval source that is
 * sensitive to internal gaps") does not happen, so a valid gap-free match is missed.
 *
 * <p>Uses only public API (Intervals factories, IntervalQuery, IntervalsSource#intervals).
 */
public class TestRepeatedOrUnderGapSensitiveParent extends LuceneTestCase {

  private static final String F = "f";

  /** X = or(b, "c b"): one single-position and one two-position alternative. */
  private static IntervalsSource x() {
    return or(term("b"), phrase("c", "b"));
  }

  /** The same alternatives plus one that never occurs in the test documents. */
  private static IntervalsSource xWithAbsentAlternative() {
    return or(term("b"), phrase("c", "b"), term("zzz"));
  }

  private int hits(IntervalsSource source, String text) throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        doc.add(new TextField(F, text, Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        int count = s.count(new IntervalQuery(F, source));
        System.out.println(
            "  text='" + text + "' source=" + source + " hits=" + count + " " + dump(r, source));
        return count;
      }
    }
  }

  /** Lists [start,end](gaps) of every interval the source returns in each matching doc. */
  private static String dump(DirectoryReader r, IntervalsSource source) throws IOException {
    List<String> out = new ArrayList<>();
    for (LeafReaderContext ctx : r.leaves()) {
      IntervalIterator it = source.intervals(F, ctx);
      if (it == null) {
        continue;
      }
      while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
        while (it.nextInterval() != IntervalIterator.NO_MORE_INTERVALS) {
          out.add("[" + it.start() + "," + it.end() + "](gaps=" + it.gaps() + ")");
        }
      }
    }
    return "intervals=" + out;
  }

  // ---- The claim: these three fail on the code under test ----

  /**
   * 'c b c b': b at 1 and the phrase 'c b' at [2,3] are two distinct, non-overlapping occurrences
   * of X with no position between them, so unordered(X, X) has a gap-free match [1,3].
   */
  public void testMaxGapsZeroUnorderedSameOrTwice() throws IOException {
    assertEquals(1, hits(maxgaps(0, unordered(x(), x())), "c b c b"));
  }

  /** Same document: b at 1 is followed by the phrase 'c b' at [2,3] with no gap. */
  public void testMaxGapsZeroOrderedSameOrTwice() throws IOException {
    assertEquals(1, hits(maxgaps(0, ordered(x(), x())), "c b c b"));
  }

  /**
   * 'a c b c b': 'a' at 0 is immediately followed by 'c b' [1,2] and 'c b' [3,4], i.e. by an
   * ordered(X, X) interval [1,4] that the phrase rewrite of LUCENE-8477 is meant to expose.
   */
  public void testPhraseThenOrderedSameOrTwice() throws IOException {
    assertEquals(1, hits(phrase(term("a"), ordered(x(), x())), "a c b c b"));
  }

  /**
   * Scope probe: a run of two equal X inside a larger ordered(...) (X, X, d) on 'c b c b d': b
   * [1], 'c b' [2,3] and d [4] follow each other with no gap.
   */
  public void testScopeRunOfEqualSourcesInsideLargerOrdered() throws IOException {
    assertEquals(1, hits(maxgaps(0, ordered(x(), xWithAbsentAlternative(), term("d"))), "c b c b d"));
    assertEquals(1, hits(maxgaps(0, ordered(x(), x(), term("d"))), "c b c b d"));
  }

  // ---- Controls (expected to pass on the code under test) ----

  /**
   * Control 1: an equivalent second source (one extra alternative that never occurs) keeps the
   * two sources unequal, the rewrite runs, and the same documents match.
   */
  public void testControlsEquivalentButUnequalSecondSource() throws IOException {
    assertEquals(1, hits(maxgaps(0, ordered(x(), xWithAbsentAlternative())), "c b c b"));
    assertEquals(1, hits(phrase(term("a"), ordered(x(), xWithAbsentAlternative())), "a c b c b"));
    assertEquals(1, hits(maxgaps(0, unordered(x(), xWithAbsentAlternative())), "c b c b"));
  }

  /**
   * Control 2: ordered(X, X') does not overmatch: it needs two non-overlapping X occurrences
   * (so the ordered control above is a clean oracle, not an artifact of overlap).
   */
  public void testControlsOrderedUnequalDoesNotOvermatch() throws IOException {
    assertEquals(0, hits(ordered(x(), xWithAbsentAlternative()), "b"));
    assertEquals(0, hits(ordered(x(), xWithAbsentAlternative()), "c b"));
    assertEquals(0, hits(maxgaps(0, ordered(x(), xWithAbsentAlternative())), "b x b"));
  }

  /**
   * Control 3: the hand-expanded form of what the documented rewrite produces for ordered(X, X)
   * (every combination of X's alternatives, each wrapped by the gap-sensitive parent) matches.
   */
  public void testControlsHandExpandedRewrite() throws IOException {
    IntervalsSource b = term("b");
    IntervalsSource cb = phrase("c", "b");
    IntervalsSource expandedMaxGaps =
        or(
            maxgaps(0, ordered(b, b)),
            maxgaps(0, ordered(b, cb)),
            maxgaps(0, ordered(cb, b)),
            maxgaps(0, ordered(cb, cb)));
    assertEquals(1, hits(expandedMaxGaps, "c b c b"));
    IntervalsSource expandedPhrase =
        or(
            phrase(term("a"), ordered(b, b)),
            phrase(term("a"), ordered(b, cb)),
            phrase(term("a"), ordered(cb, b)),
            phrase(term("a"), ordered(cb, cb)));
    assertEquals(1, hits(expandedPhrase, "a c b c b"));
  }

  /** Control 4: the failing queries are not dead: they match when the gap-free pair is two b's. */
  public void testControlsRepeatedOrStillMatchesSimpleCase() throws IOException {
    assertEquals(1, hits(maxgaps(0, ordered(x(), x())), "b b"));
    assertEquals(1, hits(maxgaps(0, unordered(x(), x())), "b b"));
    assertEquals(0, hits(maxgaps(0, ordered(x(), x())), "b x b"));
  }
}
