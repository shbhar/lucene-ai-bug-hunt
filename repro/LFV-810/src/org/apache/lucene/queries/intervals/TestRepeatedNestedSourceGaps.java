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
 * A nested ordered(a, a) / unordered(a, a) passes its inner gaps on to its parent, unlike a nested
 * ordered(a, b): maxgaps(0, ordered(ordered(a, a), a)) misses 'a x a a' while maxgaps(0,
 * ordered(ordered(a, b), a)) matches 'a x b a' (the shape of upstream
 * TestIntervalQuery#testOrderedWithNoGap). Public API only.
 */
public class TestRepeatedNestedSourceGaps extends LuceneTestCase {

  private static final String FIELD = "f";
  private static final IntervalsSource A = Intervals.term("a");
  private static final IntervalsSource B = Intervals.term("b");
  private static final IntervalsSource C = Intervals.term("c");

  private interface Check {
    void run(IndexReader reader, IndexSearcher searcher) throws IOException;
  }

  private static void withDoc(String text, Check check) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
        Document doc = new Document();
        doc.add(new TextField(FIELD, text, Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        check.run(reader, new IndexSearcher(reader));
      }
    }
  }

  private static int count(IntervalsSource source, String text) throws IOException {
    int[] c = new int[1];
    withDoc(text, (r, s) -> c[0] = s.count(new IntervalQuery(FIELD, source)));
    return c[0];
  }

  /** gaps() of the first interval of source on the single document text. */
  private static int firstGaps(IntervalsSource source, String text) throws IOException {
    int[] g = new int[1];
    withDoc(
        text,
        (r, s) -> {
          IntervalIterator it = source.intervals(FIELD, r.leaves().get(0));
          assertEquals(0, it.nextDoc());
          assertNotEquals(IntervalIterator.NO_MORE_INTERVALS, it.nextInterval());
          g[0] = it.gaps();
        });
    return g[0];
  }

  public void testNestedOrderedRepeat() throws IOException {
    // control (#13819): the inner gap of a nested ordered source is not a gap of the parent
    assertEquals(0, firstGaps(Intervals.ordered(Intervals.ordered(A, B), A), "a x b a"));
    assertEquals(1, count(Intervals.maxgaps(0, Intervals.ordered(Intervals.ordered(A, B), A)), "a x b a"));
    // IntervalIterator#gaps: "does not include the gaps inside those sub-intervals"; main reports 1
    assertEquals(0, firstGaps(Intervals.ordered(Intervals.ordered(A, A), A), "a x a a"));
    // on main: 0 hits
    assertEquals(1, count(Intervals.maxgaps(0, Intervals.ordered(Intervals.ordered(A, A), A)), "a x a a"));
  }

  public void testNestedUnorderedRepeat() throws IOException {
    assertEquals(1, count(Intervals.maxgaps(0, Intervals.ordered(Intervals.unordered(A, C), B)), "a x c b"));
    assertEquals(0, firstGaps(Intervals.ordered(Intervals.unordered(A, A), B), "a x a b"));
    assertEquals(1, count(Intervals.maxgaps(0, Intervals.ordered(Intervals.unordered(A, A), B)), "a x a b"));
  }
}
