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
import org.apache.lucene.util.FixedBitSet;

public class TestMergeBaseDeletePctOverflow extends LuceneTestCase {

  public void testMostlyDeletedGraphIsNotMergeBase() throws Exception {
    int size = 22_000_000;
    int deleted = 21_474_837;
    FixedBitSet liveDocs = new FixedBitSet(size);
    liveDocs.set(deleted, size);
    FieldInfo field =
        new FieldInfo(
            "v", 0, false, false, false, IndexOptions.NONE, DocValuesType.NONE,
            DocValuesSkipIndexType.NONE, -1, Map.of(), 0, 0, 0, 1, VectorEncoding.FLOAT32,
            VectorSimilarityFunction.EUCLIDEAN, false, false);
    IncrementalHnswGraphMerger merger = new IncrementalHnswGraphMerger(field, null, 16, 100);
    merger.addReader(new GraphReaderStub(size), doc -> doc, liveDocs);
    // expected: no base graph, 97.6% > DELETE_PCT_THRESHOLD (40%); actual: this graph is the base
    assertNull("97.6%-deleted graph chosen as merge base", merger.largestGraphReader);
  }

  private static final class GraphReaderStub extends KnnVectorsReader
      implements HnswGraphProvider {
    private final int size;

    GraphReaderStub(int size) {
      this.size = size;
    }

    @Override
    public HnswGraph getGraph(String field) {
      return new HnswGraph() {
        @Override
        public int size() {
          return size;
        }

        @Override
        public void seek(int level, int target) {
          throw new UnsupportedOperationException();
        }

        @Override
        public int nextNeighbor() {
          throw new UnsupportedOperationException();
        }

        @Override
        public int numLevels() {
          throw new UnsupportedOperationException();
        }

        @Override
        public int maxConn() {
          throw new UnsupportedOperationException();
        }

        @Override
        public int entryNode() {
          throw new UnsupportedOperationException();
        }

        @Override
        public NodesIterator getNodesOnLevel(int level) {
          throw new UnsupportedOperationException();
        }

        @Override
        public int neighborCount() {
          throw new UnsupportedOperationException();
        }
      };
    }

    @Override
    public FloatVectorValues getFloatVectorValues(String field) {
      return FloatVectorValues.fromFloats(Collections.nCopies(size, new float[1]), 1);
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
    public void checkIntegrity(MergePolicy.OneMerge merge) {}

    @Override
    public void close() {}
  }
}
