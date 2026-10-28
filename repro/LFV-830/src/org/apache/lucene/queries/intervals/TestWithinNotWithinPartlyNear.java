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
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Intervals.within(source, n, reference) "Returns intervals of the source that appear within a set
 * number of positions of intervals from the reference"; Intervals.notWithin(minuend, n,
 * subtrahend) "Returns intervals of the minuend that do not appear within a set number of
 * positions of intervals from the subtrahend query". within is containedBy(source, extend(ref, n,
 * n)) and notWithin is nonOverlapping(minuend, extend(subtrahend, n, n)): a multi-position source
 * interval that is only partly inside the window is returned by neither.
 */
public class TestWithinNotWithinPartlyNear extends LuceneTestCase {

  private static final String FIELD = "f";

  public void testPhraseNextToReferenceIsNeitherWithinNorNotWithin() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
      Document doc = new Document();
      doc.add(new TextField(FIELD, "the new york times", Field.Store.NO));
      w.addDocument(doc);
      try (IndexReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = newSearcher(reader);
        IntervalsSource newYork = Intervals.phrase("new", "york");
        IntervalsSource times = Intervals.term("times");
        assertEquals(1, searcher.count(new IntervalQuery(FIELD, newYork)));
        int within = searcher.count(new IntervalQuery(FIELD, Intervals.within(newYork, 1, times)));
        int notWithin =
            searcher.count(new IntervalQuery(FIELD, Intervals.notWithin(newYork, 1, times)));
        assertEquals(
            "the only 'new york' interval must be either within 1 position of 'times' or not"
                + " (within=" + within + ", notWithin=" + notWithin + ")",
            1,
            within + notWithin);
      }
    }
  }

  public void testPhraseContainingReferenceIsNeitherWithinNorNotWithin() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
      Document doc = new Document();
      doc.add(new TextField(FIELD, "new york", Field.Store.NO));
      w.addDocument(doc);
      try (IndexReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = newSearcher(reader);
        IntervalsSource newYork = Intervals.phrase("new", "york");
        IntervalsSource york = Intervals.term("york");
        int within = searcher.count(new IntervalQuery(FIELD, Intervals.within(newYork, 0, york)));
        int notWithin =
            searcher.count(new IntervalQuery(FIELD, Intervals.notWithin(newYork, 0, york)));
        // the two ways of writing "new york, but not near york" disagree
        BooleanQuery mustNotWithin =
            new BooleanQuery.Builder()
                .add(new IntervalQuery(FIELD, newYork), BooleanClause.Occur.MUST)
                .add(
                    new IntervalQuery(FIELD, Intervals.within(newYork, 0, york)),
                    BooleanClause.Occur.MUST_NOT)
                .build();
        assertEquals(
            "'new york' contains 'york': it must be either within 0 positions of it or not"
                + " (within=" + within + ", notWithin=" + notWithin
                + ", MUST(X) MUST_NOT(within)=" + searcher.count(mustNotWithin) + ")",
            1,
            within + notWithin);
      }
    }
  }
}
