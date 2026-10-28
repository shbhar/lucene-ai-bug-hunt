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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.intervals.IntervalQuery;
import org.apache.lucene.queries.intervals.Intervals;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SpanWithinQuery returns the little spans ("Keep matches that are contained within another
 * Spans"), so its MatchesIterator reports the little span's positions. Its offsets and sub-matches
 * come from Spans#collect, which ContainSpans implements by collecting the big spans as well: the
 * offsets of "the current match" then cover the big span and the sub-matches ("individual terms
 * within the current match") include terms outside the match positions.
 */
public class TestSpanWithinMatchesOffsets extends LuceneTestCase {

  private static final String TEXT = "ab ac bc"; // ab [0,2) @0, ac [3,5) @1, bc [6,8) @2

  /** Runs the query on the one-document index and checks its only match. */
  private void checkOnlyMatchIsAc(Query query) throws IOException {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    FieldType type = new FieldType(TextField.TYPE_STORED);
    type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    type.freeze();
    try (Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new Field("f", TEXT, type));
        writer.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        Weight weight =
            searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1f);
        LeafReaderContext ctx = reader.leaves().get(0);
        Matches m = weight.matches(ctx, 0);
        assertNotNull("the query matches the document", m);
        MatchesIterator it = m.getMatches("f");
        assertNotNull(it);
        assertTrue(it.next());
        // the only match is "ac" at position 1
        assertEquals(1, it.startPosition());
        assertEquals(1, it.endPosition());
        assertEquals("startOffset of the current match", 3, it.startOffset());
        assertEquals("endOffset of the current match", 5, it.endOffset());
        MatchesIterator sub = it.getSubMatches();
        if (sub != null) {
          while (sub.next()) {
            assertTrue(
                "sub-match at position " + sub.startPosition() + " lies outside the current match",
                sub.startPosition() >= it.startPosition() && sub.endPosition() <= it.endPosition());
          }
        }
        assertFalse(it.next());
      }
    }
  }

  public void testIntervalsContainedByControl() throws IOException {
    checkOnlyMatchIsAc(
            new IntervalQuery(
                "f",
                Intervals.containedBy(
                    Intervals.term("ac"),
                    Intervals.ordered(Intervals.term("ab"), Intervals.term("bc")))));
  }

  public void testSpanWithin() throws IOException {
    SpanQuery big =
        new SpanNearQuery(
            new SpanQuery[] {
              new SpanTermQuery(new Term("f", "ab")), new SpanTermQuery(new Term("f", "bc"))
            },
            1,
            true);
    checkOnlyMatchIsAc(new SpanWithinQuery(big, new SpanTermQuery(new Term("f", "ac"))));
  }
}
