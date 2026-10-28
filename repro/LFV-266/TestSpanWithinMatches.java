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
package org.apache.lucene.queries.spans;

import java.io.IOException;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.intervals.IntervalQuery;
import org.apache.lucene.queries.intervals.Intervals;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.search.MatchesTestBase;

/**
 * Matches of a SpanWithinQuery must describe the contained (little) span only: its positions, its
 * offsets and its terms. Offsets: ab [0,2) ac [3,5) bc [6,8) zz [9,11).
 */
public class TestSpanWithinMatches extends MatchesTestBase {

  @Override
  protected String[] getDocuments() {
    return new String[] {"ab ac bc", "ab ac bc zz"};
  }

  private static SpanQuery term(String text) {
    return new SpanTermQuery(new Term(FIELD_WITH_OFFSETS, text));
  }

  /** big = "ab bc" with slop 1, in order: matches positions 0..2 in both documents */
  private static SpanQuery big() {
    return new SpanNearQuery(new SpanQuery[] {term("ab"), term("bc")}, 1, true);
  }

  public void testSpanWithin() throws IOException {
    Query q = new SpanWithinQuery(big(), term("ac"));
    // one match per document: "ac", position 1, offsets [3,5)
    checkMatches(q, FIELD_WITH_OFFSETS, new int[][] {{0, 1, 1, 3, 5}, {1, 1, 1, 3, 5}});
    checkTermMatches(
        q,
        FIELD_WITH_OFFSETS,
        new TermMatch[][][] {{{new TermMatch(1, 3, 5)}}, {{new TermMatch(1, 3, 5)}}});
  }

  public void testSpanWithinNestedInSpanNear() throws IOException {
    Query q =
        new SpanNearQuery(
            new SpanQuery[] {new SpanWithinQuery(big(), term("ac")), term("zz")}, 1, true);
    // "ac ... zz": positions 1..3, offsets [3,11), terms ac and zz
    checkMatches(q, FIELD_WITH_OFFSETS, new int[][] {{0}, {1, 1, 3, 3, 11}});
    checkTermMatches(
        q,
        FIELD_WITH_OFFSETS,
        new TermMatch[][][] {{}, {{new TermMatch(1, 3, 5), new TermMatch(3, 9, 11)}}});
  }

  /** Control: a SpanContainingQuery match is the big span, so all three terms belong to it. */
  public void testSpanContainingControl() throws IOException {
    Query q = new SpanContainingQuery(big(), term("ac"));
    checkMatches(q, FIELD_WITH_OFFSETS, new int[][] {{0, 0, 2, 0, 8}, {1, 0, 2, 0, 8}});
    TermMatch[][] all = {{new TermMatch(0, 0, 2), new TermMatch(1, 3, 5), new TermMatch(2, 6, 8)}};
    checkTermMatches(q, FIELD_WITH_OFFSETS, new TermMatch[][][] {all, all});
  }

  /** Control: the intervals equivalent reports only the contained term (fixed in LUCENE-10229). */
  public void testIntervalsContainedByControl() throws IOException {
    Query q =
        new IntervalQuery(
            FIELD_WITH_OFFSETS,
            Intervals.containedBy(
                Intervals.term("ac"),
                Intervals.maxgaps(
                    1, Intervals.ordered(Intervals.term("ab"), Intervals.term("bc")))));
    checkMatches(q, FIELD_WITH_OFFSETS, new int[][] {{0, 1, 1, 3, 5}, {1, 1, 1, 3, 5}});
    checkTermMatches(
        q,
        FIELD_WITH_OFFSETS,
        new TermMatch[][][] {{{new TermMatch(1, 3, 5)}}, {{new TermMatch(1, 3, 5)}}});
  }
}
