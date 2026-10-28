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
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Weight#matches of an Intervals.atLeast query that has a disjunction among its sources throws
 * for a document the query matches, once the disjunction has no interval left while atLeast still
 * iterates. Public API only.
 */
public class TestAtLeastMatchesExhaustedDisjunction extends LuceneTestCase {

  private static final String FIELD = "f";

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
      Document doc = new Document();
      doc.add(new TextField(FIELD, "a c b a", Field.Store.NO)); // positions a0 c1 b2 a3
      w.addDocument(doc);
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private String iteratorIntervals(IntervalsSource source) throws IOException {
    LeafReaderContext leaf = reader.leaves().get(0);
    IntervalIterator it = source.intervals(FIELD, leaf);
    assertNotNull(it);
    assertEquals(0, it.nextDoc());
    StringBuilder sb = new StringBuilder();
    while (it.nextInterval() != IntervalIterator.NO_MORE_INTERVALS) {
      sb.append("[").append(it.start()).append(",").append(it.end()).append("]");
    }
    return sb.toString();
  }

  private String matchesPositions(IntervalsSource source) throws IOException {
    Query q = new IntervalQuery(FIELD, source);
    Weight w = searcher.createWeight(searcher.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1f);
    Matches m = w.matches(reader.leaves().get(0), 0);
    assertNotNull("the document is a hit, so Weight#matches must not be null", m);
    MatchesIterator mi = m.getMatches(FIELD);
    StringBuilder sb = new StringBuilder();
    while (mi.next()) {
      sb.append("[").append(mi.startPosition()).append(",").append(mi.endPosition()).append("]");
    }
    return sb.toString();
  }

  /** atLeast(2, a, b, or(c, d)) on 'a c b a': or(c, d) has one interval and runs out first. */
  public void testMatchesWithDisjunctionSource() throws IOException {
    IntervalsSource source =
        Intervals.atLeast(
            2,
            Intervals.term("a"),
            Intervals.term("b"),
            Intervals.or(Intervals.term("c"), Intervals.term("d")));
    assertEquals(1, searcher.count(new IntervalQuery(FIELD, source)));
    assertEquals("[0,1][1,2][2,3]", iteratorIntervals(source));
    // Matches must describe the same intervals as the iterator; on main this call throws
    // IllegalStateException (AssertionError with -ea) from DisjunctionIntervalIterator.currentOrd
    assertEquals("[0,1][1,2][2,3]", matchesPositions(source));
  }

  /** Control: the same query with a plain term instead of the disjunction works. */
  public void testControlWithoutDisjunction() throws IOException {
    IntervalsSource source =
        Intervals.atLeast(2, Intervals.term("a"), Intervals.term("b"), Intervals.term("c"));
    assertEquals("[0,1][1,2][2,3]", iteratorIntervals(source));
    assertEquals("[0,1][1,2][2,3]", matchesPositions(source));
  }
}
