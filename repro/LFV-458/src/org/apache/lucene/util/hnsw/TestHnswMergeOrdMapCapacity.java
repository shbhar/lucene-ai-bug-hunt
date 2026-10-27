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
import java.util.Map;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.hnsw.HnswGraphProvider;
import org.apache.lucene.index.ByteVectorValues;
import org.apache.lucene.index.DocValuesSkipIndexType;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.Float16VectorValues;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.VectorEncoding;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.AcceptDocs;
import org.apache.lucene.search.KnnCollector;
import org.apache.lucene.search.TaskExecutor;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

/**
 * Merging an HNSW field whose largest source graph has more than 805,306,368 vectors fails: the
 * merge-time graph reuse maps old to new ordinals through an {@code IntIntHashMap} presized to the
 * graph size, and that hash map cannot hold more than 0.75 * 2^30 entries.
 *
 * <p>The test drives the real mergers through their public API with a segment whose vectors and
 * graph are virtual (nothing of size N is materialised before the failure). The merge is
 * expected to get past the ordinal mapping; the test stops it there, when the merger starts
 * copying the graph, because building an 805M-node graph does not fit a test.
 */
public class TestHnswMergeOrdMapCapacity extends LuceneTestCase {

  /** 0.75 * 2^30 + 1: the smallest graph size the hash map cannot be presized for. */
  static final int N = 805_306_369;

  static final int DIM = 2;
  static final String FIELD = "v";

  static final FieldInfo FI =
      new FieldInfo(
          FIELD,
          0,
          false,
          false,
          false,
          IndexOptions.NONE,
          DocValuesType.NONE,
          DocValuesSkipIndexType.NONE,
          -1,
          Map.of(),
          0,
          0,
          0,
          DIM,
          VectorEncoding.FLOAT32,
          VectorSimilarityFunction.EUCLIDEAN,
          false,
          false);

  /** Thrown by the virtual graph once the merger starts copying it, i.e. after the ord mapping. */
  static final class ReachedGraphCopy extends RuntimeException {
    ReachedGraphCopy() {
      super("ordinal mapping done; merger started to copy the graph");
    }
  }

  /** Dense virtual vectors: ord -> (ord, 0); nothing is stored. */
  static final class VirtualVectors extends FloatVectorValues {
    final int size;

    VirtualVectors(int size) {
      this.size = size;
    }

    @Override
    public float[] vectorValue(int ord) {
      return new float[] {ord, 0};
    }

    @Override
    public FloatVectorValues copy() {
      return this;
    }

    @Override
    public int dimension() {
      return DIM;
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    public DocIndexIterator iterator() {
      return createDenseIterator();
    }
  }

  /** A graph of {@code size} nodes on one level; the merger only asks for its size first. */
  static final class VirtualGraph extends HnswGraph {
    final int size;

    VirtualGraph(int size) {
      this.size = size;
    }

    @Override
    public void seek(int level, int target) {
      throw new ReachedGraphCopy();
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    public int nextNeighbor() {
      throw new ReachedGraphCopy();
    }

    @Override
    public int numLevels() {
      return 1;
    }

    @Override
    public int maxConn() {
      return 16;
    }

    @Override
    public int entryNode() {
      return 0;
    }

    @Override
    public NodesIterator getNodesOnLevel(int level) {
      throw new ReachedGraphCopy();
    }

    @Override
    public int neighborCount() {
      throw new ReachedGraphCopy();
    }
  }

  /** A segment's vectors reader that exposes an HNSW graph (as Lucene99HnswVectorsReader does). */
  static final class SegmentReader extends KnnVectorsReader implements HnswGraphProvider {
    final int numVectors;
    final HnswGraph graph;

    SegmentReader(int numVectors, HnswGraph graph) {
      this.numVectors = numVectors;
      this.graph = graph;
    }

    @Override
    public HnswGraph getGraph(String field) {
      return graph;
    }

    @Override
    public void checkIntegrity(MergePolicy.OneMerge merge) {}

    @Override
    public FloatVectorValues getFloatVectorValues(String field) {
      return new VirtualVectors(numVectors);
    }

    @Override
    public ByteVectorValues getByteVectorValues(String field) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Float16VectorValues getFloat16VectorValues(String field) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void search(String field, float[] target, KnnCollector c, AcceptDocs a) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void search(String field, byte[] target, KnnCollector c, AcceptDocs a) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void search(String field, short[] target, KnnCollector c, AcceptDocs a) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close() {}
  }

  static RandomVectorScorerSupplier scorerSupplier(int maxOrd) {
    return new RandomVectorScorerSupplier() {
      @Override
      public UpdateableRandomVectorScorer scorer() {
        return new UpdateableRandomVectorScorer.AbstractUpdateableRandomVectorScorer(
            new VirtualVectors(maxOrd)) {
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

  /**
   * Segment A: N vectors with a graph, no deletions, docs 0..N-1 of the merged segment. Segment B:
   * one vector without a graph (a tiny flushed segment), doc N. Merged: N + 1 vectors.
   */
  private void mergeBigAndTiny(HnswGraphMerger merger) throws IOException {
    assertTrue("legal doc count", N + 1 <= IndexWriter.MAX_DOCS);
    merger.addReader(new SegmentReader(N, new VirtualGraph(N)), doc -> doc, null);
    merger.addReader(new SegmentReader(1, HnswGraph.EMPTY), doc -> N + doc, null);
    try {
      merger.merge(new VirtualVectors(N + 1), InfoStream.NO_OUTPUT, N + 1);
      fail("the virtual graph should have stopped the merge");
    } catch (ReachedGraphCopy expected) {
      // the ordinal mapping succeeded and the merger went on to reuse the graph: correct
    }
  }

  /** The default merger (numMergeWorkers = 1). */
  public void testIncrementalMergerMapsOrdinalsOfLargeGraph() throws IOException {
    mergeBigAndTiny(new IncrementalHnswGraphMerger(FI, scorerSupplier(N + 1), 16, 100));
  }

  /** The concurrent merger (numMergeWorkers > 1). */
  public void testConcurrentMergerMapsOrdinalsOfLargeGraph() throws IOException {
    TaskExecutor exec = new TaskExecutor(Runnable::run);
    mergeBigAndTiny(new ConcurrentHnswMerger(FI, scorerSupplier(N + 1), 16, 100, exec, 2));
  }
}
