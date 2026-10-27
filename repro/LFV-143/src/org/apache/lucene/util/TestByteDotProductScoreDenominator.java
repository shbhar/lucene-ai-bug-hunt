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
package org.apache.lucene.util;

import java.util.Arrays;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The byte DOT_PRODUCT score is {@code 0.5f + dot / (float) (len * (1 << 15))}
 * (VectorUtil#dotProductScore, and the same expression in the Panama byte scorers). The
 * denominator is multiplied in int before the cast, so from 65,536 dimensions it wraps negative and
 * every score is mirrored around 0.5; at 131,072 dimensions it wraps to 0. The vectors below are
 * all +1 / -1, far from any accumulator limit (|dot| <= 131,072).
 */
public class TestByteDotProductScoreDenominator extends LuceneTestCase {

  private static byte[] filled(int n, int value) {
    byte[] v = new byte[n];
    Arrays.fill(v, (byte) value);
    return v;
  }

  /** A vector identical to the query must score higher than its opposite (65,536 dimensions). */
  public void testDotProductScoreOrdersBySimilarity() {
    int n = 1 << 16;
    byte[] q = filled(n, 1), same = filled(n, 1), opposite = filled(n, -1);
    float sameScore = VectorSimilarityFunction.DOT_PRODUCT.compare(q, same);
    float oppositeScore = VectorSimilarityFunction.DOT_PRODUCT.compare(q, opposite);
    assertTrue(
        "same=" + sameScore + " must score higher than opposite=" + oppositeScore,
        sameScore > oppositeScore);
  }

  /** VectorUtil#dotProductScore javadoc: "scaled to be in [0, 1]" (131,072 dimensions). */
  public void testDotProductScoreInUnitInterval() {
    int n = 1 << 17;
    byte[] q = filled(n, 1);
    float score = VectorUtil.dotProductScore(q, q.clone());
    assertTrue("score " + score + " must be in [0, 1]", score >= 0f && score <= 1f);
  }

}
