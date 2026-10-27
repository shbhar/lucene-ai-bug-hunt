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

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * FixedBitSet#ensureCapacity grows the backing long[] with ArrayUtil.grow and then builds the new
 * set with {@code new FixedBitSet(arr, arr.length << 6)}. The shift is done in int arithmetic, so
 * once the grown array has 2^25 words or more (requested bits above about 1.91 billion) the new
 * length wraps to a negative number. The twin method LongBitSet#ensureCapacity computes {@code
 * (long) arr.length << 6}.
 */
public class TestFixedBitSetEnsureCapacityLarge extends LuceneTestCase {

  private static void check(int desiredBit) {
    FixedBitSet bits = FixedBitSet.ensureCapacity(new FixedBitSet(64), desiredBit);
    assertTrue(
        "length()=" + bits.length() + " must be > desiredBit=" + desiredBit,
        bits.length() > desiredBit);
    bits.set(desiredBit);
    assertTrue(bits.get(desiredBit));
    // the bit must be visible to iteration
    assertEquals(desiredBit, new BitSetIterator(bits, 1).nextDoc());
    assertEquals(
        DocIdSetIterator.NO_MORE_DOCS, new BitSetIterator(bits, 1).advance(desiredBit + 1));
  }

  /** Control: grown array stays below 2^25 words. Passes. */
  public void testDesiredBit1_9Billion() {
    check(1_900_000_000);
  }

  /** Fails on main: the returned set has a negative length. */
  public void testDesiredBit2Billion() {
    check(2_000_000_000);
  }
}
