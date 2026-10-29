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

import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ByteBlockPool;

public class TestByteSliceReaderFinalSliceAtPoolEnd extends LuceneTestCase {

  public void testFinalSliceEndingAtTwoToThe31() {
    byte[] shared = new byte[ByteBlockPool.BYTE_BLOCK_SIZE];
    ByteBlockPool pool =
        new ByteBlockPool(
            new ByteBlockPool.Allocator(ByteBlockPool.BYTE_BLOCK_SIZE) {
              int count;

              @Override
              public byte[] getByteBlock() {
                return ++count < 1 << 16 ? shared : new byte[blockSize];
              }

              @Override
              public void recycleByteBlocks(byte[][] blocks, int start, int end) {}
            });
    for (int i = 0; i < 1 << 16; i++) {
      pool.nextBuffer();
    }
    ByteSlicePool slicePool = new ByteSlicePool(pool);
    slicePool.newSlice(ByteBlockPool.BYTE_BLOCK_SIZE - 5 - 14);
    int upto = slicePool.newSlice(ByteSlicePool.FIRST_LEVEL_SIZE);
    int startIndex = pool.byteOffset + upto;
    byte[] expected = new byte[14];
    for (int i = 0; i < expected.length; i++) {
      if ((pool.buffer[upto] & 16) != 0) {
        upto = slicePool.allocSlice(pool.buffer, upto);
      }
      expected[i] = (byte) (i + 1);
      pool.buffer[upto++] = expected[i];
    }
    ByteSliceReader reader = new ByteSliceReader();
    reader.init(pool, startIndex, pool.byteOffset + upto);
    byte[] actual = new byte[expected.length];
    reader.readBytes(actual, 0, actual.length);
    // expected: the 14 bytes written; actual: the last 3 come from elsewhere in the pool (0, 0, 0)
    assertArrayEquals(expected, actual);
  }
}
