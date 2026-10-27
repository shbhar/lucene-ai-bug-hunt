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
package org.apache.lucene.misc.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.MergeInfo;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * After a fill of a direct-I/O input fails, the next read on the same input returns bytes of the
 * previously buffered block, attributed to the new position, instead of reading again or failing.
 */
public class TestDirectIOReadAfterFailedFill extends LuceneTestCase {

  private static byte expected(long pos) {
    return (byte) (pos ^ (pos >>> 8) ^ (pos >>> 16) ^ 0x5A);
  }

  private static IOContext merge() {
    return IOContext.merge(new MergeInfo(1, 1, false, 1));
  }

  private Directory open(Path path, int blockSize) throws IOException {
    // mergeBufferSize = one block; minBytesDirect = 0 so that every MERGE-context input uses
    // direct I/O
    return new DirectIODirectory(FSDirectory.open(path), blockSize, 0L);
  }

  private static void write(Directory dir, int len) throws IOException {
    try (IndexOutput out = dir.createOutput("f", IOContext.DEFAULT)) {
      for (int i = 0; i < len; i++) {
        out.writeByte(expected(i));
      }
    }
  }

  /**
   * A clone read after the original was closed (IndexInput's javadoc: every readXXX then throws
   * AlreadyClosedException): the first read that needs I/O fails, the second returns a byte of the
   * old block.
   */
  public void testCloneAfterOriginalClosed() throws IOException {
    Path path = createTempDir();
    int blockSize = Math.toIntExact(Files.getFileStore(path).getBlockSize());
    try (Directory dir = open(path, blockSize)) {
      write(dir, 3 * blockSize);
      IndexInput in = dir.openInput("f", merge());
      assumeTrue(
          "direct I/O input expected", in.getClass().getName().contains("DirectIOIndexInput"));
      IndexInput clone = in.clone();
      byte[] block = new byte[blockSize];
      clone.readBytes(block, 0, blockSize); // consume the first buffered block
      in.close();
      expectThrowsAnyOf(
          java.util.List.of(IOException.class, AlreadyClosedException.class), clone::readByte);
      try {
        long fp = clone.getFilePointer();
        byte b = clone.readByte();
        fail(
            "read on a clone of a closed input returned "
                + b
                + " at file pointer "
                + fp
                + "; the file has "
                + expected(blockSize)
                + " at "
                + blockSize
                + " and "
                + expected(0)
                + " at 0");
      } catch (IOException | AlreadyClosedException expected) {
        // expected: the input stays closed
      }
    }
  }

  /** A read while the thread is interrupted closes the channel; the next read returns old bytes. */
  public void testReadAfterInterruptedFill() throws IOException {
    Path path = createTempDir();
    int blockSize = Math.toIntExact(Files.getFileStore(path).getBlockSize());
    try (Directory dir = open(path, blockSize)) {
      write(dir, 3 * blockSize);
      try (IndexInput in = dir.openInput("f", merge())) {
        assumeTrue(
            "direct I/O input expected", in.getClass().getName().contains("DirectIOIndexInput"));
        byte[] block = new byte[blockSize];
        in.readBytes(block, 0, blockSize);
        assertEquals(expected(blockSize - 1), block[blockSize - 1]);
        Thread.currentThread().interrupt();
        try {
          expectThrows(IOException.class, in::readByte);
        } finally {
          Thread.interrupted(); // clear the flag
        }
        try {
          long fp = in.getFilePointer();
          byte b = in.readByte();
          fail(
              "read after the channel was closed by an interrupt returned "
                  + b
                  + " at file pointer "
                  + fp
                  + "; the file has "
                  + expected(blockSize)
                  + " at "
                  + blockSize
                  + " and "
                  + expected(0)
                  + " at 0");
        } catch (IOException expected) {
          // expected: ClosedChannelException (possibly wrapped)
        }
      }
    }
  }
}
