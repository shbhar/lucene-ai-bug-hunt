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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Intervals.ordered(X, X) with two equal sub-sources lets the two copies of X overlap, unlike
 * ordered(X, Y) for an unequal Y with the same intervals. Public API only; each test checks its
 * controls first, so on an affected build only the last assertion of each test fails.
 */
public class TestOrderedRepeatOverlap extends LuceneTestCase {

  private static final String FIELD = "f";
  // a term that occurs in no document: or(S, ABSENT) has exactly the intervals of S
  private static final IntervalsSource ABSENT = Intervals.term("zzz");

  /** Number of hits of the source on a one-document index holding {@code text}. */
  private static int count(IntervalsSource source, String text) throws IOException {
    try (Directory dir = newDirectory()) {
      indexOne(dir, text);
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        return new IndexSearcher(reader).count(new IntervalQuery(FIELD, source));
      }
    }
  }

  /** The intervals the source reports on that document, as "[start,end]" strings. */
  private static List<String> intervals(IntervalsSource source, String text) throws IOException {
    try (Directory dir = newDirectory()) {
      indexOne(dir, text);
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        List<String> out = new ArrayList<>();
        IntervalIterator it = source.intervals(FIELD, reader.leaves().get(0));
        if (it != null && it.nextDoc() == 0) {
          while (it.nextInterval() != IntervalIterator.NO_MORE_INTERVALS) {
            out.add("[" + it.start() + "," + it.end() + "]");
          }
        }
        return out;
      }
    }
  }

  private static void indexOne(Directory dir, String text) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
      Document doc = new Document();
      doc.add(new TextField(FIELD, text, Field.Store.NO));
      w.addDocument(doc);
    }
  }

  /** ordered(phrase(a, a), phrase(a, a)) on "a a a": the two "a a" are [0,1] and [1,2]. */
  public void testRepeatedPhrase() throws IOException {
    IntervalsSource aa = Intervals.phrase("a", "a");
    // controls: the same sub-intervals through an unequal second source, and the flat form
    assertEquals(0, count(Intervals.ordered(aa, Intervals.or(aa, ABSENT)), "a a a"));
    IntervalsSource a = Intervals.term("a");
    assertEquals(0, count(Intervals.ordered(a, a, a, a), "a a a"));
    assertEquals(1, count(Intervals.ordered(aa, aa), "a a a a"));
    // affected builds: 1 (the two copies share position 1)
    assertEquals(0, count(Intervals.ordered(aa, aa), "a a a"));
  }

  /** On a document that does match, the reported interval must be the non-overlapping one. */
  public void testRepeatedPhraseIntervals() throws IOException {
    IntervalsSource aa = Intervals.phrase("a", "a");
    assertEquals(
        List.of("[0,3]"), intervals(Intervals.ordered(aa, Intervals.or(aa, ABSENT)), "a a a a"));
    // affected builds: [[0,2], [1,3]]
    assertEquals(List.of("[0,3]"), intervals(Intervals.ordered(aa, aa), "a a a a"));
  }

  /**
   * unorderedNoOverlaps: "Returns intervals in which both the subsources appear and do not
   * overlap".
   */
  public void testRepeatedPhraseUnorderedNoOverlaps() throws IOException {
    IntervalsSource aa = Intervals.phrase("a", "a");
    assertEquals(0, count(Intervals.unorderedNoOverlaps(aa, Intervals.or(aa, ABSENT)), "a a a"));
    assertEquals(1, count(Intervals.unorderedNoOverlaps(aa, aa), "a a a a"));
    // affected builds: 1
    assertEquals(0, count(Intervals.unorderedNoOverlaps(aa, aa), "a a a"));
  }

  /** ordered(ordered(a, a), ordered(a, a)) needs four a's, like ordered(a, a, a, a). */
  public void testRepeatedNestedOrdered() throws IOException {
    IntervalsSource aa = Intervals.ordered(Intervals.term("a"), Intervals.term("a"));
    assertEquals(0, count(Intervals.ordered(aa, Intervals.or(aa, ABSENT)), "a a a"));
    // affected builds: 1
    assertEquals(0, count(Intervals.ordered(aa, aa), "a a a"));
  }

  /** "a near b, twice in order" needs two b's. */
  public void testRepeatedUnorderedPair() throws IOException {
    IntervalsSource ab = Intervals.unordered(Intervals.term("a"), Intervals.term("b"));
    assertEquals(0, count(Intervals.ordered(ab, Intervals.or(ab, ABSENT)), "a b a"));
    assertEquals(1, count(Intervals.ordered(ab, ab), "a b a b"));
    // affected builds: 1, although the document has a single b
    assertEquals(0, count(Intervals.ordered(ab, ab), "a b a"));
  }
}
