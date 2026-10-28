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

import static org.apache.lucene.queries.intervals.Intervals.maxgaps;
import static org.apache.lucene.queries.intervals.Intervals.or;
import static org.apache.lucene.queries.intervals.Intervals.ordered;
import static org.apache.lucene.queries.intervals.Intervals.phrase;
import static org.apache.lucene.queries.intervals.Intervals.term;
import static org.apache.lucene.queries.intervals.Intervals.unordered;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The same disjunction passed twice to ordered() or unordered() under a gap-sensitive parent
 * (maxgaps, phrase). X = or(b, "c b") has a one-position and a two-position alternative. In "c b c
 * b" (positions 0 to 3), b at 1 and "c b" at [2, 3] are two distinct, non-overlapping occurrences
 * of X with no position between them, so a gap-free match exists.
 */
public class TestRepeatedOrRewrite extends LuceneTestCase {

  private static final String FIELD = "f";

  private static IntervalsSource x() {
    return or(term("b"), phrase("c", "b"));
  }

  /** The same alternatives plus one that occurs in no document: equivalent, but not equal. */
  private static IntervalsSource xPlusAbsent() {
    return or(term("b"), phrase("c", "b"), term("zzz"));
  }

  private static void assertHits(int expected, IntervalsSource source, String text)
      throws IOException {
    assertEquals(source + " on '" + text + "'", expected, count(source, text));
  }

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

  public void testMaxGapsUnorderedSameOrTwice() throws IOException {
    assertHits(1, maxgaps(0, unordered(x(), x())), "c b c b");
  }

  public void testMaxGapsOrderedSameOrTwice() throws IOException {
    assertHits(1, maxgaps(0, ordered(x(), x())), "c b c b");
  }

  public void testPhraseOverOrderedSameOrTwice() throws IOException {
    // "a" at 0 is followed by "c b" [1, 2] and "c b" [3, 4]
    assertHits(1, phrase(term("a"), ordered(x(), x())), "a c b c b");
  }

  public void testAnalyzedTextRepeatedWordWithMultiWordSynonym() throws IOException {
    // the query text "york york", each word with the synonym path "new york" (the token graph a
    // synonym filter emits for york => new york when it keeps the original)
    CannedTokenStream ts =
        new CannedTokenStream(
            new Token("york", 1, 0, 4, 2),
            new Token("new", 0, 0, 4),
            new Token("york", 1, 0, 4),
            new Token("york", 1, 5, 9, 2),
            new Token("new", 0, 5, 9),
            new Token("york", 1, 5, 9));
    IntervalsSource source = Intervals.analyzedText(ts, 0, false);
    IntervalsSource y = or(term("york"), phrase("new", "york"));
    assertEquals(maxgaps(0, unordered(y, y)), source);
    assertHits(1, source, "new york new york");
  }

  public void testControls() throws IOException {
    // with an equivalent but unequal second source, the documented rewrite runs and matches
    assertHits(1, maxgaps(0, ordered(x(), xPlusAbsent())), "c b c b");
    assertHits(1, phrase(term("a"), ordered(x(), xPlusAbsent())), "a c b c b");
    // ... and it needs two non-overlapping occurrences with no position between them
    assertHits(0, maxgaps(0, ordered(x(), xPlusAbsent())), "c b");
    assertHits(0, maxgaps(0, ordered(x(), xPlusAbsent())), "b x b");
    // the repeated queries are not dead: two adjacent b's match
    assertHits(1, maxgaps(0, unordered(x(), x())), "b b");
    assertHits(1, maxgaps(0, ordered(x(), x())), "b b");
    // one occurrence of X is not two, and a gap filter must not add hits
    assertHits(0, unordered(x(), x()), "c b");
    assertHits(0, maxgaps(0, unordered(x(), x())), "c b");
    assertHits(0, maxgaps(0, ordered(x(), x())), "c b");
  }
}
