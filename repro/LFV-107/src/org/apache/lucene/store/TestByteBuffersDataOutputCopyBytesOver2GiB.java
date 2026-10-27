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
package org.apache.lucene.store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * ByteBuffersDataOutput#copyBytes(DataInput, long numBytes) narrows numBytes to an int. For
 * numBytes in [2^31, 2^32) the int is negative and nothing is copied; above 2^32 only numBytes
 * mod 2^32 bytes are copied. No exception is thrown in either case.
 *
 * <p>On the buggy code these tests allocate nothing (nothing is copied). A correct implementation
 * needs about 2.1 GB of heap for them, so run with -Xmx4g or more when checking a fix.
 *
 * <p>Uses only public API.
 */
public class TestByteBuffersDataOutputCopyBytesOver2GiB extends LuceneTestCase {

  private static final long NUM_BYTES = (1L << 31) + 7; // just over 2 GiB

  /** A DataInput that yields numBytes zero bytes. */
  private static final class ZeroInput extends DataInput {
    long remaining;

    ZeroInput(long n) {
      remaining = n;
    }

    @Override
    public byte readByte() throws IOException {
      if (remaining <= 0) throw new java.io.EOFException();
      remaining--;
      return 0;
    }

    @Override
    public void readBytes(byte[] b, int offset, int len) throws IOException {
      if (len > remaining) throw new java.io.EOFException();
      java.util.Arrays.fill(b, offset, offset + len, (byte) 0);
      remaining -= len;
    }

    @Override
    public void skipBytes(long numBytes) throws IOException {
      if (numBytes > remaining) throw new java.io.EOFException();
      remaining -= numBytes;
    }
  }

  public void testCopyBytesCopiesAllBytes() throws IOException {
    ByteBuffersDataOutput out = new ByteBuffersDataOutput();
    ZeroInput in = new ZeroInput(NUM_BYTES);
    out.copyBytes(in, NUM_BYTES);
    assertEquals("bytes consumed from the input", 0L, in.remaining);
    assertEquals("ByteBuffersDataOutput.size() after copyBytes", NUM_BYTES, out.size());
  }

  /** Directory#copyFrom of a file just over 2 GiB into a ByteBuffersDirectory. */
  public void testDirectoryCopyFromLargeFile() throws IOException {
    Path tmp = createTempDir();
    // a sparse source file of NUM_BYTES bytes (only the last byte is written)
    try (FileChannel ch =
        FileChannel.open(
            tmp.resolve("big.bin"), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      ch.write(ByteBuffer.wrap(new byte[] {42}), NUM_BYTES - 1);
    }
    try (Directory src = new NIOFSDirectory(tmp);
        Directory dst = new ByteBuffersDirectory()) {
      assertEquals(NUM_BYTES, src.fileLength("big.bin"));
      dst.copyFrom(src, "big.bin", "big.bin", IOContext.DEFAULT);
      assertEquals("length of the copied file", NUM_BYTES, dst.fileLength("big.bin"));
      try (IndexInput in = dst.openInput("big.bin", IOContext.DEFAULT)) {
        in.seek(NUM_BYTES - 1);
        assertEquals(42, in.readByte());
      }
    }
  }
}
