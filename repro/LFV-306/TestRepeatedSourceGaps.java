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
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Intervals.ordered and Intervals.unordered turn equal sub-sources into one repeating source. When
 * the repeated piece spans more than one position (a phrase), its gaps must still be the gaps
 * between the copies.
 */
public class TestRepeatedSourceGaps extends LuceneTestCase {

  private static final String F = "f";

  // doc 0: positions 0-3, doc 1: positions 0-5, doc 2: rock@0 roll@2 roll@4 ("and" is a stop word)
  private static final String[] DOCS = {
    "new york new york", "x y z x y z", "rock and roll and roll"
  };

  private Analyzer analyzer;
  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer =
        new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true, MockTokenFilter.ENGLISH_STOPSET);
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      for (String text : DOCS) {
        Document doc = new Document();
        doc.add(new TextField(F, text, Field.Store.NO));
        w.addDocument(doc);
      }
    }
    reader = DirectoryReader.open(dir);
    assertEquals(1, reader.leaves().size());
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    analyzer.close();
    super.tearDown();
  }

  private static IntervalsSource newYork() {
    return Intervals.phrase("new", "york");
  }

  private int count(IntervalsSource source) throws IOException {
    return searcher.count(new IntervalQuery(F, source));
  }

  /** [start, end, gaps, width] of the first interval of doc, from the iterator. */
  private List<Integer> iteratorInterval(IntervalsSource source, int doc) throws IOException {
    LeafReaderContext ctx = reader.leaves().get(0);
    IntervalIterator it = source.intervals(F, ctx);
    assertEquals(doc, it.advance(doc));
    assertNotEquals(IntervalIterator.NO_MORE_INTERVALS, it.nextInterval());
    return List.of(it.start(), it.end(), it.gaps(), it.width());
  }

  /** [start, end, gaps, width] of the first match of doc, from the Matches API. */
  private List<Integer> matchesInterval(IntervalsSource source, int doc) throws IOException {
    LeafReaderContext ctx = reader.leaves().get(0);
    IntervalMatchesIterator mi = source.matches(F, ctx, doc);
    assertNotNull(mi);
    assertTrue(mi.next());
    return List.of(mi.startPosition(), mi.endPosition(), mi.gaps(), mi.width());
  }

  /** "new york" twice, back to back: 0 gaps between the two copies. */
  public void testMaxGapsOrderedRepeatedPhrase() throws IOException {
    IntervalsSource source = Intervals.maxgaps(0, Intervals.ordered(newYork(), newYork()));
    assertEquals(source + " on 'new york new york'", 1, count(source));
  }

  public void testMaxGapsUnorderedRepeatedPhrase() throws IOException {
    IntervalsSource source = Intervals.maxgaps(0, Intervals.unordered(newYork(), newYork()));
    assertEquals(source + " on 'new york new york'", 1, count(source));
  }

  /** The iterator and the Matches API must report the same interval, gaps and width. */
  public void testIteratorAgreesWithMatches() throws IOException {
    IntervalsSource source = Intervals.ordered(newYork(), newYork());
    assertEquals(
        "Matches [start, end, gaps, width]", List.of(0, 3, 0, 4), matchesInterval(source, 0));
    assertEquals(
        "iterator [start, end, gaps, width]", List.of(0, 3, 0, 4), iteratorInterval(source, 0));
  }

  public void testThreeWordPhraseRepeated() throws IOException {
    IntervalsSource xyz = Intervals.phrase("x", "y", "z");
    IntervalsSource source = Intervals.ordered(xyz, xyz);
    assertEquals(
        "iterator [start, end, gaps, width]", List.of(0, 5, 0, 6), iteratorInterval(source, 1));
  }

  /** Weight#matches must return null exactly when the scorer does not match the document. */
  public void testScorerAgreesWithWeightMatches() throws IOException {
    Query q = new IntervalQuery(F, Intervals.maxgaps(0, Intervals.ordered(newYork(), newYork())));
    Weight weight = searcher.createWeight(searcher.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1f);
    LeafReaderContext ctx = searcher.getIndexReader().leaves().get(0);
    boolean matchesFindsDoc0 = weight.matches(ctx, 0) != null;
    boolean scorerFindsDoc0 = searcher.count(q) == 1;
    assertTrue("Weight#matches finds doc 0", matchesFindsDoc0);
    assertEquals("the scorer agrees with Weight#matches", matchesFindsDoc0, scorerFindsDoc0);
  }

  /** Plain text with a stop word: analyzedText builds ordered(rock, extend(roll), extend(roll)). */
  public void testAnalyzedTextWithStopWord() throws IOException {
    IntervalsSource source = Intervals.analyzedText("rock and roll and roll", analyzer, F, 1, true);
    assertEquals(source + " on 'rock and roll and roll'", 1, count(source));
  }

  /** Controls that pass with and without the bug. */
  public void testControls() throws IOException {
    // the same query, but the second copy wrapped in a no-op filter, so it is not deduplicated
    IntervalsSource notEqual =
        Intervals.maxgaps(0, Intervals.ordered(newYork(), Intervals.maxwidth(2, newYork())));
    assertEquals(notEqual.toString(), 1, count(notEqual));
    // a repeated single term: start == end, so the sign makes no difference
    IntervalsSource singleTerm =
        Intervals.maxgaps(1, Intervals.ordered(Intervals.term("york"), Intervals.term("york")));
    assertEquals(singleTerm.toString(), 1, count(singleTerm));
    // the same plain text without the repeat
    IntervalsSource noRepeat = Intervals.analyzedText("rock and roll", analyzer, F, 1, true);
    assertEquals(noRepeat.toString(), 1, count(noRepeat));
  }
}
