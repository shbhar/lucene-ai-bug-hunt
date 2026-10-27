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
package org.apache.lucene.facet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.Scorable;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DrillSidewaysScorer walks the doc-ID space in 2048-doc chunks with an int {@code nextChunkStart}
 * that is advanced by {@code nextChunkStart += CHUNK} until {@code nextChunkStart >= maxDoc}. For a
 * segment with maxDoc in [2^31 - 2047, IndexWriter.MAX_DOCS] the last chunk start 2^31 - 2048 is
 * still below maxDoc, the next addition wraps to Integer.MIN_VALUE, and every later chunk start is a
 * multiple of 2048 that is at most 2^31 - 2048, so the loop never ends and the documents of the last
 * chunk are never collected.
 *
 * <p>The segment is simulated: a FilterLeafReader reports maxDoc = MAX_DOCS (the scorer reads only
 * maxDoc from the reader) and the base query and the drill-down dimension are constant-score
 * iterators over the last three doc IDs. The scorer is driven directly through its package-private
 * constructor, as TestDrillSideways does. A wrapper iterator stops the run after 32x the number of
 * docID() calls a terminating run needs and reports what was collected.
 */
public class TestDrillSidewaysScorerLastChunk extends LuceneTestCase {

  private static final int MAX_DOCS = IndexWriter.MAX_DOCS; // 2,147,483,519

  /** Reports a different maxDoc; the scorer under test does not read anything else. */
  private static final class BigMaxDocReader extends FilterLeafReader {
    private final int maxDoc;

    BigMaxDocReader(LeafReader in, int maxDoc) {
      super(in);
      this.maxDoc = maxDoc;
    }

    @Override
    public int maxDoc() {
      return maxDoc;
    }

    @Override
    public int numDocs() {
      return maxDoc;
    }

    @Override
    public CacheHelper getCoreCacheHelper() {
      return null;
    }

    @Override
    public CacheHelper getReaderCacheHelper() {
      return null;
    }
  }

  /** Delegating iterator that fails once docID() was called far more often than needed. */
  private static final class ProgressGuard extends DocIdSetIterator {
    private final DocIdSetIterator in;
    private final long limit;
    private final List<Integer> collected;
    private long calls;

    ProgressGuard(DocIdSetIterator in, long limit, List<Integer> collected) {
      this.in = in;
      this.limit = limit;
      this.collected = collected;
    }

    @Override
    public int docID() {
      if (++calls > limit) {
        throw new IllegalStateException(
            "DrillSidewaysScorer made no progress: docID() called "
                + calls
                + " times, iterator stuck at doc "
                + in.docID()
                + ", collected so far="
                + collected);
      }
      return in.docID();
    }

    @Override
    public int nextDoc() throws IOException {
      return in.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
      return in.advance(target);
    }

    @Override
    public long cost() {
      return in.cost();
    }
  }

  private static LeafCollector recorder(List<Integer> out) {
    return new LeafCollector() {
      @Override
      public void setScorer(Scorable scorer) {}

      @Override
      public void collect(int doc) {
        out.add(doc);
      }
    };
  }

  /**
   * Runs one DrillSidewaysScorer over a simulated segment of {@code maxDoc} docs. The base query
   * matches {@code base}, each of {@code numDims} drill-down dimensions matches the last three
   * docs. Returns the docs the main collector collected.
   */
  private List<Integer> run(int maxDoc, DocIdSetIterator base, int numDims) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        w.addDocument(new Document());
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReaderContext ctx = new BigMaxDocReader(r.leaves().get(0).reader(), maxDoc).getContext();
        assertEquals(maxDoc, ctx.reader().maxDoc());
        List<Integer> hits = new ArrayList<>();
        List<Integer> sideways = new ArrayList<>();
        // A terminating run calls docID() about twice per 2048-doc chunk: 2 * 2^20 calls for 2^31.
        long limit = 32L * (maxDoc / 2048 + 1);
        Scorer baseScorer =
            new ConstantScoreScorer(1f, ScoreMode.COMPLETE, new ProgressGuard(base, limit, hits));
        DrillSidewaysScorer.DocsAndCost[] dims = new DrillSidewaysScorer.DocsAndCost[numDims];
        for (int d = 0; d < numDims; d++) {
          DocIdSetIterator it =
              new ProgressGuard(DocIdSetIterator.range(maxDoc - 3, maxDoc), limit, hits);
          dims[d] =
              new DrillSidewaysScorer.DocsAndCost(
                  new ConstantScoreScorer(1f, ScoreMode.COMPLETE, it), recorder(sideways));
        }
        DrillSidewaysScorer scorer = new DrillSidewaysScorer(ctx, baseScorer, dims, false);
        scorer.score(recorder(hits), null, 0, DocIdSetIterator.NO_MORE_DOCS);
        return hits;
      }
    }
  }

  /** Control: a segment whose last chunk ends exactly at 2^31 - 2048 is scored correctly. */
  public void testUnionScoringBelowLastChunk() throws IOException {
    int maxDoc = Integer.MAX_VALUE - 2047; // 2^31 - 2048
    // 1 dimension: base cost 3 >= drill-down cost 3 / 10, so score() uses doUnionScoring
    List<Integer> hits = run(maxDoc, DocIdSetIterator.range(maxDoc - 3, maxDoc), 1);
    assertEquals(List.of(maxDoc - 3, maxDoc - 2, maxDoc - 1), hits);
  }

  /** Control for the drill-down-advance path, same segment size as the first control. */
  public void testDrillDownAdvanceScoringBelowLastChunk() throws IOException {
    int maxDoc = Integer.MAX_VALUE - 2047; // 2^31 - 2048
    List<Integer> hits = run(maxDoc, DocIdSetIterator.all(maxDoc), 2);
    assertEquals(List.of(maxDoc - 3, maxDoc - 2, maxDoc - 1), hits);
  }

  /** doUnionScoring on a MAX_DOCS segment: never returns, the last three hits are lost. */
  public void testUnionScoringLastChunk() throws IOException {
    List<Integer> hits = run(MAX_DOCS, DocIdSetIterator.range(MAX_DOCS - 3, MAX_DOCS), 1);
    assertEquals(List.of(MAX_DOCS - 3, MAX_DOCS - 2, MAX_DOCS - 1), hits);
  }

  /** doDrillDownAdvanceScoring (2 dims far cheaper than the base query) on a MAX_DOCS segment. */
  public void testDrillDownAdvanceScoringLastChunk() throws IOException {
    // base matches every doc (cost MAX_DOCS), the 2 dims cost 3 each: 3 < MAX_DOCS / 10
    List<Integer> hits = run(MAX_DOCS, DocIdSetIterator.all(MAX_DOCS), 2);
    assertEquals(List.of(MAX_DOCS - 3, MAX_DOCS - 2, MAX_DOCS - 1), hits);
  }
}
