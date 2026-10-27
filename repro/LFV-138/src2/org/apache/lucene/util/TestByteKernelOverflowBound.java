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
 * Variant for releases before uint8SquareDistance existed (#15148): the uint8 test is omitted.
 *
 * <p>The byte-vector kernels accumulate in a 32-bit int. Code comments in DefaultVectorUtilSupport
 * state that cosine(byte[]) and squareDistance(byte[]) "will not overflow if dim < 2^18" and that
 * uint8SquareDistance "will not overflow if dim < 2^16". The largest squared difference of two
 * bytes is 255^2 = 65025 (not 2^14), and the largest |byte * byte| is 2^14 = 16384, so the int
 * accumulators can overflow from 33,026 dimensions (squareDistance, uint8SquareDistance) and from
 * 131,072 dimensions (cosine, dotProduct). Every input below uses fewer dimensions than the stated
 * bound.
 */
public class TestByteKernelOverflowBound extends LuceneTestCase {

  private static byte[] filled(int n, int value) {
    byte[] v = new byte[n];
    Arrays.fill(v, (byte) value);
    return v;
  }

  /** squareDistance(byte[]): 33,026 < 2^18 dimensions. */
  public void testSquareDistanceBelowCommentedBound() {
    int n = 33_026;
    byte[] a = filled(n, 127), b = filled(n, -128);
    long exact = (long) n * 255 * 255; // 2,147,515,650
    int got = VectorUtil.squareDistance(a, b);
    assertEquals(
        "sum of " + n + " squared differences (comment: no overflow if dim < 2^18)",
        exact,
        (long) got);
  }

  /** cosine(byte[]) of a vector with itself: 131,072 = 2^17 < 2^18 dimensions. */
  public void testCosineOfVectorWithItselfBelowCommentedBound() {
    int n = 1 << 17;
    byte[] a = filled(n, -128);
    float got = VectorUtil.cosine(a, a.clone());
    assertEquals("cosine of a vector with itself (comment: no overflow if dim < 2^18)", 1f, got, 1e-6f);
  }

  /**
   * The same overflow through the public similarity function: the document farthest from the query
   * gets a higher EUCLIDEAN score than a document next to the query (66,052 dimensions).
   */
  public void testEuclideanSimilarityRanksFarthestFirst() {
    int n = 66_052;
    byte[] query = filled(n, -128), near = filled(n, -127), far = filled(n, 127);
    float nearScore = VectorSimilarityFunction.EUCLIDEAN.compare(near, query);
    float farScore = VectorSimilarityFunction.EUCLIDEAN.compare(far, query);
    assertTrue(
        "near=" + nearScore + " must score higher than far=" + farScore, nearScore > farScore);
  }

}
