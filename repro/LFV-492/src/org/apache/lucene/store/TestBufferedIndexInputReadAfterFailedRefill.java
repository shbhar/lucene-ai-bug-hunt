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

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * After a refill of a {@link BufferedIndexInput} fails, the next read on the same input returns
 * bytes from the previously buffered region of the file, attributed to the new position, instead of
 * reading again or failing.
 */
public class TestBufferedIndexInputReadAfterFailedRefill extends LuceneTestCase {

  private static final int LEN = 3 * BufferedIndexInput.BUFFER_SIZE;

  private static byte expected(long pos) {
    return (byte) (pos ^ (pos >>> 8) ^ 0x5A); // distinct at 0, 1, 2048 and 2049
  }

  private static byte[] content() {
    byte[] b = new byte[LEN];
    for (int i = 0; i < LEN; i++) {
      b[i] = expected(i);
    }
    return b;
  }

  /** A BufferedIndexInput over a byte[] whose next readInternal call can be made to fail once. */
  private static final class FlakyInput extends BufferedIndexInput {
    private final byte[] data;
    boolean failNextRead;

    FlakyInput(byte[] data) {
      super("FlakyInput", BufferedIndexInput.BUFFER_SIZE);
      this.data = data;
    }

    @Override
    protected void readInternal(ByteBuffer b) throws IOException {
      if (failNextRead) {
        failNextRead = false;
        throw new IOException("simulated transient read failure");
      }
      long pos = getFilePointer();
      if (pos + b.remaining() > data.length) {
        throw new EOFException("read past EOF");
      }
      b.put(data, (int) pos, b.remaining());
    }

    @Override
    protected void seekInternal(long pos) throws IOException {
      if (pos > length()) {
        throw new EOFException("seek past EOF");
      }
    }

    @Override
    public long length() {
      return data.length;
    }

    @Override
    public void close() {}
  }

  /** A transient failure of one readInternal call: the retry returns the wrong byte. */
  public void testRetryAfterTransientReadFailure() throws IOException {
    FlakyInput in = new FlakyInput(content());
    assertEquals(expected(0), in.readByte()); // buffer now holds file bytes [0, 1024)
    in.seek(2048); // outside the buffer
    in.failNextRead = true;
    expectThrows(IOException.class, in::readByte); // the refill fails
    assertEquals(2048, in.getFilePointer());
    // The failure was transient, so reading again must return the byte at 2048.
    byte b = in.readByte();
    assertEquals(
        "byte returned for position 2048 after a failed refill (file pointer now "
            + in.getFilePointer()
            + "); the byte at position 0 is "
            + expected(0),
        expected(2048),
        b);
  }

  /** The same through random access: readByte(pos) after a failed refill. */
  public void testRandomAccessRetryAfterTransientReadFailure() throws IOException {
    FlakyInput in = new FlakyInput(content());
    assertEquals(expected(0), in.readByte(0));
    in.failNextRead = true;
    expectThrows(IOException.class, () -> in.readByte(2048));
    assertEquals(
        "readByte(2049) after a failed refill; the byte at position 1 is " + expected(1),
        expected(2049),
        in.readByte(2049));
  }

  /**
   * NIOFSDirectory: a clone read after the original was closed. IndexInput's javadoc says every
   * readXXX on the clone then throws AlreadyClosedException; the first read throws an IOException
   * (ClosedChannelException), the second returns a byte from the clone's old buffer.
   */
  public void testNIOFSCloneAfterOriginalClosed() throws IOException {
    try (Directory dir = new NIOFSDirectory(createTempDir())) {
      try (IndexOutput out = dir.createOutput("f", IOContext.DEFAULT)) {
        out.writeBytes(content(), LEN);
      }
      IndexInput in = dir.openInput("f", IOContext.DEFAULT);
      IndexInput clone = in.clone();
      assertEquals(expected(0), clone.readByte());
      clone.seek(2048);
      in.close();
      expectThrowsAnyOf(
          java.util.List.of(IOException.class, AlreadyClosedException.class), clone::readByte);
      try {
        byte b = clone.readByte();
        fail(
            "read on a clone of a closed input returned "
                + b
                + " for position 2048 (file pointer now "
                + clone.getFilePointer()
                + "); the file has "
                + expected(2048)
                + " there and "
                + expected(0)
                + " at position 0");
      } catch (IOException | AlreadyClosedException expected) {
        // expected: the input stays closed
      }
    }
  }

  /**
   * NIOFSDirectory: a read while the thread is interrupted closes the channel
   * (ClosedByInterruptException). The NIOFSDirectory javadoc says later accesses throw
   * ClosedChannelException; the next read on the same input returns a byte from the old buffer.
   */
  public void testNIOFSReadAfterInterruptedRefill() throws IOException {
    try (Directory dir = new NIOFSDirectory(createTempDir())) {
      try (IndexOutput out = dir.createOutput("f", IOContext.DEFAULT)) {
        out.writeBytes(content(), LEN);
      }
      try (IndexInput in = dir.openInput("f", IOContext.DEFAULT)) {
        assertEquals(expected(0), in.readByte());
        in.seek(2048);
        Thread.currentThread().interrupt();
        try {
          expectThrows(IOException.class, in::readByte);
        } finally {
          Thread.interrupted(); // clear the flag
        }
        try {
          byte b = in.readByte();
          fail(
              "read after the channel was closed by an interrupt returned "
                  + b
                  + " for position 2048 (file pointer now "
                  + in.getFilePointer()
                  + "); the file has "
                  + expected(2048)
                  + " there and "
                  + expected(0)
                  + " at position 0");
        } catch (IOException expected) {
          // expected: ClosedChannelException (possibly wrapped)
        }
      }
    }
  }
}
