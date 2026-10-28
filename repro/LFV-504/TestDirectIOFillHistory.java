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

import java.io.EOFException;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.OptionalLong;
import java.util.Set;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.mockfile.FilterFileChannel;
import org.apache.lucene.tests.mockfile.FilterFileSystemProvider;
import org.apache.lucene.tests.mockfile.FilterPath;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Version-history repro for DirectIOIndexInput's fill (refill before 10.x main): it moves filePos
 * to the next buffer window, and clears the buffer, before its EOF check and before the channel
 * read succeeds. When the EOF check or the read throws, the input keeps the previous window's bytes
 * under the new window's address. A later legal seek-and-read, or a plain read, then returns the
 * byte of another file position (or a spurious EOFException).
 *
 * <p>One test per trigger. Every scenario that does not inject a failure is run first on the plain
 * FSDirectory the file was written with (the reference, which must pass), then on a
 * DirectIODirectory that uses direct I/O for every read (through the protected {@code
 * useDirectIO} hook, whose signature is the same from 9.0 to main). Only public and protected API
 * is used. The source compiles unchanged against 9.1 to main; a copy with the 9.0 test-framework
 * package names is used for 9.0.
 */
public class TestDirectIOFillHistory extends LuceneTestCase {

  /** Buffer window of the 1-argument DirectIODirectory constructor (256 KiB in every release). */
  private static final int CAP = DirectIODirectory.DEFAULT_MERGE_BUFFER_SIZE;

  /** Byte i of the test file; the window parity flips the high bit, so data[x] != data[x + CAP]. */
  private static byte[] pattern(int n) {
    byte[] b = new byte[n];
    for (int i = 0; i < n; i++) {
      b[i] = (byte) ((i % 251) ^ (((i / CAP) & 1) << 7));
    }
    return b;
  }

  private static byte[] writeFile(Path dir, String name, int n) throws IOException {
    byte[] data = pattern(n);
    try (Directory fs = FSDirectory.open(dir);
        IndexOutput out = fs.createOutput(name, IOContext.DEFAULT)) {
      out.writeBytes(data, 0, n);
    }
    return data;
  }

  /** Direct I/O for every read (outputs are written by the delegate). */
  private static Directory directIO(Path dir) throws IOException {
    return new DirectIODirectory(FSDirectory.open(dir)) {
      @Override
      protected boolean useDirectIO(String name, IOContext context, OptionalLong fileLength) {
        return fileLength.isPresent();
      }
    };
  }

  private static IndexInput open(Directory d, String name, boolean expectDirect)
      throws IOException {
    IndexInput in = d.openInput(name, IOContext.DEFAULT);
    if (expectDirect) {
      // guard against a vacuous run: the input under test must be the direct I/O input
      assertTrue(in.toString(), in.toString().startsWith("DirectIOIndexInput"));
    }
    return in;
  }

  private interface Scenario {
    void run(Directory d, boolean direct) throws Exception;
  }

  private static String label(boolean direct) {
    return direct ? "DirectIODirectory" : "reference FSDirectory";
  }

  private void onBoth(Path p, Scenario s) throws Exception {
    try (Directory ref = FSDirectory.open(p)) {
      s.run(ref, false); // the reference must pass, otherwise the expectation is wrong
    }
    try (Directory dio = directIO(p)) {
      s.run(dio, true);
    }
  }

  /** Reads the byte at pos; a spurious EOFException at a legal position fails the test. */
  private static byte readByteAt(IndexInput in, long pos, String what) throws IOException {
    try {
      return in.readByte();
    } catch (EOFException e) {
      throw new AssertionError(
          what + ": EOFException reading legal position " + pos + " of " + in.length(), e);
    }
  }

  /** Fails exactly one positional channel read when armed; everything else is delegated. */
  static final class OneShotFailingFS extends FilterFileSystemProvider {
    volatile boolean armed;

    OneShotFailingFS(FileSystem delegate) {
      super("oneshotfail://", delegate);
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
      // the plain store, so that DirectIODirectory can read the block size
      return Files.getFileStore(FilterPath.unwrap(path));
    }

    @Override
    public FileChannel newFileChannel(
        Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
        throws IOException {
      return new FilterFileChannel(super.newFileChannel(path, options, attrs)) {
        @Override
        public int read(ByteBuffer dst, long position) throws IOException {
          if (armed) {
            armed = false;
            throw new IOException("simulated transient read error");
          }
          return super.read(dst, position);
        }
      };
    }
  }

