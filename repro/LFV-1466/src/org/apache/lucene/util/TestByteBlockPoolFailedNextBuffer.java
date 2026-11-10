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

import org.apache.lucene.tests.util.LuceneTestCase;

public class TestByteBlockPoolFailedNextBuffer extends LuceneTestCase {

  public void testValueAddedAfterFailedNextBufferReadsBack() {
    final int maxBlocks = Integer.MAX_VALUE / ByteBlockPool.BYTE_BLOCK_SIZE + 1;
    final byte[] shared = new byte[ByteBlockPool.BYTE_BLOCK_SIZE];
    ByteBlockPool pool =
        new ByteBlockPool(
            new ByteBlockPool.Allocator(ByteBlockPool.BYTE_BLOCK_SIZE) {
              int calls;

              @Override
              public byte[] getByteBlock() {
                return ++calls < maxBlocks ? shared : new byte[blockSize];
              }

              @Override
              public void recycleByteBlocks(byte[][] blocks, int start, int end) {}
            });
    BytesRefBlockPool values = new BytesRefBlockPool(pool);
    for (int i = 0; i < maxBlocks; i++) {
      pool.nextBuffer();
    }
    values.addBytesRef(new BytesRef("a"));
    expectThrows(ArithmeticException.class, pool::nextBuffer);

    int start = values.addBytesRef(new BytesRef("b"));
    BytesRef readBack = new BytesRef();
    values.fillBytesRef(readBack, start);
    // expected "b"; actual "a": the failed nextBuffer left byteOffset one block behind
    assertEquals(new BytesRef("b"), readBack);
  }
}
