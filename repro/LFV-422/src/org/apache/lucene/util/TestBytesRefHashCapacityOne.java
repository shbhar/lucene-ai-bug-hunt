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

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * BytesRefHash(pool, capacity, bytesStartArray) accepts capacity 1 (it only rejects capacity <= 0
 * and non-powers of two). With hashSize 1, hashHalfSize is 0, so the "count == hashHalfSize" rehash
 * trigger in add() never fires: the single slot fills up and is never grown, and hashMask is 0, so
 * the high-bits word stored for an entry is the whole hashcode.
 *
 * <p>Uses only public API. The hang test leaves a spinning daemon thread behind, hence the
 * thread-leak scope.
 */
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class TestBytesRefHashCapacityOne extends LuceneTestCase {

  private static BytesRefHash newHash(int capacity) {
    return new BytesRefHash(
        new ByteBlockPool(new ByteBlockPool.DirectAllocator()),
        capacity,
        new BytesRefHash.DirectBytesStartArray(capacity));
  }

  private static int hash(BytesRef b) {
    return StringHelper.murmurhash3_x86_32(b, StringHelper.GOOD_FAST_HASH_SEED);
  }

  /**
   * Returns an 8-byte key whose MurmurHash3_x86_32 under BytesRefHash's seed equals {@code target}.
   * Every murmur3 step is invertible, so the second 4-byte block can be solved for directly.
   */
  private static BytesRef keyWithHash(int target) {
    final int c1 = 0xcc9e2d51, c2 = 0x1b873593;
    final int k1 = 0x9E3779B9;
    int h = StringHelper.GOOD_FAST_HASH_SEED;
    h ^= Integer.rotateLeft(k1 * c1, 15) * c2;
    h = Integer.rotateLeft(h, 13) * 5 + 0xe6546b64;
    int x = target; // undo fmix
    x = unxorshift(x, 16);
    x *= inverse(0xc2b2ae35);
    x = unxorshift(x, 13);
    x *= inverse(0x85ebca6b);
    x = unxorshift(x, 16);
    x ^= 8; // length
    x = Integer.rotateRight((x - 0xe6546b64) * inverse(5), 13);
    final int k2 = Integer.rotateRight((x ^ h) * inverse(c2), 15) * inverse(c1);
    final byte[] b = new byte[8];
    BitUtil.VH_LE_INT.set(b, 0, k1);
    BitUtil.VH_LE_INT.set(b, 4, k2);
    final BytesRef key = new BytesRef(b);
    assertEquals("key synthesis", target, hash(key));
    return key;
  }

  private static int unxorshift(int h, int s) {
    int r = h;
    for (int i = 0; i < 32 / s + 1; i++) {
      r = h ^ (r >>> s);
    }
    return r;
  }

  private static int inverse(int odd) {
    int x = odd;
    for (int i = 0; i < 5; i++) {
      x *= 2 - odd * x;
    }
    return x;
  }

  /**
   * add() javadoc: returns the new id, otherwise -(id)-1 if the bytes were already hashed. find()
   * javadoc: returns the id of the given bytes, or -1 if there is no mapping.
   */
  public void testCapacityOneLosesKeyWhoseHashIsMinusOne() {
    final BytesRefHash h = newHash(1); // accepted by the constructor
    final BytesRef key = keyWithHash(-1);
    assertEquals(0, h.add(key));
    assertEquals(1, h.size());
    assertEquals("find() of the key that was just added", 0, h.find(key));
    assertEquals("add() of a key that is already present", -1, h.add(key));
    assertEquals("the key was added once", 1, h.size());
  }

  /** Control: the same key with capacity 2 behaves as documented. */
  public void testCapacityTwoControl() {
    final BytesRefHash h = newHash(2);
    final BytesRef key = keyWithHash(-1);
    assertEquals(0, h.add(key));
    assertEquals(0, h.find(key));
    assertEquals(-1, h.add(key));
    assertEquals(1, h.size());
    assertEquals(1, h.add(new BytesRef("b")));
    assertEquals(2, h.size());
  }

  /** With capacity 1, adding a second distinct key never returns (linear probe over a full table). */
  public void testCapacityOneSecondDistinctKeyNeverReturns() throws Exception {
    final BytesRefHash h = newHash(1);
    final BytesRef a = new BytesRef("a");
    final BytesRef b = new BytesRef("b");
    assumeTrue("needs a key whose hashcode is not -1", hash(a) != -1);
    assertEquals(0, h.add(a));
    final int[] result = {Integer.MIN_VALUE};
    final Thread t = new Thread(() -> result[0] = h.add(b), "capacity-one-add");
    t.setDaemon(true);
    t.start();
    t.join(20_000);
    final StackTraceElement[] st = t.getStackTrace();
    final StringBuilder top = new StringBuilder();
    for (int i = 0; i < Math.min(3, st.length); i++) {
      top.append("\n    at ").append(st[i]);
    }
    assertFalse(
        "add() of a second distinct key did not return within 20 s; the thread is still in:" + top,
        t.isAlive());
    assertEquals(1, result[0]);
  }
}