  /**
   * [A] One positional channel read fails (the channel stays open), as a transient device or
   * network error would. The read that hit it throws; the next read on the same input must read the
   * file again or keep throwing. Expected to fail wherever fill clears the buffer and moves filePos
   * before channel.read: from the class's first commit on.
   */
  public void testReadAfterTransientReadError() throws Exception {
    Path plain = FilterPath.unwrap(createTempDir("transient"));
    OneShotFailingFS fs = new OneShotFailingFS(plain.getFileSystem());
    Path p = fs.getFileSystem(URI.create("oneshotfail:///")).getPath(plain.toString());
    final byte[] data = writeFile(p, "f", 2 * CAP);
    assertTrue(data[0] != data[CAP]);
    try (Directory d = directIO(p);
        IndexInput in = open(d, "f", true)) {
      byte[] first = new byte[CAP];
      in.readBytes(first, 0, CAP); // the first window, read while the device is healthy
      assertArrayEquals(Arrays.copyOf(data, CAP), first);
      fs.armed = true;
      expectThrows(IOException.class, in::readByte); // needs the next window: the injected error
      assertFalse("vacuity guard: the injected read failure was not hit", fs.armed);
      final long fp = in.getFilePointer();
      final byte b;
      try {
        b = in.readByte();
      } catch (IOException e) {
        return; // keeps failing: acceptable
      }
      assertEquals(
          "DirectIODirectory: byte returned at file pointer " + fp + " after one failed read",
          data[(int) fp],
          b);
    }
  }

  /**
   * [B] A fresh input; the first read asks for more bytes than the file has; then seek(0) and read
   * the first byte. Expected to fail once the EOF check compares the request size on the first
   * fill (after filePos was already moved to 0).
   */
  public void testSeekToStartAfterOversizedFirstRead() throws Exception {
    Path p = createTempDir("oversizedFirstRead");
    final int n = 2 * CAP + 10;
    final byte[] data = writeFile(p, "f", n);
    assertTrue(data[0] != data[CAP]);
    onBoth(
        p,
        (d, direct) -> {
          try (IndexInput in = open(d, "f", direct)) {
            expectThrows(EOFException.class, () -> in.readBytes(new byte[3 * CAP], 0, 3 * CAP));
            in.seek(0);
            assertEquals(0, in.getFilePointer());
            assertEquals(
                label(direct) + ": byte at 0 after seek(0)",
                data[0],
                readByteAt(in, 0, label(direct)));
          }
        });
  }

  /**
   * [C] A 1-byte file: readBytes of 2 bytes throws EOFException; then seek(0) and read the only
   * byte. Same mechanism as [B]; here it shows up as a spurious EOFException at a legal position.
   */
  public void testOneByteFileSeekToStartAfterReadPastEOF() throws Exception {
    Path p = createTempDir("oneByte");
    final byte[] data = writeFile(p, "f", 1);
    onBoth(
        p,
        (d, direct) -> {
          try (IndexInput in = open(d, "f", direct)) {
            expectThrows(EOFException.class, () -> in.readBytes(new byte[2], 0, 2));
            in.seek(0);
            assertEquals(0, in.getFilePointer());
            assertEquals(
                label(direct) + ": byte at 0 after seek(0)",
                data[0],
                readByteAt(in, 0, label(direct)));
          }
        });
  }

  /**
   * [D] A read runs past the end of the file (100 bytes left, 200 requested) right after a full
   * buffer window was consumed; then a seek to a legal position in the next window. Expected to
   * fail once seek repositions inside the current buffer without a read.
   */
  public void testSeekToLegalPositionAfterReadPastEOF() throws Exception {
    Path p = createTempDir("readPastEOF");
    final int n = CAP + 100;
    final byte[] data = writeFile(p, "f", n);
    final int target = CAP + 3;
    assertTrue(data[target] != data[target - CAP]);
    onBoth(
        p,
        (d, direct) -> {
          try (IndexInput in = open(d, "f", direct)) {
            byte[] first = new byte[CAP];
            in.readBytes(first, 0, CAP);
            assertArrayEquals(Arrays.copyOf(data, CAP), first);
            expectThrows(EOFException.class, () -> in.readBytes(new byte[200], 0, 200));
            in.seek(target);
            assertEquals(target, in.getFilePointer());
            assertEquals(
                label(direct) + ": byte at " + target + " after seek(" + target + ")",
                data[target],
                readByteAt(in, target, label(direct)));
          }
        });
  }

