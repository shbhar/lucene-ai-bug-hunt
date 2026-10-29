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

import java.util.Collections;
import java.util.Map;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.hnsw.DefaultFlatVectorScorer;
import org.apache.lucene.codecs.hnsw.HnswGraphProvider;
import org.apache.lucene.index.ByteVectorValues;
import org.apache.lucene.index.DocValuesSkipIndexType;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.Float16VectorValues;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.VectorEncoding;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.AcceptDocs;
import org.apache.lucene.search.KnnCollector;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

public class TestHnswMergeLargeGraphOrdMapping extends LuceneTestCase {

  private static final int N = 805_306_369;

  public void testMergeReusesGraphOfMoreThan805MillionVectors() throws Exception {
    FieldInfo fieldInfo =
        new FieldInfo(
            "v",
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
            2,
            VectorEncoding.FLOAT32,
            VectorSimilarityFunction.EUCLIDEAN,
            false,
            false);
    FloatVectorValues vectors =
        FloatVectorValues.fromFloats(Collections.nCopies(N, new float[2]), 2);
    IncrementalHnswGraphMerger merger =
        new IncrementalHnswGraphMerger(
            fieldInfo,
            DefaultFlatVectorScorer.INSTANCE.getRandomVectorScorerSupplier(
                VectorSimilarityFunction.EUCLIDEAN, vectors),
            16,
            100);
    merger.addReader(new GraphSegmentReader(vectors), doc -> doc, null);
    // expected: ordinals are mapped and the graph copy starts; actual: BufferAllocationException
    expectThrows(GraphCopyStarted.class, () -> merger.merge(vectors, InfoStream.NO_OUTPUT, N));
  }

  private static final class GraphCopyStarted extends RuntimeException {}

  private static final class GraphSegmentReader extends KnnVectorsReader
      implements HnswGraphProvider {
    private final FloatVectorValues vectors;

    GraphSegmentReader(FloatVectorValues vectors) {
      this.vectors = vectors;
    }

    @Override
    public HnswGraph getGraph(String field) {
      return new HnswGraph() {
        @Override
        public int size() {
          return N;
        }

        @Override
        public int maxConn() {
          throw new GraphCopyStarted();
        }

        @Override
        public int numLevels() {
          throw new GraphCopyStarted();
        }

        @Override
        public int entryNode() {
          throw new GraphCopyStarted();
        }

        @Override
        public NodesIterator getNodesOnLevel(int level) {
          throw new GraphCopyStarted();
        }

        @Override
        public void seek(int level, int target) {
          throw new GraphCopyStarted();
        }

        @Override
        public int nextNeighbor() {
          throw new GraphCopyStarted();
        }

        @Override
        public int neighborCount() {
          throw new GraphCopyStarted();
        }
      };
    }

    @Override
    public FloatVectorValues getFloatVectorValues(String field) {
      return vectors;
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
    public void search(String field, float[] target, KnnCollector collector, AcceptDocs docs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void search(String field, byte[] target, KnnCollector collector, AcceptDocs docs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void search(String field, short[] target, KnnCollector collector, AcceptDocs docs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void checkIntegrity(MergePolicy.OneMerge merge) {}

    @Override
    public void close() {}
  }
}
