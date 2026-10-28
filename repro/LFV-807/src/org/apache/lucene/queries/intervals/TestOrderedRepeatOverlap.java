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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Intervals.ordered(X, X) with two equal sub-sources lets the two X intervals overlap, unlike
 * ordered(X, Y) for any other Y with the same intervals: ordered(phrase(a, a), phrase(a, a))
 * matches 'a a a', and ordered(unordered(a, b), unordered(a, b)) matches 'a b a', which has only
 * one b. Public API only.
 */
public class TestOrderedRepeatOverlap extends LuceneTestCase {

  private static final String FIELD = "f";
  // a term that occurs in no document: or(S, ABSENT) has exactly the intervals of S
  private static final IntervalsSource ABSENT = Intervals.term("zzz");

  private static int count(IntervalsSource source, String text) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
        Document doc = new Document();
        doc.add(new TextField(FIELD, text, Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        return new IndexSearcher(reader).count(new IntervalQuery(FIELD, source));
      }
    }
  }

  public void testRepeatedPhrase() throws IOException {
    IntervalsSource aa = Intervals.phrase("a", "a");
    // controls: the same sub-intervals through an unequal second source, and the flat form
    assertEquals(0, count(Intervals.ordered(aa, Intervals.or(aa, ABSENT)), "a a a"));
    assertEquals(
        0,
        count(
            Intervals.ordered(
                Intervals.term("a"), Intervals.term("a"), Intervals.term("a"), Intervals.term("a")),
            "a a a"));
    assertEquals(1, count(Intervals.ordered(aa, aa), "a a a a"));
    // on main: 1 (the two "a a" intervals [0,1] and [1,2] share position 1)
    assertEquals(0, count(Intervals.ordered(aa, aa), "a a a"));
  }

  /** unorderedNoOverlaps: "Returns intervals in which both the subsources appear and do not overlap". */
  public void testRepeatedPhraseUnorderedNoOverlaps() throws IOException {
    IntervalsSource aa = Intervals.phrase("a", "a");
    assertEquals(0, count(Intervals.unorderedNoOverlaps(aa, Intervals.or(aa, ABSENT)), "a a a"));
    assertEquals(1, count(Intervals.unorderedNoOverlaps(aa, aa), "a a a a"));
    // on main: 1
    assertEquals(0, count(Intervals.unorderedNoOverlaps(aa, aa), "a a a"));
  }

  public void testRepeatedNestedOrdered() throws IOException {
    IntervalsSource aa = Intervals.ordered(Intervals.term("a"), Intervals.term("a"));
    assertEquals(0, count(Intervals.ordered(aa, Intervals.or(aa, ABSENT)), "a a a"));
    assertEquals(0, count(Intervals.ordered(aa, aa), "a a a"));
  }

  public void testRepeatedUnorderedPair() throws IOException {
    IntervalsSource ab = Intervals.unordered(Intervals.term("a"), Intervals.term("b"));
    assertEquals(0, count(Intervals.ordered(ab, Intervals.or(ab, ABSENT)), "a b a"));
    assertEquals(1, count(Intervals.ordered(ab, ab), "a b a b"));
    // on main: 1, although the document has a single b
    assertEquals(0, count(Intervals.ordered(ab, ab), "a b a"));
  }
}
