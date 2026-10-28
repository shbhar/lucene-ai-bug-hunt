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

import static org.apache.lucene.queries.spans.SpanTestUtil.spanContainingQuery;
import static org.apache.lucene.queries.spans.SpanTestUtil.spanNearOrderedQuery;
import static org.apache.lucene.queries.spans.SpanTestUtil.spanOrQuery;
import static org.apache.lucene.queries.spans.SpanTestUtil.spanTermQuery;
import static org.apache.lucene.queries.spans.SpanTestUtil.spanWithinQuery;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check written by the intended-behaviour verifier. It asserts only a law that follows
 * from the javadocs, with no reference implementation: SpanOrQuery "matches the union of its
 * clauses", so adding a clause to the little query can only add little spans, and the hit count of
 * SpanContainingQuery(big, little) and SpanWithinQuery(big, little) can only grow. All queries are
 * wrapped in the upstream test framework's AssertingSpanQuery (Spans contract checks under -ea).
 */
public class TestContainmentMonotoneUnion extends LuceneTestCase {
  private static final String F = "f";

  private IndexSearcher searcher;
  private IndexReader reader;
  private Directory dir;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w =
        new IndexWriter(
            dir,
            newIndexWriterConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)))) {
      Document doc = new Document();
      doc.add(newTextField(F, "new york city", Field.Store.NO));
      w.addDocument(doc);
    }
    reader = DirectoryReader.open(dir);
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  /** big = "new york" [0,2); little = york [1,2) is inside it. */
  public void testContainingGrowsWithUnion() throws Exception {
    SpanQuery big = spanNearOrderedQuery(F, 0, "new", "york");
    SpanQuery york = spanTermQuery(F, "york");
    SpanQuery newYorkCity = spanNearOrderedQuery(F, 0, "new", "york", "city");
    SpanQuery expanded = spanOrQuery(newYorkCity, york); // spans [0,3), [1,2)
    int narrow = searcher.count(spanContainingQuery(big, york));
    int wide = searcher.count(spanContainingQuery(big, expanded));
    assertEquals("SpanContaining(big, york)", 1, narrow);
    assertTrue(
        "SpanContaining(big, spanOr(new york city, york)) must match at least what"
            + " SpanContaining(big, york) matches: narrow="
            + narrow
            + " wide="
            + wide,
        wide >= narrow);
  }

  /** big = york [1,2); the little span york [1,2) is inside it. */
  public void testWithinGrowsWithUnion() throws Exception {
    SpanQuery big = spanTermQuery(F, "york");
    SpanQuery york = spanTermQuery(F, "york");
    SpanQuery newYorkCity = spanNearOrderedQuery(F, 0, "new", "york", "city");
    SpanQuery expanded = spanOrQuery(newYorkCity, york); // spans [0,3), [1,2)
    int narrow = searcher.count(spanWithinQuery(big, york));
    int wide = searcher.count(spanWithinQuery(big, expanded));
    assertEquals("SpanWithin(york, york)", 1, narrow);
    assertTrue(
        "SpanWithin(york, spanOr(new york city, york)) must match at least what"
            + " SpanWithin(york, york) matches: narrow="
            + narrow
            + " wide="
            + wide,
        wide >= narrow);
  }

  /** Control: clause order inside the SpanOrQuery must not matter (it is a set union). */
  public void testOrClauseOrderIrrelevant() throws Exception {
    SpanQuery big = spanNearOrderedQuery(F, 0, "new", "york");
    SpanQuery york = spanTermQuery(F, "york");
    SpanQuery newYorkCity = spanNearOrderedQuery(F, 0, "new", "york", "city");
    assertEquals(
        searcher.count(spanContainingQuery(big, spanOrQuery(newYorkCity, york))),
        searcher.count(spanContainingQuery(big, spanOrQuery(york, newYorkCity))));
  }
}
