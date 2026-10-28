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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * containedBy / within look at every alternative of a disjunctive reference (the reference's
 * disjunction is pulled up at construction, LUCENE-8477), but overlapping / nonOverlapping /
 * notWithin iterate the minimized Intervals.or(...), in which an alternative that contains another
 * alternative is dropped. With the reference or(york, "new york"), the interval of a source term is
 * then reported both within and not within n positions of the reference, and both contained by and
 * not overlapping it.
 */
public class TestOverlapFiltersDisjunctiveReference extends LuceneTestCase {

  private static final String FIELD = "f";

  private static List<String> intervals(IntervalsSource source, IndexReader reader)
      throws IOException {
    List<String> out = new ArrayList<>();
    for (LeafReaderContext ctx : reader.leaves()) {
      IntervalIterator it = source.intervals(FIELD, ctx);
      if (it == null) continue;
      for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
        for (int s = it.nextInterval();
            s != IntervalIterator.NO_MORE_INTERVALS;
            s = it.nextInterval()) {
          out.add((ctx.docBase + doc) + ":[" + it.start() + "," + it.end() + "]");
        }
      }
    }
    return out;
  }

  private static IndexReader index(Directory dir, String text) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
      Document doc = new Document();
      doc.add(new TextField(FIELD, text, Field.Store.NO));
      w.addDocument(doc);
    }
    return DirectoryReader.open(dir);
  }

  private static int count(IndexSearcher searcher, IntervalsSource source) throws IOException {
    return searcher.count(new IntervalQuery(FIELD, source));
  }

  /** 'york' or 'new york' */
  private static IntervalsSource place() {
    return Intervals.or(Intervals.term("york"), Intervals.phrase("new", "york"));
  }

  public void testSameIntervalIsWithinAndNotWithin() throws IOException {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir, "visit new york")) {
      IndexSearcher searcher = newSearcher(reader);
      IntervalsSource visit = Intervals.term("visit");
      // controls: 'visit' (position 0) is next to 'new york' (1-2) and two positions from 'york'
      assertEquals(1, count(searcher, Intervals.within(visit, 1, Intervals.phrase("new", "york"))));
      assertEquals(
          0, count(searcher, Intervals.notWithin(visit, 1, Intervals.phrase("new", "york"))));
      assertEquals(0, count(searcher, Intervals.within(visit, 1, Intervals.term("york"))));
      assertEquals(1, count(searcher, Intervals.notWithin(visit, 1, Intervals.term("york"))));
      // within() over the disjunction sees the 'new york' alternative
      assertEquals("[0:[0,0]]", intervals(Intervals.within(visit, 1, place()), reader).toString());
      // ... so notWithin() over the same disjunction must not return the same interval
      assertEquals(
          "the interval of 'visit' is reported both within 1 position of or(york, \"new york\")"
              + " and not within 1 position of it",
          "[]",
          intervals(Intervals.notWithin(visit, 1, place()), reader).toString());
    }
  }

  public void testNestedPhraseAlternatives() throws IOException {
    // 'hall' is next to 'new york city' but two positions after 'new york'
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir, "new york city hall")) {
      IndexSearcher searcher = newSearcher(reader);
      IntervalsSource place =
          Intervals.or(Intervals.phrase("new", "york"), Intervals.phrase("new", "york", "city"));
      IntervalsSource hall = Intervals.term("hall");
      assertEquals(1, count(searcher, Intervals.within(hall, 1, place)));
      assertEquals(
          "'hall' is within 1 position of the 'new york city' alternative",
          0,
          count(searcher, Intervals.notWithin(hall, 1, place)));
    }
  }

  public void testContainedByButNotOverlapping() throws IOException {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir, "new york")) {
      IndexSearcher searcher = newSearcher(reader);
      IntervalsSource n = Intervals.term("new");
      // controls with the single alternative
      assertEquals(1, count(searcher, Intervals.overlapping(n, Intervals.phrase("new", "york"))));
      assertEquals(
          0, count(searcher, Intervals.nonOverlapping(n, Intervals.phrase("new", "york"))));
      // 'new' appears within an interval of or(york, "new york") ...
      assertEquals(1, count(searcher, Intervals.containedBy(n, place())));
      assertEquals(1, count(searcher, Intervals.within(n, 0, place())));
      // ... so it overlaps an interval of it
      assertEquals(
          "containedBy(new, or(york, \"new york\")) matches but overlapping(...) does not",
          1,
          count(searcher, Intervals.overlapping(n, place())));
    }
  }

  public void testContainedByAndNonOverlapping() throws IOException {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir, "new york")) {
      IndexSearcher searcher = newSearcher(reader);
      IntervalsSource n = Intervals.term("new");
      assertEquals("[0:[0,0]]", intervals(Intervals.containedBy(n, place()), reader).toString());
      assertEquals(
          "the interval of 'new' is reported both contained by or(york, \"new york\") and not"
              + " overlapping it (and not within 0 positions of it)",
          "[][]",
          intervals(Intervals.nonOverlapping(n, place()), reader).toString()
              + intervals(Intervals.notWithin(n, 0, place()), reader));
    }
  }
}
