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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.intervals.IntervalQuery;
import org.apache.lucene.queries.intervals.Intervals;
import org.apache.lucene.queries.intervals.IntervalsSource;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SpanContainingQuery: "matching spans from big that contain at least one spans from little".
 * SpanWithinQuery: "matching spans from little that are inside of big". The Spans contract orders
 * spans by start position and then by end position, so the end positions of a SpanOrQuery of a long
 * and a short clause are not monotone. Both queries then miss documents in which a short little span
 * that starts after a longer one lies inside a big span.
 */
public class TestSpanContainNonMonotoneLittle extends LuceneTestCase {
  private static final String F = "f";

  private static SpanQuery t(String s) {
    return new SpanTermQuery(new Term(F, s));
  }

  private static SpanQuery near(int slop, SpanQuery... clauses) {
    return new SpanNearQuery(clauses, slop, true);
  }

  private static List<String> spans(IndexSearcher searcher, SpanQuery q) throws Exception {
    List<String> out = new ArrayList<>();
    SpanWeight w =
        ((SpanQuery) searcher.rewrite(q)).createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f);
    for (LeafReaderContext ctx : searcher.getIndexReader().leaves()) {
      Spans sp = w.getSpans(ctx, SpanWeight.Postings.POSITIONS);
      if (sp == null) continue;
      while (sp.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
        while (sp.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
          out.add("[" + sp.startPosition() + "," + sp.endPosition() + ")");
        }
      }
    }
    return out;
  }

  private void check(String text, SpanQuery big, SpanQuery little) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(
              dir,
              newIndexWriterConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)))) {
        Document doc = new Document();
        doc.add(new TextField(F, text, Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        String diag =
            "doc '" + text + "': big spans " + spans(searcher, big) + ", little spans "
                + spans(searcher, little);
        Query containing = new SpanContainingQuery(big, little);
        Query within = new SpanWithinQuery(big, little);
        assertEquals(
            diag,
            "SpanContainingQuery hits=1 SpanWithinQuery hits=1",
            "SpanContainingQuery hits="
                + searcher.count(containing)
                + " SpanWithinQuery hits="
                + searcher.count(within));
      }
    }
  }

  /** little = spanOr(spanNear([c, d], 1), a) has spans [0,3) and [1,2); [1,2) is inside big a [1,2). */
  public void testWithinShortLittleAfterLongLittle() throws Exception {
    check("c a d", t("a"), new SpanOrQuery(near(1, t("c"), t("d")), t("a")));
  }

  /** big [0,2) contains the little span b [1,2) but not the earlier, longer little span [0,3). */
  public void testContainingShortLittleAfterLongLittle() throws Exception {
    check(
        "a b a", near(0, t("a"), t("b")), new SpanOrQuery(near(5, t("a"), t("a")), t("b")));
  }

  /**
   * Sentence-scoped search: "cake, or apple followed by pie, inside one sentence". The first sentence
   * contains "cake", so the document must match. It does without "apple"; adding the word "apple"
   * to the first sentence makes it stop matching.
   */
  public void testSentenceScopedOr() throws Exception {
    SpanQuery sentence = near(100, t("bos"), t("eos"));
    SpanQuery little = new SpanOrQuery(near(5, t("apple"), t("pie")), t("cake"));
    check("bos cake eos bos pie eos", sentence, little); // control: matches today
    check("bos apple cake eos bos pie eos", sentence, little); // fails today
  }

  /** Control: the Intervals counterparts of the three shapes above match. */
  public void testIntervalsControl() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(
              dir,
              newIndexWriterConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)))) {
        for (String text : new String[] {"c a d", "a b a", "bos apple cake eos bos pie eos"}) {
          Document doc = new Document();
          doc.add(new TextField(F, text, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        IntervalsSource little1 =
            Intervals.or(Intervals.maxgaps(1, Intervals.ordered(Intervals.term("c"), Intervals.term("d"))), Intervals.term("a"));
        IntervalsSource big2 = Intervals.phrase("a", "b");
        IntervalsSource little2 =
            Intervals.or(Intervals.maxgaps(5, Intervals.ordered(Intervals.term("a"), Intervals.term("a"))), Intervals.term("b"));
        IntervalsSource sentence = Intervals.ordered(Intervals.term("bos"), Intervals.term("eos"));
        IntervalsSource little3 =
            Intervals.or(Intervals.maxgaps(5, Intervals.ordered(Intervals.term("apple"), Intervals.term("pie"))), Intervals.term("cake"));
        assertEquals(2, searcher.count(new IntervalQuery(F, Intervals.containedBy(little1, Intervals.term("a")))));
        assertEquals(1, searcher.count(new IntervalQuery(F, Intervals.containing(big2, little2))));
        assertEquals(1, searcher.count(new IntervalQuery(F, Intervals.containedBy(little2, big2))));
        assertEquals(1, searcher.count(new IntervalQuery(F, Intervals.containing(sentence, little3))));
        assertEquals(1, searcher.count(new IntervalQuery(F, Intervals.containedBy(little3, sentence))));
      }
    }
  }
}
