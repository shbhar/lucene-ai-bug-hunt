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
 * Intervals.ordered(a, a) and Intervals.unordered(a, a) report minExtent() 1 although every
 * interval they return covers two positions, so the disjunction rewrite treats them as
 * single-position clauses and phrase(x, or(ordered(a, a), a), c) misses 'x a a c'. Public API only.
 */
public class TestRepeatedSourceMinExtent extends LuceneTestCase {

  private static final String FIELD = "f";

  private static int count(IntervalsSource source, String... docs) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
        for (String text : docs) {
          Document doc = new Document();
          doc.add(new TextField(FIELD, text, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        return new IndexSearcher(reader).count(new IntervalQuery(FIELD, source));
      }
    }
  }

  private static final IntervalsSource A = Intervals.term("a");
  private static final IntervalsSource B = Intervals.term("b");
  private static final IntervalsSource C = Intervals.term("c");
  private static final IntervalsSource X = Intervals.term("x");

  /** IntervalsSource#minExtent: "the minimum possible width of an interval returned by this source". */
  public void testMinExtentOfRepeatedTerm() {
    assertEquals(2, Intervals.ordered(A, B).minExtent()); // control: two different terms
    assertEquals(2, Intervals.ordered(A, A).minExtent());
    assertEquals(2, Intervals.unordered(A, A).minExtent());
  }

  /** Adding an alternative to a phrase clause must not lose the hit of the first alternative. */
  public void testPhraseOverDisjunctionWithOrderedRepeat() throws IOException {
    assertEquals(1, count(Intervals.phrase(X, Intervals.ordered(A, A), C), "x a a c"));
    assertEquals(1, count(Intervals.phrase(X, Intervals.or(Intervals.ordered(A, B), A), C), "x a b c"));
    // on main: 0, because or(ordered(a, a), a) is not rewritten and minimizes ordered(a, a) away
    assertEquals(1, count(Intervals.phrase(X, Intervals.or(Intervals.ordered(A, A), A), C), "x a a c"));
  }

  public void testPhraseOverDisjunctionWithUnorderedRepeat() throws IOException {
    assertEquals(1, count(Intervals.phrase(X, Intervals.unordered(A, A), C), "x a a c"));
    assertEquals(
        1, count(Intervals.phrase(X, Intervals.or(Intervals.unordered(A, B), A), C), "x a b c"));
    assertEquals(
        1, count(Intervals.phrase(X, Intervals.or(Intervals.unordered(A, A), A), C), "x a a c"));
  }
}
