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
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.junit.BeforeClass;

/**
 * DirectIODirectory accepts any mergeBufferSize, but its direct-I/O buffer is an aligned slice
 * whose capacity is a multiple of the file system block size. DirectIOIndexInput starts at
 * filePos = -mergeBufferSize and then advances by the buffer's capacity, so when mergeBufferSize is
 * not a multiple of the block size the first read is issued at a negative or unaligned file
 * position and fails. A mergeBufferSize below the block size can also give a zero-capacity buffer
 * (depending on the native address of the allocation), and DirectIOIndexOutput then throws
 * BufferOverflowException on its first byte.
 */
public class TestDirectIOMergeBufferSize extends LuceneTestCase {

  @BeforeClass
  public static void checkSupported() throws IOException {
    assumeTrue(
        "needs a JDK with ExtendedOpenOption.DIRECT",
        DirectIODirectory.ExtendedOpenOption_DIRECT != null);
    Path path = createTempDir("directIOProbe");
    try (Directory dir = directIO(path, DirectIODirectory.DEFAULT_MERGE_BUFFER_SIZE);
        IndexOutput out = dir.createOutput("out", IOContext.DEFAULT)) {
      out.writeString("test");
    } catch (IOException e) {
      assumeNoException("needs a file system that supports direct I/O", e);
    }
  }

  /** A DirectIODirectory that uses direct I/O for every file, as TestDirectIODirectory does. */
  private static DirectIODirectory directIO(Path path, int mergeBufferSize) throws IOException {
    return new DirectIODirectory(FSDirectory.open(path), mergeBufferSize, 0L) {
      @Override
      protected boolean useDirectIO(String name, IOContext context, OptionalLong fileLength) {
        return true;
      }
    };
  }

  private static int blockSize(Path path) throws IOException {
    return Math.toIntExact(Files.getFileStore(path).getBlockSize());
  }

  /** Reads through DirectIOIndexInput with a buffer size that is not a block multiple. */
  public void testReadWithBufferSizeNotAMultipleOfTheBlockSize() throws Exception {
    Path path = createTempDir("notMultiple");
    final int blockSize = blockSize(path);
    final int mergeBufferSize = 4 * blockSize + 100; // e.g. 16484 for 4 KiB blocks
    byte[] bytes = new byte[3 * mergeBufferSize];
    random().nextBytes(bytes);
    // write with the plain FSDirectory so that only the read side is exercised
    try (Directory fs = FSDirectory.open(path);
        IndexOutput out = fs.createOutput("file", IOContext.DEFAULT)) {
      out.writeBytes(bytes, 0, bytes.length);
    }
    try (Directory dir = directIO(path, mergeBufferSize);
        IndexInput in = dir.openInput("file", IOContext.DEFAULT)) {
      assertEquals(bytes.length, in.length());
      assertEquals(bytes[0], in.readByte());
      byte[] actual = new byte[bytes.length];
      in.seek(0);
      in.readBytes(actual, 0, actual.length);
      assertArrayEquals(bytes, actual);
    }
  }

  /** The same buffer size end to end: every merge reads its inputs through direct I/O. */
  public void testForceMergeWithBufferSizeNotAMultipleOfTheBlockSize() throws Exception {
    Path path = createTempDir("forceMerge");
    final int mergeBufferSize = 4 * blockSize(path) + 100;
    // minBytesDirect = 0 and the default useDirectIO(): merges use direct I/O, nothing else does
    try (Directory dir = new DirectIODirectory(FSDirectory.open(path), mergeBufferSize, 0L)) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setMergePolicy(new LogDocMergePolicy())
              .setMergeScheduler(new SerialMergeScheduler());
      iwc.setUseCompoundFile(false);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int seg = 0; seg < 3; seg++) {
          for (int i = 0; i < 100; i++) {
            Document doc = new Document();
            doc.add(new StringField("id", seg + "-" + i, Field.Store.YES));
            w.addDocument(doc);
          }
          w.commit();
        }
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        assertEquals(300, r.numDocs());
      }
    }
  }

  /**
   * A buffer size below the block size: the aligned output buffer has capacity 0 or one block,
   * depending on where the native allocation lands. Capacity 0 makes the first writeByte throw
   * BufferOverflowException. Several outputs are opened so that the outcome does not hinge on a
   * single allocation.
   */
  public void testWriteWithBufferSizeBelowTheBlockSize() throws Exception {
    Path path = createTempDir("belowBlock");
    final int mergeBufferSize = blockSize(path) / 4; // e.g. 1024 for 4 KiB blocks
    try (Directory dir = directIO(path, mergeBufferSize)) {
      for (int i = 0; i < 32; i++) {
        try (IndexOutput out = dir.createOutput("out" + i, IOContext.DEFAULT)) {
          out.writeByte((byte) 42);
        }
        assertEquals(1L, dir.fileLength("out" + i));
      }
    }
  }

  /**
   * Same configuration, writeBytes instead of writeByte: with a zero-capacity buffer the loop in
   * DirectIOIndexOutput.writeBytes makes no progress. The test bounds the wait itself (10 s per
   * output), then interrupts the writer, which closes the channel and ends the loop.
   */
  public void testWriteBytesWithBufferSizeBelowTheBlockSizeReturns() throws Exception {
    Path path = createTempDir("belowBlockBytes");
    final int mergeBufferSize = blockSize(path) / 4;
    try (Directory dir = directIO(path, mergeBufferSize)) {
      for (int i = 0; i < 32; i++) {
        final IndexOutput out = dir.createOutput("out" + i, IOContext.DEFAULT);
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread writer =
            new Thread(
                () -> {
                  try {
                    out.writeBytes(new byte[10], 0, 10);
                  } catch (Throwable t) {
                    failure.set(t);
                  } finally {
                    done.countDown();
                  }
                });
        writer.start();
        boolean returned = done.await(10, TimeUnit.SECONDS);
        if (returned == false) {
          writer.interrupt();
          writer.join();
        }
        try {
          out.close();
        } catch (IOException ignored) {
          // the interrupt closed the channel
        }
        assertTrue("writeBytes(10 bytes) did not return within 10 s (output " + i + ")", returned);
        assertNull(failure.get());
      }
    }
  }
}
