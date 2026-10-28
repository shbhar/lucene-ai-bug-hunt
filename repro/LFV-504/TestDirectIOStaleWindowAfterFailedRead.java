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
import java.util.OptionalLong;
import java.util.Set;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.MergeInfo;
import org.apache.lucene.tests.mockfile.FilterFileChannel;
import org.apache.lucene.tests.mockfile.FilterFileSystemProvider;
import org.apache.lucene.tests.mockfile.FilterPath;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * After a read on a DirectIOIndexInput throws, the next seek or read on the same input can return
 * bytes of another file position. Every file stores, at each multiple of 4, its own position as an
 * int, so readInt() shows where the returned bytes really came from. Scenarios without an injected
 * failure run first on FSDirectory.open (the reference, which passes), then on DirectIODirectory.
 */
public class TestDirectIOStaleWindowAfterFailedRead extends LuceneTestCase {

  private static final int CAP = DirectIODirectory.DEFAULT_MERGE_BUFFER_SIZE; // 262144

  /** Writes a file of n bytes (n % 4 == 0) whose int at position p is p. */
  private static void writeFile(Directory dir, String name, int n) throws IOException {
    try (IndexOutput out = dir.createOutput(name, IOContext.DEFAULT)) {
      for (int p = 0; p < n; p += Integer.BYTES) {
        out.writeInt(p);
      }
    }
  }

  /** Direct I/O for every input, whatever its size or context. */
  private static Directory directIO(Path p) throws IOException {
    return new DirectIODirectory(FSDirectory.open(p)) {
      @Override
      protected boolean useDirectIO(String name, IOContext context, OptionalLong fileLength) {
        return fileLength.isPresent();
      }
    };
  }

  private interface Scenario {
    void run(IndexInput in, String label) throws IOException;
  }

  private void onBoth(Path p, int n, Scenario s) throws IOException {
    try (Directory fs = FSDirectory.open(p)) {
      writeFile(fs, "f", n);
      try (IndexInput in = fs.openInput("f", IOContext.DEFAULT)) {
        s.run(in, "reference"); // the reference must pass
      }
    }
    try (Directory d = directIO(p);
        IndexInput in = d.openInput("f", IOContext.DEFAULT)) {
      assertTrue(in.toString(), in.toString().startsWith("DirectIOIndexInput"));
      s.run(in, "DirectIODirectory");
    }
  }

  /** readInt() at a legal position; an EOFException there fails the test. */
  private static int readIntAt(IndexInput in, long pos, String label) throws IOException {
    try {
      return in.readInt();
    } catch (EOFException e) {
      throw new AssertionError(label + ": EOFException at legal position " + pos, e);
    }
  }

  /** [B] The first read asks for more bytes than the file has; then seek(0) and readInt(). */
  public void testSeekToStartAfterOversizedRead() throws IOException {
    onBoth(
        createTempDir("b"),
        2 * CAP + 16,
        (in, label) -> {
          expectThrows(EOFException.class, () -> in.readBytes(new byte[3 * CAP], 0, 3 * CAP));
          in.seek(0);
          assertEquals(label + ": int at 0", 0, readIntAt(in, 0, label));
        });
  }

  /** [C] A 4-byte file: readBytes of 8 throws; then seek(0) and readInt() must succeed. */
  public void testTinyFileSeekToStartAfterReadPastEOF() throws IOException {
    onBoth(
        createTempDir("c"),
        4,
        (in, label) -> {
          expectThrows(EOFException.class, () -> in.readBytes(new byte[8], 0, 8));
          in.seek(0);
          assertEquals(label + ": int at 0", 0, readIntAt(in, 0, label));
        });
  }

  /** [D] Read one full window, then a read that runs past EOF; then a legal seek and readInt(). */
  public void testSeekToLegalPositionAfterReadPastEOF() throws IOException {
    onBoth(
        createTempDir("d"),
        CAP + 96,
        (in, label) -> {
          in.readBytes(new byte[CAP], 0, CAP);
          expectThrows(EOFException.class, () -> in.readBytes(new byte[200], 0, 200)); // 96 left
          in.seek(CAP + 4);
          assertEquals(CAP + 4, in.getFilePointer());
          assertEquals(label + ": int at " + (CAP + 4), CAP + 4, readIntAt(in, CAP + 4, label));
        });
  }

  /** [D] with the stock DirectIODirectory: merge context, default buffer and 10 MiB threshold. */
  public void testStockMergeInputSeekAfterReadPastEOF() throws IOException {
    Path p = createTempDir("stock");
    final int n = 40 * CAP + 96; // 10,485,856 bytes, just over DEFAULT_MIN_BYTES_DIRECT
    final long target = 40L * CAP + 4;
    try (Directory fs = FSDirectory.open(p)) {
      writeFile(fs, "f", n);
    }
    IOContext merge = IOContext.merge(new MergeInfo(1000, 64L << 20, false, 1));
    try (Directory d = new DirectIODirectory(FSDirectory.open(p));
        IndexInput in = d.openInput("f", merge)) {
      assertTrue(in.toString(), in.toString().startsWith("DirectIOIndexInput"));
      in.seek(39L * CAP);
      in.readBytes(new byte[CAP], 0, CAP);
      expectThrows(EOFException.class, () -> in.readBytes(new byte[200], 0, 200)); // 96 left
      in.seek(target);
      assertEquals(
          "DirectIODirectory: int at " + target,
          target,
          readIntAt(in, target, "DirectIODirectory"));
    }
  }

  /** Fails the next positional channel read once when armed; the channel stays open. */
  static final class FailOnceFS extends FilterFileSystemProvider {
    volatile boolean armed;

    FailOnceFS(FileSystem delegate) {
      super("failonce://", delegate);
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
      return Files.getFileStore(FilterPath.unwrap(path)); // DirectIODirectory reads the block size
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

  /** [A] One channel read fails; the next readInt() must re-read the file or keep failing. */
  public void testReadAfterTransientReadError() throws IOException {
    Path plain = FilterPath.unwrap(createTempDir("a"));
    FailOnceFS fs = new FailOnceFS(plain.getFileSystem());
    Path p = fs.getFileSystem(URI.create("failonce:///")).getPath(plain.toString());
    try (Directory d = directIO(p)) {
      writeFile(d, "f", 2 * CAP);
      try (IndexInput in = d.openInput("f", IOContext.DEFAULT)) {
        in.readBytes(new byte[CAP], 0, CAP); // the first window, read while the device is healthy
        fs.armed = true;
        expectThrows(IOException.class, in::readInt); // needs the next window: the injected error
        assertFalse("the injected failure was not hit", fs.armed);
        assertEquals(CAP, in.getFilePointer());
        final int v;
        try {
          v = in.readInt();
        } catch (IOException _) {
          return; // failing again is fine
        }
        assertEquals("DirectIODirectory: int at " + CAP + " after one failed read", CAP, v);
      }
    }
  }
}
