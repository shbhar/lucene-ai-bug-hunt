package org.apache.lucene.index;

import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ByteBlockPool;

/**
 * Independent check of the finding: ByteBlockPool.getPosition() computes {@code bufferUpto *
 * allocator.blockSize + byteUpto} in int before widening to long.
 *
 * <p>The pool can hold at most 65536 blocks of 32 KB, because nextBuffer() guards byteOffset with
 * Math.addExact. So the largest legal absolute position is 2^31, which happens when the last block
 * (index 65535) is completely full. The test reaches that state differently from the original
 * repro: it calls nextBuffer() directly to get to the last block, then fills that block with 4096
 * small 8-byte slices. (The original repro used one 32 KB slice per block.)
 *
 * <p>The oracle does not depend on getPosition(): it is {@code (long) pool.byteOffset +
 * pool.byteUpto}, which is how ByteSlicePool and TermsHashPerField compute addresses.
 *
 * <p>Every block is the same shared 32 KB array, so the test needs about 0.5 MB of heap (the
 * buffers[] reference array) rather than 2 GB.
 */
public class TestVerifySA6GetPosition extends LuceneTestCase {

  private static final int BS = ByteBlockPool.BYTE_BLOCK_SIZE; // 32768
  private static final int MAX_BLOCKS = 1 << 16; // 65536 blocks * 32768 bytes = 2^31 bytes

  private static ByteBlockPool sharedBlockPool() {
    final byte[] shared = new byte[BS];
    return new ByteBlockPool(
        new ByteBlockPool.Allocator(BS) {
          @Override
          public void recycleByteBlocks(byte[][] blocks, int start, int end) {}

          @Override
          public byte[] getByteBlock() {
            return shared;
          }
        });
  }

  /** Move the pool to its last legal block (bufferUpto == 65535) with byteUpto == 0. */
  private static ByteBlockPool poolAtLastBlock() {
    ByteBlockPool pool = sharedBlockPool();
    for (int i = 0; i < MAX_BLOCKS; i++) {
      pool.nextBuffer();
    }
    assertEquals((long) (MAX_BLOCKS - 1) * BS, pool.byteOffset); // 2147450880, no overflow yet
    assertEquals(0, pool.byteUpto);
    return pool;
  }

  private static long oracle(ByteBlockPool pool) {
    return (long) pool.byteOffset + pool.byteUpto;
  }

  /**
   * Main check. On main 7cecf79523 this is expected to fail with "expected:<2147483648> but
   * was:<-2147483648>".
   */
  public void testSlicesFillingLastBlockExactly() {
    ByteBlockPool pool = poolAtLastBlock();
    ByteSlicePool slices = new ByteSlicePool(pool);
    final int sliceSize = 8; // 4096 * 8 == 32768
    for (int i = 0; i < BS / sliceSize - 1; i++) {
      slices.newSlice(sliceSize);
    }
    // One slice short of a full block: getPosition() is still right here (2^31 - 8)
    assertEquals(BS - sliceSize, pool.byteUpto);
    assertEquals(2147483640L, oracle(pool));
    assertEquals("one slice before the end", oracle(pool), pool.getPosition());

    // The last slice fits exactly. newSlice() only advances when byteUpto > BS - size, so it
    // does NOT call nextBuffer() and the pool stays on block 65535 with byteUpto == BS.
    final int start = slices.newSlice(sliceSize);
    assertEquals(BS - sliceSize, start);
    assertEquals(BS, pool.byteUpto);
    assertEquals((long) (MAX_BLOCKS - 1) * BS, pool.byteOffset); // still on the last block
    assertEquals(2147483648L, oracle(pool));
    System.out.println(
        "SA6 slices: byteOffset="
            + pool.byteOffset
            + " byteUpto="
            + pool.byteUpto
            + " oracle="
            + oracle(pool)
            + " getPosition()="
            + pool.getPosition());
    assertEquals("absolute position after the last block is full", 2147483648L, pool.getPosition());
  }

  /**
   * Control: the append(byte[]) path, which is what FSTSuffixNodeCache (the only production caller
   * of getPosition()) uses, calls nextBuffer() eagerly when a block is exactly full. So getPosition()
   * peaks at 2^31 - 1 (correct) and the next byte throws ArithmeticException before getPosition()
   * could return 2^31.
   */
  public void testAppendPathCannotReachBoundary() {
    ByteBlockPool pool = poolAtLastBlock();
    pool.append(new byte[BS - 1]);
    assertEquals(BS - 1, pool.byteUpto);
    assertEquals(Integer.MAX_VALUE, pool.getPosition()); // 2^31 - 1, correct
    assertEquals(oracle(pool), pool.getPosition());
    expectThrows(ArithmeticException.class, () -> pool.append(new byte[1]));
    // Side observation (not asserted): nextBuffer() changes bufferUpto/byteUpto before its
    // addExact throws, so the pool is left inconsistent and getPosition() reads (int) 2^31.
    System.out.println(
        "SA6 append control: after ArithmeticException byteOffset="
            + pool.byteOffset
            + " byteUpto="
            + pool.byteUpto
            + " getPosition()="
            + pool.getPosition());
  }

  /** Control: the same through append(ByteBlockPool, long, int), used by copyFallbackNodeBytes. */
  public void testPoolToPoolAppendCannotReachBoundary() {
    ByteBlockPool src = new ByteBlockPool(new ByteBlockPool.DirectAllocator());
    src.append(new byte[BS]);
    ByteBlockPool pool = poolAtLastBlock();
    pool.append(src, 0, BS - 1);
    assertEquals(Integer.MAX_VALUE, pool.getPosition());
    expectThrows(ArithmeticException.class, () -> pool.append(src, 0, 1));
  }

  /** Spot-check: below the boundary getPosition() matches the oracle at every block start. */
  public void testBelowBoundaryIsCorrect() {
    ByteBlockPool pool = sharedBlockPool();
    for (int i = 0; i < MAX_BLOCKS; i++) {
      pool.nextBuffer();
      assertEquals(oracle(pool), pool.getPosition());
    }
  }
}
