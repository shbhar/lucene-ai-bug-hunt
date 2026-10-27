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

import java.io.ByteArrayOutputStream;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ByteBlockPool;

/**
 * A ByteBlockPool holds at most 65536 blocks (nextBuffer() guards byteOffset with Math.addExact),
 * i.e. global offsets 0 .. 2^31-1. ByteSlicePool.allocKnownSizeSlice places a slice at the very end
 * of a block when it fits exactly, so in the last block a slice can end at global offset 2^31.
 * ByteSliceReader.nextSlice decides whether it reached the final slice with the int expression
 * {@code nextIndex + newSize >= endIndex}; at that slice nextIndex + newSize == 2^31 wraps to
 * Integer.MIN_VALUE, the reader treats the final slice as a non-final one, and once it reaches the
 * slice's last 4 bytes it reads them as a forwarding address.
 *
 * <p>Same package as the package-private slice classes, as TestByteSliceReader/TestByteSlicePool do.
 * The writer loop is the one TestByteSliceReader uses. To keep the heap small, the first 65535
 * blocks of the pool are one shared array (only the last block is written).
 */
public class TestByteSliceReaderPoolEnd extends LuceneTestCase {

  private static final int BS = ByteBlockPool.BYTE_BLOCK_SIZE;

  /** Pool positioned at the start of block {@code blockIndex}, a fresh zeroed array. */
  private static ByteBlockPool poolAtBlock(int blockIndex) {
    final byte[] shared = new byte[BS];
    final ByteBlockPool pool =
        new ByteBlockPool(
            new ByteBlockPool.Allocator(BS) {
              int calls;

              @Override
              public byte[] getByteBlock() {
                return calls++ < blockIndex ? shared : new byte[BS];
              }

              @Override
              public void recycleByteBlocks(byte[][] blocks, int start, int end) {}
            });
    for (int i = 0; i <= blockIndex; i++) {
      pool.nextBuffer();
    }
    assertEquals((long) blockIndex * BS, pool.byteOffset);
    return pool;
  }

  /**
   * Writes one stream whose slices exactly tile the rest of the block: a 139-byte filler slice, then
   * levels 5,14,20,30,40,40,80,80,120 and 162 slices of 200 (139 + 429 + 162*200 = 32768). The
   * stream is filled up to the byte before the last slice's level marker. Returns the bytes read back
   * with readByte().
   */
  private static byte[][] writeAndRead(int blockIndex) {
    final ByteBlockPool pool = poolAtBlock(blockIndex);
    final ByteSlicePool slicePool = new ByteSlicePool(pool);
    slicePool.newSlice(139); // unrelated allocation that shifts the stream's alignment
    byte[] buffer = pool.buffer;
    int upto = slicePool.newSlice(ByteSlicePool.FIRST_LEVEL_SIZE);
    final int startIndex = upto + pool.byteOffset;
    final ByteArrayOutputStream written = new ByteArrayOutputStream();
    while (upto < BS - 1) {
      if ((buffer[upto] & 16) != 0) {
        upto = slicePool.allocSlice(buffer, upto);
        buffer = pool.buffer;
      }
      final byte b = (byte) (written.size() * 31 + 7);
      buffer[upto++] = b;
      written.write(b);
    }
    assertEquals("stream fills its last slice up to the level marker", BS - 1, upto);
    assertEquals(BS, pool.byteUpto); // the last slice ends exactly at the end of the block
    final int endIndex = upto + pool.byteOffset;

    final byte[] expected = written.toByteArray();
    final byte[] actual = new byte[expected.length];
    final ByteSliceReader reader = new ByteSliceReader();
    reader.init(pool, startIndex, endIndex);
    int n = 0;
    try {
      for (; n < expected.length; n++) {
        actual[n] = reader.readByte();
      }
    } catch (RuntimeException | AssertionError e) {
      throw new AssertionError(
          "block "
              + blockIndex
              + ": reading byte "
              + n
              + " of "
              + expected.length
              + " failed (startIndex="
              + startIndex
              + ", endIndex="
              + endIndex
              + ")",
          e);
    }
    return new byte[][] {expected, actual};
  }

  /** Control: the same layout one block earlier (the last slice ends at 2^31 - 32768). */
  public void testSameLayoutInSecondToLastBlock() {
    final byte[][] r = writeAndRead((1 << 16) - 2);
    assertArrayEquals(r[0], r[1]);
  }

  /** The last block of a full-size pool: the last slice ends at global offset 2^31. */
  public void testLastSliceEndingAtTwoToThe31() {
    final byte[][] r = writeAndRead((1 << 16) - 1);
    assertArrayEquals("bytes read back differ from the bytes written", r[0], r[1]);
  }
}
