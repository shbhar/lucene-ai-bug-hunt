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
package org.apache.lucene.util.hnsw;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.TaskExecutor;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.FixedBitSet;

/**
 * HnswConcurrentMergeBuilder.build(maxOrd) for a legal maxOrd close to IndexWriter.MAX_DOCS.
 *
 * <p>The workers claim batches of 2048 ordinals with {@code workProgress.getAndAdd(batchSize)} and
 * stop at the first claim that is not below maxOrd. Every worker makes one such final claim, so
 * the counter is read up to (ceil(maxOrd / 2048) + numWorkers - 1) * 2048. Once that exceeds
 * Integer.MAX_VALUE the returned start wraps to a negative value, which passes the {@code start <
 * maxOrd} check, and the worker calls addGraphNode with negative ordinals.
 *
 * <p>This is a synthetic, class-level isolation of the claim loop, not an end-to-end merge: every
 * ordinal is marked initialized in an otherwise empty graph, so each addGraphNode call is only a
 * bit lookup and nothing per node is allocated (in a real merge the initialized nodes are the
 * reused graph's nodes and the others are inserted). The claim arithmetic does not depend on which
 * ordinals are initialized. The graph array for MAX_DOCS nodes needs about 9 GB of heap.
 */
public class TestHnswConcurrentMergeClaimWrap extends LuceneTestCase {

  static RandomVectorScorerSupplier scorerSupplier(int maxOrd) {
    FloatVectorValues values =
        new FloatVectorValues() {
          @Override
          public float[] vectorValue(int ord) {
            return new float[] {ord};
          }

          @Override
          public FloatVectorValues copy() {
            return this;
          }

          @Override
          public int dimension() {
            return 1;
          }

          @Override
          public int size() {
            return maxOrd;
          }
        };
    return new RandomVectorScorerSupplier() {
      @Override
      public UpdateableRandomVectorScorer scorer() {
        return new UpdateableRandomVectorScorer.AbstractUpdateableRandomVectorScorer(values) {
          @Override
          public void setScoringOrdinal(int node) {}

          @Override
          public float score(int node) {
            return 0;
          }
        };
      }

      @Override
      public RandomVectorScorerSupplier copy() {
        return this;
      }
    };
  }

  public void testBuildWithMaxDocsOrdinalsAndTwoWorkers() throws IOException, InterruptedException {
    // Any maxOrd > Integer.MAX_VALUE + 1 - 2048 * numWorkers triggers it; MAX_DOCS is the largest
    // legal segment.
    int maxOrd = IndexWriter.MAX_DOCS;
    int numWorkers = 2;
    OnHeapHnswGraph graph = new OnHeapHnswGraph(16, maxOrd);
    FixedBitSet initialized = new FixedBitSet(maxOrd);
    initialized.set(0, maxOrd);
    ExecutorService exec = Executors.newFixedThreadPool(numWorkers);
    try {
      HnswConcurrentMergeBuilder builder =
          new HnswConcurrentMergeBuilder(
              new TaskExecutor(exec),
              numWorkers,
              scorerSupplier(maxOrd),
              100,
              graph,
              initialized);
      // HnswBuilder#build: "Adds all nodes to the graph up to the provided maxOrd"
      assertSame(graph, builder.build(maxOrd));
    } finally {
      exec.shutdown();
      assertTrue(exec.awaitTermination(60, TimeUnit.SECONDS));
    }
  }
}
