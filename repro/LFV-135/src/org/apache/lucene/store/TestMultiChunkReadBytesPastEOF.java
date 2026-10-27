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
import java.util.Locale;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * TestMultiMMap#testSeekingExceptions checks that seeking past EOF on a multi-chunk slice throws
 * EOFException naming the requested position. The same files and slice, with absolute reads
 * through RandomAccessInput.
 */
public class TestMultiChunkReadBytesPastEOF extends LuceneTestCase {

  private static final int CHUNK_SIZE = 128;
  private static final int SIZE = 128 + 63;

  private static IndexInput open(Directory dir) throws Exception {
    try (IndexOutput out = dir.createOutput("a", IOContext.DEFAULT)) {
      for (int i = 0; i < SIZE; ++i) {
        out.writeByte((byte) 0);
      }
    }
    return dir.openInput("a", IOContext.DEFAULT);
  }

  private static RandomAccessInput slice(IndexInput in) throws Exception {
    return in.randomAccessSlice(33, CHUNK_SIZE + 15); // spans two chunks, as in the upstream test
  }

  public void testReadBytesPastEOFNamesThePosition() throws Exception {
    try (Directory dir = new MMapDirectory(createTempDir(), CHUNK_SIZE);
        IndexInput in = open(dir)) {
      RandomAccessInput slice = slice(in);
      final long posAfterEOF = SIZE + 123;
      String expected = String.format(Locale.ROOT, "(pos=%d)", posAfterEOF);
      // readByte(long) gets it right
      var eof = expectThrows(EOFException.class, () -> slice.readByte(posAfterEOF));
      assertTrue(eof.getMessage(), eof.getMessage().contains(expected));
      // readBytes(long, ...) does not
      eof = expectThrows(EOFException.class, () -> slice.readBytes(posAfterEOF, new byte[1], 0, 1));
      assertTrue("wrong position in error message: " + eof, eof.getMessage().contains(expected));
    }
  }

  public void testReadBytesPastEOFThrowsEOFException() throws Exception {
    try (Directory dir = new MMapDirectory(createTempDir(), CHUNK_SIZE);
        IndexInput in = open(dir)) {
      RandomAccessInput slice = slice(in);
      final long posAfterEOF = 2 * CHUNK_SIZE + 5 - 33; // 228, the slice has 143 bytes
      expectThrows(EOFException.class, () -> slice.readByte(posAfterEOF));
      expectThrows(EOFException.class, () -> slice.readBytes(posAfterEOF, new byte[1], 0, 1));
    }
  }
}
