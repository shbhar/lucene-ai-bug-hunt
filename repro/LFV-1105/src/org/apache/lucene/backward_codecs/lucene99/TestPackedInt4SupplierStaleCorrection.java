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
package org.apache.lucene.backward_codecs.lucene99;

import java.io.IOException;
import org.apache.lucene.codecs.hnsw.DefaultFlatVectorScorer;
import org.apache.lucene.codecs.hnsw.FlatVectorsScorer;
import org.apache.lucene.codecs.lucene99.Lucene99ScalarQuantizedVectorScorer;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.VectorUtil;
import org.apache.lucene.util.hnsw.UpdateableRandomVectorScorer;
import org.apache.lucene.util.quantization.ScalarQuantizer;

/**
 * Two packed (compress=true) int4 vectors in the Lucene99 scalar-quantized layout, [packed codes]
 * [float correction] per vector, as Lucene99ScalarQuantizedVectorsWriter (9.11 to 10.3) wrote them.
 * The supplier's scorer must return the same score for (scoring ordinal, node) whichever nodes it
 * scored in between.
 */
public class TestPackedInt4SupplierStaleCorrection extends LuceneTestCase {

  public void testScoreDoesNotDependOnPreviouslyScoredNodes() throws IOException {
    VectorSimilarityFunction sim = VectorSimilarityFunction.DOT_PRODUCT;
    float[][] vectors = {{0.9f, 0.1f, -0.3f, 0.2f}, {-0.5f, 0.6f, 0.4f, -0.1f}};
    int dim = 4;
    ScalarQuantizer sq = new ScalarQuantizer(-1f, 1f, (byte) 4);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexOutput out = dir.createOutput("vecs", IOContext.DEFAULT)) {
        for (float[] v0 : vectors) {
          float[] v = VectorUtil.l2normalize(v0.clone());
          byte[] codes = new byte[dim];
          float correction = sq.quantize(v, codes, sim);
          byte[] packed = new byte[dim / 2];
          for (int i = 0; i < dim / 2; i++) {
            packed[i] = (byte) ((codes[i] << 4) | codes[dim / 2 + i]);
          }
          out.writeBytes(packed, packed.length);
          out.writeInt(Float.floatToIntBits(correction));
        }
      }
      try (IndexInput in = dir.openInput("vecs", IOContext.DEFAULT)) {
        FlatVectorsScorer flatScorer =
            new Lucene99ScalarQuantizedVectorScorer(DefaultFlatVectorScorer.INSTANCE);
        OffHeapQuantizedByteVectorValues values =
            new OffHeapQuantizedByteVectorValues.DenseOffHeapVectorValues(
                dim, vectors.length, sq, true, sim, flatScorer, in);
        UpdateableRandomVectorScorer scorer =
            flatScorer.getRandomVectorScorerSupplier(sim, values).scorer();
        scorer.setScoringOrdinal(1);
        float before = scorer.score(1);
        scorer.score(0);
        assertEquals("score(1) after scoring node 0", before, scorer.score(1), 0f);
      }
    }
  }
}
