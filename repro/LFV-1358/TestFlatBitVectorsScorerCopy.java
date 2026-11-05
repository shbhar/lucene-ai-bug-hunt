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
package org.apache.lucene.codecs.bitvectors;

import org.apache.lucene.index.ByteVectorValues;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.hnsw.UpdateableRandomVectorScorer;

public class TestFlatBitVectorsScorerCopy extends LuceneTestCase {

  private static final class CountingValues extends ByteVectorValues {
    final byte[][] vectors = {{0x0F}, {(byte) 0xF0}};
    int reads;

    @Override
    public int dimension() {
      return 1;
    }

    @Override
    public int size() {
      return 2;
    }

    @Override
    public byte[] vectorValue(int ord) {
      reads++;
      return vectors[ord];
    }

    @Override
    public CountingValues copy() {
      return new CountingValues();
    }
  }

  public void testCopyDoesNotShareVectorValues() throws Exception {
    CountingValues original = new CountingValues();
    UpdateableRandomVectorScorer scorer =
        new FlatBitVectorsScorer()
            .getRandomVectorScorerSupplier(VectorSimilarityFunction.EUCLIDEAN, original)
            .copy()
            .scorer();
    scorer.setScoringOrdinal(0);
    scorer.score(1);
    // a scorer of the copy must read only the copy's own values, never the original
    assertEquals(0, original.reads);
  }
}
