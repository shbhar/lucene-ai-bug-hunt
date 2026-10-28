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
package org.apache.lucene.index;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;

/**
 * Small-heap unit tests for BinaryDocValuesFieldUpdates: applying one buffered binary update adds
 * the same value for every document its term matches, so the value bytes kept must not grow with
 * the number of matched documents.
 */
public class TestBinaryDocValuesFieldUpdatesSharedValue extends LuceneTestCase {

  /** 10,000 documents get the same 1 KiB value: keeping 10,000 copies costs 10,240,000 bytes. */
  public void testSameValueOnManyDocsIsStoredOnce() {
    final int numDocs = 10_000;
    final byte[] bytes = new byte[1024];
    random().nextBytes(bytes);
    final BytesRef value = new BytesRef(bytes);
    BinaryDocValuesFieldUpdates updates = new BinaryDocValuesFieldUpdates(0, "f", numDocs);
    for (int doc = 0; doc < numDocs; doc++) {
      updates.add(doc, value);
    }
    updates.finish();
    long copies = (long) numDocs * bytes.length;
    assertTrue(
        "ramBytesUsed=" + updates.ramBytesUsed() + " for " + copies + " bytes of copies",
        updates.ramBytesUsed() < copies / 10);
    DocValuesFieldUpdates.Iterator it = updates.iterator();
    for (int doc = 0; doc < numDocs; doc++) {
      assertEquals(doc, it.nextDoc());
      assertTrue(it.hasValue());
      assertEquals(value, it.binaryValue());
    }
    assertEquals(DocIdSetIterator.NO_MORE_DOCS, it.nextDoc());
  }

  /**
   * Runs of different values (including empty ones), documents in random order and updated more
   * than once, resets, and a caller that reuses one BytesRef and overwrites its bytes between runs:
   * every document must read back its last value.
   */
  public void testRunsOfValuesReadBackLastValue() {
    final int maxDoc = 200;
    final byte[][] pool = new byte[5][];
    for (int i = 0; i < pool.length; i++) {
      pool[i] = new byte[i == 0 ? 0 : 1 + random().nextInt(40)];
      random().nextBytes(pool[i]);
    }
    for (int iter = 0; iter < 50; iter++) {
      BinaryDocValuesFieldUpdates updates = new BinaryDocValuesFieldUpdates(0, "f", maxDoc);
      byte[][] expected = new byte[maxDoc][]; // null: no entry
      boolean[] reset = new boolean[maxDoc];
      BytesRefBuilder reused = new BytesRefBuilder();
      int runs = 1 + random().nextInt(20);
      for (int run = 0; run < runs; run++) {
        if (random().nextInt(10) == 0) {
          int doc = random().nextInt(maxDoc);
          updates.reset(doc);
          expected[doc] = null;
          reset[doc] = true;
          continue;
        }
        byte[] v = pool[random().nextInt(pool.length)];
        BytesRef ref;
        if (random().nextBoolean()) {
          reused.copyBytes(v, 0, v.length); // same object, new bytes
          ref = reused.get();
        } else {
          byte[] padded = new byte[v.length + 3];
          System.arraycopy(v, 0, padded, 1, v.length);
          ref = new BytesRef(padded, 1, v.length);
        }
        int matches = 1 + random().nextInt(30);
        for (int m = 0; m < matches; m++) {
          int doc = random().nextInt(maxDoc);
          updates.add(doc, ref);
          expected[doc] = v;
          reset[doc] = false;
        }
      }
      updates.finish();
      DocValuesFieldUpdates.Iterator it = updates.iterator();
      for (int doc = 0; doc < maxDoc; doc++) {
        if (expected[doc] == null && reset[doc] == false) {
          continue;
        }
        assertEquals(doc, it.nextDoc());
        if (reset[doc]) {
          assertFalse("doc " + doc, it.hasValue());
        } else {
          assertTrue("doc " + doc, it.hasValue());
          assertEquals("doc " + doc, new BytesRef(expected[doc]), it.binaryValue());
        }
      }
      assertEquals(DocIdSetIterator.NO_MORE_DOCS, it.nextDoc());
    }
  }
}
