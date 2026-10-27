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
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.MergeInfo;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * fill() throws EOFException "early to maintain buffer state (position in particular)" (its own
 * comment), but it advances filePos before that check. After the EOFException, a seek back to a
 * legal position inside the old buffer and a read return bytes of another position, or the read
 * fails with EOFException although the position is legal.
 */
public class TestDirectIOStateAfterEOF extends LuceneTestCase {

  private static byte expected(long pos) {
    return (byte) (pos ^ (pos >>> 8) ^ (pos >>> 16) ^ 0x5A);
  }

  private interface Body {
    void run(IndexInput in, int blockSize) throws IOException;
  }

  private void withFile(int len, Body body) throws IOException {
    withFile(len, 1, body);
  }

  private void withFile(int len, int blocksPerBuffer, Body body) throws IOException {
    Path path = createTempDir();
    int blockSize = Math.toIntExact(Files.getFileStore(path).getBlockSize());
    try (Directory dir =
        new DirectIODirectory(FSDirectory.open(path), blocksPerBuffer * blockSize, 0L)) {
      try (IndexOutput out = dir.createOutput("f", IOContext.DEFAULT)) {
        for (int i = 0; i < len; i++) {
          out.writeByte(expected(i));
        }
      }
      try (IndexInput in = dir.openInput("f", IOContext.merge(new MergeInfo(1, 1, false, 1)))) {
        assumeTrue("direct I/O input expected", in.getClass().getName().contains("DirectIOIndexInput"));
        body.run(in, blockSize);
      }
    }
  }

  /** seek past the end of a slice, then a legal seek and a read: the wrong byte comes back. */
  public void testSliceSeekBackAfterSeekPastEOF() throws IOException {
    withFile(
        4100,
        (in, blockSize) -> {
          assumeTrue("4096-byte blocks", blockSize == 4096);
          IndexInput slice = in.slice("s", 1, 4098);
          slice.seek(4095);
          expectThrows(EOFException.class, () -> slice.seek(4107));
          slice.seek(4097); // legal
          assertEquals("byte at slice position 4097 (file position 4098)", expected(4098), slice.readByte());
        });
  }

  /** The same on a clone of the top-level input, with a two-block buffer. */
  public void testCloneSeekBackAfterSeekPastEOF() throws IOException {
    withFile(
        4100,
        2,
        (in, blockSize) -> {
          assumeTrue("4096-byte blocks", blockSize == 4096);
          IndexInput clone = in.clone();
          clone.seek(4099);
          expectThrows(EOFException.class, () -> clone.seek(4109));
          clone.seek(4096); // legal
          assertEquals("byte at position 4096", expected(4096), clone.readByte());
        });
  }

  /** A read past the end (no seek past it): afterwards a legal position cannot be read. */
  public void testReadAfterReadPastEOF() throws IOException {
    withFile(
        1,
        (in, blockSize) -> {
          in.seek(0);
          expectThrows(EOFException.class, () -> in.readFloats(new float[2], 0, 2));
          in.seek(0); // legal
          assertEquals("byte at position 0", expected(0), in.readByte());
        });
  }
}