  /**
   * [D-slice] slice(1, CAP + 50) of a CAP + 100 byte file; read CAP - 1 bytes, then a read that runs
   * past the slice's end, then seek(CAP) (inside the slice) and read. Before slices became
   * DirectIOIndexInput instances, this exercises BufferedIndexInput.wrap over the direct input.
   */
  public void testSliceSeekToLegalPositionAfterReadPastEOF() throws Exception {
    Path p = createTempDir("sliceReadPastEOF");
    final int n = CAP + 100;
    final byte[] data = writeFile(p, "f", n);
    final int sliceOffset = 1, sliceLength = CAP + 50, target = CAP;
    assertTrue(data[sliceOffset + target] != data[sliceOffset + target - CAP]);
    onBoth(
        p,
        (d, direct) -> {
          try (IndexInput in = open(d, "f", direct)) {
            IndexInput s = in.slice("s", sliceOffset, sliceLength);
            byte[] first = new byte[CAP - 1];
            s.readBytes(first, 0, first.length);
            assertArrayEquals(Arrays.copyOfRange(data, 1, CAP), first);
            // 51 bytes left in the slice, 100 requested
            expectThrows(EOFException.class, () -> s.readBytes(new byte[100], 0, 100));
            s.seek(target);
            assertEquals(target, s.getFilePointer());
            assertEquals(
                label(direct)
                    + ": slice byte at "
                    + target
                    + " (file position "
                    + (sliceOffset + target)
                    + ")",
                data[sliceOffset + target],
                readByteAt(s, target, label(direct)));
          }
        });
  }

  /**
   * [E] A clone has consumed its first buffer window when the original is closed. The IndexInput
   * javadoc says reads on the clone then throw AlreadyClosedException; this test only asks that
   * every read either throws or returns the byte at its file pointer.
   */
  public void testCloneReadsAfterOriginalClosed() throws Exception {
    Path p = createTempDir("cloneAfterClose");
    final int n = 2 * CAP;
    final byte[] data = writeFile(p, "f", n);
    assertTrue(data[0] != data[CAP]);
    onBoth(
        p,
        (d, direct) -> {
          IndexInput in = open(d, "f", direct);
          IndexInput c = in.clone();
          byte[] first = new byte[CAP];
          c.readBytes(first, 0, CAP);
          assertArrayEquals(Arrays.copyOf(data, CAP), first);
          in.close();
          StringBuilder log = new StringBuilder();
          for (int attempt = 1; attempt <= 3; attempt++) {
            final long fp = c.getFilePointer();
            final byte b;
            try {
              b = c.readByte();
            } catch (Exception e) {
              log.append(" attempt ").append(attempt).append(": ").append(e.getClass().getName());
              continue;
            }
            assertEquals(
                label(direct)
                    + ": read #"
                    + attempt
                    + " on a clone of a closed input returned a byte for file pointer "
                    + fp
                    + ";"
                    + log,
                data[(int) fp],
                b);
          }
        });
  }

  /**
   * [F] The reading thread is interrupted, so the channel operation throws
   * ClosedByInterruptException and the channel is closed. After the interrupt flag is cleared,
   * later reads must throw or return the byte at the file pointer.
   */
  public void testReadsAfterInterruptedRead() throws Exception {
    Path p = createTempDir("interruptedRead");
    final int n = 2 * CAP;
    final byte[] data = writeFile(p, "f", n);
    assertTrue(data[0] != data[CAP]);
    onBoth(
        p,
        (d, direct) -> {
          try (IndexInput in = open(d, "f", direct)) {
            byte[] first = new byte[CAP];
            in.readBytes(first, 0, CAP);
            assertArrayEquals(Arrays.copyOf(data, CAP), first);
            boolean firstThrew = false;
            Thread.currentThread().interrupt();
            try {
              in.readByte(); // needs the next window
            } catch (IOException e) {
              firstThrew = true;
            } finally {
              Thread.interrupted(); // clear the flag
            }
            if (direct) {
              // vacuity guard: the interrupt must have failed the direct I/O read
              assertTrue("the interrupted direct I/O read did not fail", firstThrew);
            }
            for (int attempt = 1; attempt <= 2; attempt++) {
              final long fp = in.getFilePointer();
              final byte b;
              try {
                b = in.readByte();
              } catch (IOException e) {
                continue;
              }
              assertEquals(
                  label(direct)
                      + ": read #"
                      + attempt
                      + " after the interrupted read returned a byte for file pointer "
                      + fp,
                  data[(int) fp],
                  b);
            }
          }
        });
  }
}
