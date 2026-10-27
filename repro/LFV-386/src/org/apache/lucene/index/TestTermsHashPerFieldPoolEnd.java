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

import java.io.IOException;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ByteBlockPool;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Counter;
import org.apache.lucene.util.IntBlockPool;

/**
 * Same defect as TestByteSliceReaderPoolEnd, but the stream is written by the real
 * TermsHashPerField (add -> initStreamSlices / addTerm -> writeByte, which allocates slices through
 * ByteSlicePool) and read back through TermsHashPerField.initReader + ByteSliceReader, i.e. the code
 * the in-RAM postings use at flush. As in TermsHash, the term bytes live in the same pool as the
 * postings. The TermsHashPerField subclass mirrors the one in TestTermsHashPerField; its addTerm
 * writes one byte per occurrence so the stream length is exact.
 *
 * <p>The first 65535 blocks of the byte pool are one shared array, so the test needs little heap.
 */
public class TestTermsHashPerFieldPoolEnd extends LuceneTestCase {

  private static final int BS = ByteBlockPool.BYTE_BLOCK_SIZE;

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
    return pool;
  }

  private static byte valueAt(int i) {
    return (byte) (i * 31 + 7);
  }

  /**
   * Term of 137 bytes (2-byte length header) => term bytes 139 + first slice 5 + levels
   * 14+20+30+40+40+80+80+120 + 161 slices of 200 = 32768: the stream's last slice ends exactly at the
   * end of the block. 31952 one-byte writes fill it up to its level marker.
   */
  private static void run(int blockIndex) throws IOException {
    final ByteBlockPool bytePool = poolAtBlock(blockIndex);
    final int[] written = {0};
    final TermsHashPerField hash =
        new TermsHashPerField(
            1,
            new IntBlockPool(),
            bytePool,
            bytePool, // TermsHash: termBytePool == bytePool for the primary hash
            Counter.newCounter(),
            null,
            "f",
            IndexOptions.DOCS_AND_FREQS) {

          @Override
          void newTerm(int termID, int docID) {}

          @Override
          void addTerm(int termID, int docID) {
            writeByte(0, valueAt(written[0]++));
          }

          @Override
          void newPostingsArray() {}

          @Override
          ParallelPostingsArray createPostingsArray(int size) {
            return new ParallelPostingsArray(size);
          }
        };
    hash.start(null, true);
    final BytesRef term = new BytesRef(new byte[137]);
    hash.add(term, 0); // new term: term bytes + first slice
    for (int i = 0; i < 31952; i++) {
      hash.add(term, 0);
    }
    hash.finish();
    assertEquals("the last slice ends exactly at the end of the block", BS, bytePool.byteUpto);
    assertEquals((long) blockIndex * BS, bytePool.byteOffset);

    final ByteSliceReader reader = new ByteSliceReader();
    hash.initReader(reader, 0, 0);
    final byte[] actual = new byte[written[0]];
    for (int i = 0; i < actual.length; i++) {
      assertFalse("early eof at byte " + i, reader.eof());
      actual[i] = reader.readByte();
    }
    final byte[] expected = new byte[written[0]];
    for (int i = 0; i < expected.length; i++) {
      expected[i] = valueAt(i);
    }
    assertArrayEquals(
        "block " + blockIndex + ": postings stream read back differs from what was written",
        expected,
        actual);
    assertTrue(reader.eof());
  }

  /** Control: same layout in the second-to-last block. */
  public void testSecondToLastBlock() throws IOException {
    run((1 << 16) - 2);
  }

  /** The last block a ByteBlockPool can have: the stream's last slice ends at offset 2^31. */
  public void testLastBlock() throws IOException {
    run((1 << 16) - 1);
  }
}
