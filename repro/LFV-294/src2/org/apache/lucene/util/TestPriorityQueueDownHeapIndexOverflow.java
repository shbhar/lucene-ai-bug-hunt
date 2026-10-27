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

import java.util.Collections;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * PriorityQueue accepts maxSize up to ArrayUtil.MAX_ARRAY_LENGTH - 1, but downHeap computes the
 * child index as {@code i << 1} in int. Once the queue holds more than 2^30 elements, a sift-down
 * that reaches node 2^30 (a leaf) computes a negative child index, passes the {@code k <= size}
 * guard and reads heap[-2147483647].
 *
 * <p>Needs about 4.3 GB of heap (an Object[] of 2^30 + 2 compressed references); run with
 * -Xmx6g.
 */
public class TestPriorityQueueDownHeapIndexOverflow extends LuceneTestCase {

  // release variant (10.x and 9.x): PriorityQueue is abstract with a lessThan method there
  private static PriorityQueue<Integer> newQueue(int maxSize) {
    return new PriorityQueue<Integer>(maxSize) {
      @Override
      protected boolean lessThan(Integer a, Integer b) {
        return a < b;
      }
    };
  }

  public void testUpdateTopSinksToNodeTwoToThe30() {
    final int n = (1 << 30) + 1; // node 2^30 exists
    PriorityQueue<Integer> pq = newQueue(n);
    final Integer zero = 0;
    pq.addAll(Collections.nCopies(n, zero)); // one shared element; the heap only stores references
    assertEquals(n, pq.size());

    // The new top (1) is larger than every other element, so it must sink to a leaf.
    // With equal children downHeap follows the left spine 1, 2, 4, ..., 2^30.
    Integer newTop = pq.updateTop(1);

    assertSame(zero, newTop);
    assertEquals(n, pq.size());
  }

  public void testControlJustBelowTwoToThe30() {
    final int n = 1 << 30; // node 2^30 is the last element; it has no children and i << 1 wraps too
    final int m = (1 << 30) - 1; // largest size whose leaves are all < 2^30
    PriorityQueue<Integer> pq = newQueue(m);
    final Integer zero = 0;
    pq.addAll(Collections.nCopies(m, zero));
    Integer newTop = pq.updateTop(1);
    assertSame(zero, newTop);
    assertEquals(m, pq.size());
    assertTrue(n > m);
  }
}
