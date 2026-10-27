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
package org.apache.lucene.tests.search;

import java.io.IOException;
import java.util.Collection;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.DocIdStream;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorable;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DocIdStream#forEach(int upTo, IOIntConsumer) must only visit doc IDs below upTo. The stream that
 * the test framework's AssertingLeafCollector hands to a wrapped collector ignores upTo and visits
 * every remaining doc, so a correct collector that consumes a stream in two steps (forEach(upTo),
 * then count()) gets different results under AssertingIndexSearcher than under IndexSearcher.
 *
 * <p>Uses public API only (AssertingIndexSearcher, DocIdStream, Collector).
 */
public class TestAssertingDocIdStreamForEachUpTo extends LuceneTestCase {

  private static final int NUM_DOCS = 10;
  private static final int SPLIT = 5;

  /** Counts hits below SPLIT and at or above SPLIT; streams are consumed in two steps. */
  private static final class SplitCounter implements Collector {
    int below, above, outOfBound;

    @Override
    public ScoreMode scoreMode() {
      return ScoreMode.COMPLETE_NO_SCORES;
    }

    @Override
    public LeafCollector getLeafCollector(LeafReaderContext context) {
      return new LeafCollector() {
        @Override
        public void setScorer(Scorable scorer) {}

        @Override
        public void collect(int doc) {
          if (doc < SPLIT) {
            below++;
          } else {
            above++;
          }
        }

        @Override
        public void collect(DocIdStream stream) throws IOException {
          // Documented: visits the stream's doc IDs below SPLIT, which may then not be consumed
          // again; the rest are counted afterwards.
          stream.forEach(
              SPLIT,
              doc -> {
                if (doc >= SPLIT) {
                  outOfBound++;
                }
                below++;
              });
          above += stream.count();
        }
      };
    }
  }

  /** Searches with a CollectorManager and sums the per-slice counters. */
  private static SplitCounter count(IndexSearcher searcher, Query query) throws IOException {
    return searcher.search(
        query,
        new CollectorManager<SplitCounter, SplitCounter>() {
          @Override
          public SplitCounter newCollector() {
            return new SplitCounter();
          }

          @Override
          public SplitCounter reduce(Collection<SplitCounter> collectors) {
            SplitCounter sum = new SplitCounter();
            for (SplitCounter c : collectors) {
              sum.below += c.below;
              sum.above += c.above;
              sum.outOfBound += c.outOfBound;
            }
            return sum;
          }
        });
  }

  private static Query disjunction() {
    // A pure disjunction, collected without scores, is scored by BooleanScorer, which passes each
    // window's matches to the collector as one DocIdStream.
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("f", "a")), Occur.SHOULD)
        .add(new TermQuery(new Term("f", "b")), Occur.SHOULD)
        .build();
  }

  private static void index(Directory dir) throws IOException {
    try (IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(newLogMergePolicy()))) {
      for (int i = 0; i < NUM_DOCS; i++) {
        Document doc = new Document();
        doc.add(new StringField("f", i % 2 == 0 ? "a" : "b", Field.Store.NO));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
  }

  private static void assertSplit(String what, SplitCounter c) {
    assertEquals(what + ": docs at or above upTo=" + SPLIT + " passed to forEach(upTo)", 0, c.outOfBound);
    assertEquals(what + ": hits below " + SPLIT, SPLIT, c.below);
    assertEquals(what + ": hits at or above " + SPLIT, NUM_DOCS - SPLIT, c.above);
  }

  public void testForEachUpToUnderAssertingIndexSearcher() throws IOException {
    try (Directory dir = newDirectory()) {
      index(dir);
      try (IndexReader reader = DirectoryReader.open(dir)) {
        Query query = disjunction();
        IndexSearcher plain = new IndexSearcher(reader);
        plain.setQueryCache(null);
        SplitCounter expected = count(plain, query);
        assertSplit("plain IndexSearcher", expected);

        // AssertingLeafCollector decomposes a stream into collect(int) calls on rare occasions and
        // AssertingBulkScorer splits the doc ID space at random points, so search several times.
        for (int iter = 0; iter < 20; iter++) {
          IndexSearcher asserting = new AssertingIndexSearcher(random(), reader);
          asserting.setQueryCache(null);
          SplitCounter actual = count(asserting, query);
          assertSplit("AssertingIndexSearcher, iteration " + iter, actual);
        }
      }
    }
  }

  /** The same through LuceneTestCase.newSearcher, which wraps with AssertingIndexSearcher. */
  public void testForEachUpToUnderNewSearcher() throws IOException {
    try (Directory dir = newDirectory()) {
      index(dir);
      try (IndexReader reader = DirectoryReader.open(dir)) {
        Query query = disjunction();
        for (int iter = 0; iter < 20; iter++) {
          IndexSearcher searcher = newSearcher(reader);
          SplitCounter actual = count(searcher, query);
          assertSplit("newSearcher, iteration " + iter, actual);
        }
      }
    }
  }
}
