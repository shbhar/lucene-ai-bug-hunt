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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FeatureField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * WANDScorer with a minimum competitive score of +Infinity.
 *
 * <p>Scores of +Infinity are valid (TopScoreDocCollector only rules out NaN and -Infinity). Here
 * they come from documented-legal inputs: finite FeatureField values (1e37) and a linear weight in
 * (0, 64]; 64 * 1e37 overflows to +Infinity. Once the top-k queue holds +Infinity scores,
 * TopScoreDocCollector calls setMinCompetitiveScore(+Infinity) (nextUp(+Infinity) is +Infinity;
 * across slices the shared accumulator passes +Infinity without nextUp to leaves with smaller doc
 * ids, whose +Infinity documents are still competitive since ties are broken by doc id). Per
 * Scorable#setMinCompetitiveScore a scorer may only skip documents whose score is LESS than
 * minScore.
 *
 * <p>The query ((a b c)~2) is executed by WANDScorer (TOP_SCORES with minShouldMatch > 1). Every
 * document scores +Infinity, so the exact top 3 are docs 0, 1, 2 (score desc, doc asc).
 */
public class TestWandInfiniteMinCompetitiveScore extends LuceneTestCase {

  static Directory buildIndex() throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int seg = 0; seg < 2; ++seg) {
        for (int i = 0; i < 100; ++i) {
          Document d = new Document();
          d.add(new FeatureField("feat", "a", 1e37f));
          d.add(new FeatureField("feat", "b", 1e37f));
          d.add(new FeatureField("feat", "c", 1e37f));
          w.addDocument(d);
        }
        w.commit(); // two segments: docs 0-99 and 100-199
      }
    }
    return dir;
  }

  static Query wandQuery() {
    return new BooleanQuery.Builder()
        .add(FeatureField.newLinearQuery("feat", "a", 64), BooleanClause.Occur.SHOULD)
        .add(FeatureField.newLinearQuery("feat", "b", 64), BooleanClause.Occur.SHOULD)
        .add(FeatureField.newLinearQuery("feat", "c", 64), BooleanClause.Occur.SHOULD)
        .setMinimumNumberShouldMatch(2)
        .build();
  }

  static void assertTop3(Query q, TopDocs td) {
    StringBuilder sb = new StringBuilder();
    for (ScoreDoc sd : td.scoreDocs) sb.append(sd.doc).append(':').append(sd.score).append(' ');
    String got = q + " -> " + sb;
    assertEquals(got, 3, td.scoreDocs.length);
    for (int i = 0; i < 3; ++i) {
      assertEquals(got, i, td.scoreDocs[i].doc);
      assertEquals(got, Float.POSITIVE_INFINITY, td.scoreDocs[i].score, 0f);
    }
  }

  /** Single-threaded search: the queue fills with +Infinity, then the collector sets +Infinity. */
  public void testSingleThreaded() throws IOException {
    try (Directory dir = buildIndex();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setQueryCache(null);
      Query q = wandQuery();
      // exhaustive reference (COMPLETE, no pruning)
      assertTop3(q, searcher.search(q, new TopScoreDocCollectorManager(3, null, Integer.MAX_VALUE)));
      // top-k with dynamic pruning (TOP_SCORES)
      assertTop3(q, searcher.search(q, new TopScoreDocCollectorManager(3, null, 1)));
    }
  }

  /**
   * Concurrent search, one slice per segment. A latch makes the slice of the second segment (docs
   * 100-199) finish before the first slice starts collecting, which is one legal schedule of a
   * concurrent search. The first slice then reads the shared min score +Infinity without nextUp
   * (its doc ids are smaller), so its +Infinity documents are competitive and must be kept.
   */
  public void testConcurrent() throws Exception {
    runConcurrent(wandQuery());
  }

  static void runConcurrent(Query q) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(1);
    try (Directory dir = buildIndex();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(2, reader.leaves().size());
      IndexSearcher searcher =
          new IndexSearcher(reader, executor) {
            @Override
            protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
              LeafSlice[] slices = new LeafSlice[leaves.size()];
              for (int i = 0; i < slices.length; ++i) {
                slices[i] =
                    new LeafSlice(
                        new ArrayList<>(
                            List.of(LeafReaderContextPartition.createForEntireSegment(leaves.get(i)))));
              }
              return slices;
            }
          };
      searcher.setQueryCache(null);
      assertTop3(q, searcher.search(q, new TopScoreDocCollectorManager(3, null, Integer.MAX_VALUE)));
      assertTop3(q, searcher.search(q, new SecondSegmentFirst(new TopScoreDocCollectorManager(3, null, 1))));
    } finally {
      executor.shutdown();
      assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
    }
  }

  /** Delegating manager whose collectors hold back docBase 0 until the other leaf is finished. */
  static final class SecondSegmentFirst implements CollectorManager<Collector, TopDocs> {
    final TopScoreDocCollectorManager in;
    final CountDownLatch secondLeafDone = new CountDownLatch(1);
    final List<TopScoreDocCollector> created = new ArrayList<>();

    SecondSegmentFirst(TopScoreDocCollectorManager in) {
      this.in = in;
    }

    @Override
    public synchronized Collector newCollector() throws IOException {
      TopScoreDocCollector c = in.newCollector();
      created.add(c);
      return new FilterCollector(c) {
        @Override
        public LeafCollector getLeafCollector(LeafReaderContext context) throws IOException {
          if (context.docBase == 0) {
            try {
              assertTrue("second leaf did not finish", secondLeafDone.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
            return super.getLeafCollector(context);
          }
          return new FilterLeafCollector(super.getLeafCollector(context)) {
            @Override
            public void finish() throws IOException {
              super.finish();
              secondLeafDone.countDown();
            }
          };
        }
      };
    }

    @Override
    public TopDocs reduce(Collection<Collector> collectors) throws IOException {
      return in.reduce(created);
    }
  }
}
