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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Intervals.atLeast(k, sources) must return every minimal interval that spans k of the sources. For
 * k = 1 that is exactly what or(sources) returns.
 */
public class TestAtLeastLostIntervals extends LuceneTestCase {

  private static final String FIELD = "f";

  private static IntervalsSource t(String term) {
    return Intervals.term(term);
  }

  private static IndexReader index(Directory dir, String text) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
      Document doc = new Document();
      doc.add(new TextField(FIELD, text, Field.Store.NO));
      w.addDocument(doc);
    }
    return DirectoryReader.open(dir);
  }

  /** Indexes {@code text} as the only document and returns all intervals of {@code source}. */
  private static List<String> intervals(IntervalsSource source, String text) throws IOException {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir, text)) {
      List<String> out = new ArrayList<>();
      IntervalIterator it = source.intervals(FIELD, reader.leaves().get(0));
      if (it != null && it.nextDoc() == 0) {
        while (it.nextInterval() != IntervalIterator.NO_MORE_INTERVALS) {
          out.add("[" + it.start() + "," + it.end() + "]");
        }
      }
      return out;
    }
  }

  /** Indexes {@code text} as the only document and counts the hits of an IntervalQuery. */
  private static int count(IntervalsSource source, String text) throws IOException {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir, text)) {
      return new IndexSearcher(reader).count(new IntervalQuery(FIELD, source));
    }
  }

  /** k = 1 over "a b": both terms are minimal intervals, as or(a, b) returns. */
  public void testMinShouldMatchOneReturnsEveryInterval() throws IOException {
    assertEquals(List.of("[0,0]", "[1,1]"), intervals(Intervals.or(t("a"), t("b")), "a b"));
    assertEquals(List.of("[0,0]", "[1,1]"), intervals(Intervals.atLeast(1, t("a"), t("b")), "a b"));
  }

  /** Nested: "c" at position 1 is followed by "b" at position 2, so the document matches. */
  public void testNestedAtLeastMatchesDocument() throws IOException {
    assertEquals(1, count(Intervals.ordered(t("c"), Intervals.or(t("a"), t("b"))), "a c b"));
    assertEquals(
        1, count(Intervals.ordered(t("c"), Intervals.atLeast(1, t("a"), t("b"))), "a c b"));
  }

  /** k = 2 with a sub-source that spans several positions: [0,2] is (a c)+b, [1,3] is b+d. */
  public void testMinShouldMatchTwoWithMultiPositionSource() throws IOException {
    assertEquals(List.of("[1,3]"), intervals(Intervals.unordered(t("b"), t("d")), "a b c d"));
    IntervalsSource source =
        Intervals.atLeast(2, Intervals.ordered(t("a"), t("c")), t("b"), t("d"));
    assertEquals(List.of("[0,2]", "[1,3]"), intervals(source, "a b c d"));
    assertEquals(1, count(Intervals.containing(source, t("d")), "a b c d"));
  }

  /** "a b" at [0,1] contains "b" at [1,1], so the minimal intervals are the two b's. */
  public void testNoStaleIntervalBehindTheCurrentStart() throws IOException {
    IntervalsSource b = t("b");
    IntervalsSource ab = Intervals.phrase("a", "b");
    assertEquals(List.of("[1,1]", "[2,2]"), intervals(Intervals.or(b, ab), "a b b"));
    assertEquals(List.of("[1,1]", "[2,2]"), intervals(Intervals.atLeast(1, b, ab), "a b b"));
  }
}
