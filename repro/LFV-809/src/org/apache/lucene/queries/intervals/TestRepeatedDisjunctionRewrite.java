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
 * Intervals.or(...) "automatically rewrites if wrapped by an interval source that is sensitive to
 * internal gaps", but not when the disjunction is passed twice to unordered/ordered: the two equal
 * sub-sources are merged into one repeated source that the rewrite does not look into, so
 * maxgaps(0, unordered(X, X)) with X = or(b, phrase(c, b)) misses 'c b c b', while the same query
 * whose second X carries an extra, absent alternative matches. Public API only.
 */
public class TestRepeatedDisjunctionRewrite extends LuceneTestCase {

  private static final String FIELD = "f";
  private static final IntervalsSource ABSENT = Intervals.term("zzz"); // in no document

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

  private static IntervalsSource x() {
    return Intervals.or(Intervals.term("b"), Intervals.phrase("c", "b"));
  }

  private static IntervalsSource xWithAbsent() {
    return Intervals.or(Intervals.term("b"), Intervals.phrase("c", "b"), ABSENT);
  }

  public void testUnorderedRepeatedDisjunctionUnderMaxGaps() throws IOException {
    // 'c b c b': b at 1 and the phrase "c b" at [2,3] are adjacent, so there is a no-gap match
    assertEquals(1, count(Intervals.maxgaps(0, Intervals.unordered(x(), xWithAbsent())), "c b c b"));
    // on main: 0
    assertEquals(1, count(Intervals.maxgaps(0, Intervals.unordered(x(), x())), "c b c b"));
  }

  public void testOrderedRepeatedDisjunctionUnderMaxGaps() throws IOException {
    assertEquals(1, count(Intervals.maxgaps(0, Intervals.ordered(x(), xWithAbsent())), "c b c b"));
    // on main: 0
    assertEquals(1, count(Intervals.maxgaps(0, Intervals.ordered(x(), x())), "c b c b"));
  }

  public void testOrderedRepeatedDisjunctionUnderPhrase() throws IOException {
    IntervalsSource a = Intervals.term("a");
    assertEquals(1, count(Intervals.phrase(a, Intervals.ordered(x(), xWithAbsent())), "a c b c b"));
    // on main: 0
    assertEquals(1, count(Intervals.phrase(a, Intervals.ordered(x(), x())), "a c b c b"));
  }
}
