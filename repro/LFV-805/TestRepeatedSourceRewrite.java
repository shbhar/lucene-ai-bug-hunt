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
 * ordered(a, a) and unordered(a, a) report minExtent() 1, so the gap-sensitive disjunction rewrite
 * keeps or(ordered(a, a), a) as one OR, which minimizes the two-position match away. Public API
 * only. Each test checks a control first: the same query without or(), or with two different words.
 */
public class TestRepeatedSourceRewrite extends LuceneTestCase {

  private static final String FIELD = "body";
  private static final IntervalsSource A = Intervals.term("a");
  private static final IntervalsSource B = Intervals.term("b");
  private static final IntervalsSource C = Intervals.term("c");
  private static final IntervalsSource X = Intervals.term("x");

  /** Hits of the source on a one-document index holding text. */
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

  /** IntervalsSource#minExtent: "the minimum possible width of an interval returned". */
  public void testMinExtent() {
    assertEquals(2, Intervals.ordered(A, B).minExtent()); // control: two different words
    assertEquals("ordered(a, a)", 2, Intervals.ordered(A, A).minExtent());
    assertEquals("unordered(a, a)", 2, Intervals.unordered(A, A).minExtent());
  }

  /** "x, then either 'a a' or 'a', then c" on 'x a a c'. */
  public void testPhraseOverOrWithOrderedRepeat() throws IOException {
    assertEquals(1, count(Intervals.phrase(X, Intervals.ordered(A, A), C), "x a a c"));
    assertEquals(
        1, count(Intervals.phrase(X, Intervals.or(Intervals.ordered(A, B), A), C), "x a b c"));
    IntervalsSource query = Intervals.phrase(X, Intervals.or(Intervals.ordered(A, A), A), C);
    assertEquals(query.toString(), 1, count(query, "x a a c"));
  }

  public void testPhraseOverOrWithUnorderedRepeat() throws IOException {
    assertEquals(1, count(Intervals.phrase(X, Intervals.unordered(A, A), C), "x a a c"));
    assertEquals(
        1, count(Intervals.phrase(X, Intervals.or(Intervals.unordered(A, B), A), C), "x a b c"));
    IntervalsSource query = Intervals.phrase(X, Intervals.or(Intervals.unordered(A, A), A), C);
    assertEquals(query.toString(), 1, count(query, "x a a c"));
  }

  /** The other alternative need not be the repeated word: any single word between the copies. */
  public void testOtherWordBetweenTheCopies() throws IOException {
    assertEquals(1, count(Intervals.phrase(X, Intervals.ordered(A, A), C), "x a b a c"));
    IntervalsSource query = Intervals.phrase(X, Intervals.or(Intervals.ordered(A, A), B), C);
    assertEquals(query.toString(), 1, count(query, "x a b a c"));
  }

  /** maxgaps(0, ...) is the other gap-sensitive parent that rewrites its disjunctions. */
  public void testMaxGapsOverOrWithOrderedRepeat() throws IOException {
    assertEquals(
        1,
        count(Intervals.maxgaps(0, Intervals.ordered(X, Intervals.ordered(A, A), C)), "x a a c"));
    IntervalsSource query =
        Intervals.maxgaps(0, Intervals.ordered(X, Intervals.or(Intervals.ordered(A, A), A), C));
    assertEquals(query.toString(), 1, count(query, "x a a c"));
  }
}
