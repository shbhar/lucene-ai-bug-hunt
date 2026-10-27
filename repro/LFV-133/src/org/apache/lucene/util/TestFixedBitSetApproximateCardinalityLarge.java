/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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

import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * FixedBitSet#approximateCardinality scales its sample with {@code (interval / rangeLength) *
 * numWords / maxWord} in int arithmetic. {@code 64 * numWords} overflows once the set has 2^25
 * words, i.e. for lengths of 2^31 - 63 bits or more (legal FixedBitSet lengths; Lucene's doc-ID bit
 * sets stay below IndexWriter.MAX_DOCS = 2^31 - 129, so they are not affected).
 */
public class TestFixedBitSetApproximateCardinalityLarge extends LuceneTestCase {

  private static void check(int numBits) {
    FixedBitSet bits = new FixedBitSet(numBits);
    bits.set(0, numBits);
    int approx = bits.approximateCardinality();
    assertTrue("approximateCardinality()=" + approx + " for " + numBits + " set bits", approx > 0);
    // BitDocIdSet(BitSet) uses approximateCardinality() as its cost
    assertTrue(new BitDocIdSet(bits).iterator().cost() > 0);
  }

  /** Control: 2^31 - 64 bits. Passes. */
  public void testLength2To31Minus64() {
    check(Integer.MAX_VALUE - 63);
  }

  /** Integer.MAX_VALUE bits. Fails on main: negative approximation. */
  public void testLengthIntegerMaxValue() {
    check(Integer.MAX_VALUE);
  }
}
