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

import static org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS;

import java.io.IOException;
import java.util.Map;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.hnsw.HnswGraphProvider;
import org.apache.lucene.index.ByteVectorValues;
import org.apache.lucene.index.DocValuesSkipIndexType;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.VectorEncoding;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.AcceptDocs;
import org.apache.lucene.search.KnnCollector;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;

/**
 * IncrementalHnswGraphMerger.addReader computes {@code ((graphSize - live) * 100) / graphSize} in
 * int arithmetic. Once more than 21,474,836 vectors of a graph are deleted the product wraps, the
 * percentage becomes negative, and a graph with far more than DELETE_PCT_THRESHOLD (40%) of its
 * vectors deleted is accepted as the merge base. Drives the public merger API with readers that
 * report the sizes of a 30M-vector segment (no 30M-vector index is built; only sizes, the live-docs
 * mask and the doc iterator are consulted by addReader). The chosen base is read through the
 * protected {@code largestGraphReader} field from a subclass.
 *
 * <p>Release (10.4/10.5) variant: identical logic, without the main-only FP16 reader methods.
 */
public class TestMergeBaseDeletePctOverflow extends LuceneTestCase {

  private static final String FIELD = "v";

  public void testHeavilyDeletedGraphIsNotChosenAsBase() throws IOException {
    check(true);
  }

  /** Same readers, added in the other order. */
  public void testHeavilyDeletedGraphIsNotChosenAsBaseReverseOrder() throws IOException {
    check(false);
  }

  private void check(boolean heavilyDeletedFirst) throws IOException {
    // segment A: 30,000,000 vectors, 25,000,000 deleted (83.3%) -> 5,000,000 live
    FakeReader a = new FakeReader(30_000_000);
    Bits aLive = new Bits() {
      @Override
      public boolean get(int index) {
        return index < 5_000_000;
      }

      @Override
      public int length() {
        return 30_000_000;
      }
    };
    // segment B: 4,000,000 vectors, no deletions
    FakeReader b = new FakeReader(4_000_000);

    BaseProbe merger = new BaseProbe();
    if (heavilyDeletedFirst) {
      merger.addReader(a, doc -> -1, aLive);
      merger.addReader(b, doc -> -1, null);
    } else {
      merger.addReader(b, doc -> -1, null);
      merger.addReader(a, doc -> -1, aLive);
    }

    assertNotNull(merger.base());
    assertSame(
        "segment A has 83% of its vectors deleted (> DELETE_PCT_THRESHOLD = 40%), so the"
            + " deletion-free segment B must be the base; deletePct computed for A = "
            + ((30_000_000 - 5_000_000) * 100) / 30_000_000,
        b,
        merger.base());
  }

  /** Control: the same shape scaled down 10x (no int overflow) picks segment B. */
  public void testControlScaledDown() throws IOException {
    FakeReader a = new FakeReader(3_000_000);
    Bits aLive = new Bits() {
      @Override
      public boolean get(int index) {
        return index < 500_000;
      }

      @Override
      public int length() {
        return 3_000_000;
      }
    };
    FakeReader b = new FakeReader(400_000);
    BaseProbe merger = new BaseProbe();
    merger.addReader(a, doc -> -1, aLive);
    merger.addReader(b, doc -> -1, null);
    assertSame(b, merger.base());
  }

  private static final FieldInfo FI =
      new FieldInfo(
          FIELD, 0, false, false, false, IndexOptions.NONE, DocValuesType.NONE,
          DocValuesSkipIndexType.NONE, -1, Map.of(), 0, 0, 0, 2, VectorEncoding.FLOAT32,
          VectorSimilarityFunction.EUCLIDEAN, false, false);

  private static final class BaseProbe extends IncrementalHnswGraphMerger {
    BaseProbe() {
      super(FI, null, 16, 100);
    }

    KnnVectorsReader base() {
      return largestGraphReader == null ? null : largestGraphReader.reader();
    }
  }

  /** A graph that only reports its size; addReader needs nothing else. */
  private static HnswGraph sizedGraph(int size) {
    return new HnswGraph() {
      @Override
      public void seek(int level, int target) {
        throw new UnsupportedOperationException();
      }

      @Override
      public int size() {
        return size;
      }

      @Override
      public int nextNeighbor() {
        return NO_MORE_DOCS;
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
        return new DenseNodesIterator(size);
      }

      @Override
      public int neighborCount() {
        return 0;
      }
    };
  }

  /** Dense float vectors (doc i = ord i), one constant value; plus a graph of the same size. */
  private static final class FakeReader extends KnnVectorsReader implements HnswGraphProvider {
    private final int size;

    FakeReader(int size) {
      this.size = size;
    }

    @Override
    public HnswGraph getGraph(String field) {
      return sizedGraph(size);
    }

    @Override
    public FloatVectorValues getFloatVectorValues(String field) {
      float[] v = {1f, 1f};
      return new FloatVectorValues() {
        @Override
        public float[] vectorValue(int ord) {
          return v;
        }

        @Override
        public int dimension() {
          return 2;
        }

        @Override
        public int size() {
          return size;
        }

        @Override
        public FloatVectorValues copy() {
          return this;
        }

        @Override
        public DocIndexIterator iterator() {
          return createDenseIterator();
        }
      };
    }

    @Override
    public void checkIntegrity() {}

    @Override
    public ByteVectorValues getByteVectorValues(String field) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void search(String f, float[] t, KnnCollector c, AcceptDocs a) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void search(String f, byte[] t, KnnCollector c, AcceptDocs a) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close() {}
  }
}
