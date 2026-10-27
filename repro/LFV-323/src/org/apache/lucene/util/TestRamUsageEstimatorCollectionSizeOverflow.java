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
import java.util.Collections;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * RamUsageEstimator.sizeOfCollection computes {@code NUM_BYTES_ARRAY_HEADER + collection.size() *
 * NUM_BYTES_OBJECT_REF} in int arithmetic. For a collection with more than about 2^29 elements
 * (2^28 without compressed oops) the product overflows and the estimate becomes negative. Every
 * other array formula in the class widens to long first (for example shallowSizeOf(Object[])).
 *
 * <p>Uses public API only. Needs about 2.2 GB of heap for the array-backed list (run with -Xmx3g or
 * more); the nCopies variant needs no extra heap.
 */
public class TestRamUsageEstimatorCollectionSizeOverflow extends LuceneTestCase {

  /** Smallest size for which header + size * ref no longer fits in an int. */
  private static int minimalOverflowingSize() {
    return (Integer.MAX_VALUE - RamUsageEstimator.NUM_BYTES_ARRAY_HEADER)
            / RamUsageEstimator.NUM_BYTES_OBJECT_REF
        + 1;
  }

  /** An array-backed list: the shape the method's own comment assumes. */
  public void testArrayBackedListOfNulls() {
    int n = minimalOverflowingSize();
    Object[] backing = new Object[n];
    List<Object> list = Arrays.asList(backing);
    long estimate = RamUsageEstimator.sizeOfCollection(list);
    // The backing array alone takes this many bytes; the estimate must be at least that.
    long backingArray = RamUsageEstimator.shallowSizeOf(backing);
    assertTrue(
        "sizeOfCollection("
            + n
            + " elements) = "
            + estimate
            + ", but the backing Object[] alone is "
            + backingArray
            + " bytes",
        estimate >= backingArray);
  }

  /** The same through the general entry point sizeOfObject(Object). */
  public void testSizeOfObjectOnLargeList() {
    int n = minimalOverflowingSize();
    Object[] backing = new Object[n];
    long estimate = RamUsageEstimator.sizeOfObject(Arrays.asList(backing));
    long backingArray = RamUsageEstimator.shallowSizeOf(backing);
    assertTrue(
        "sizeOfObject(list of " + n + ") = " + estimate + " < backing array " + backingArray,
        estimate >= backingArray);
  }

  /** No large heap needed: a virtual list of the same size gives a negative size in bytes. */
  public void testNCopiesIsNeverNegative() {
    int n = minimalOverflowingSize();
    long estimate = RamUsageEstimator.sizeOfCollection(Collections.nCopies(n, null));
    assertTrue("sizeOfCollection(nCopies(" + n + ", null)) = " + estimate, estimate >= 0);
  }
}
