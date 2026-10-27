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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * notContainedBy(small, big) returns the intervals of small that do not appear within intervals of
 * big. When big is an OR, an interval inside any one clause is inside big, so it must not be
 * returned, and adding a clause to big can only remove hits.
 */
public class TestNotContainedByDisjunction extends LuceneTestCase {

  private static final String FIELD = "f";

  private static void index(Directory dir, String... texts) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
      for (String text : texts) {
        Document doc = new Document();
        doc.add(new TextField(FIELD, text, Field.Store.NO));
        w.addDocument(doc);
      }
    }
  }

  private static int count(IndexSearcher searcher, IntervalsSource source) throws IOException {
    return searcher.count(new IntervalQuery(FIELD, source));
  }

  /** "york" that is part of neither "new york" nor "york city": only the third document. */
  public void testOrOfPhrasesAsBigSource() throws IOException {
    try (Directory dir = newDirectory()) {
      index(dir, "i love new york", "york city is big", "york is here");
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        IntervalsSource york = Intervals.term("york");
        IntervalsSource newYork = Intervals.phrase("new", "york");
        IntervalsSource yorkCity = Intervals.phrase("york", "city");

        // the same exclusion written as two chained filters, and as an OR without the rewrite
        IntervalsSource chained =
            Intervals.notContainedBy(Intervals.notContainedBy(york, newYork), yorkCity);
        IntervalsSource noRewrite =
            Intervals.notContainedBy(york, Intervals.or(false, newYork, yorkCity));
        assertEquals(chained.toString(), 1, count(searcher, chained));
        assertEquals(noRewrite.toString(), 1, count(searcher, noRewrite));

        IntervalsSource withOr = Intervals.notContainedBy(york, Intervals.or(newYork, yorkCity));
        assertEquals(withOr.toString(), 1, count(searcher, withOr));
      }
    }
  }

  /** "a" in "c a" lies inside "c a"; adding the clause "b" to big must not bring it back. */
  public void testAddingClauseToBigSourceAddsNoHits() throws IOException {
    try (Directory dir = newDirectory()) {
      index(dir, "c a", "b");
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        IntervalsSource a = Intervals.term("a");
        IntervalsSource ca = Intervals.phrase("c", "a");

        IntervalsSource oneClause = Intervals.notContainedBy(a, ca);
        assertEquals(oneClause.toString(), 0, count(searcher, oneClause));

        IntervalsSource twoClauses =
            Intervals.notContainedBy(a, Intervals.or(ca, Intervals.term("b")));
        assertEquals(twoClauses.toString(), 0, count(searcher, twoClauses));
      }
    }
  }
}
