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
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Reads past the end of a ByteBuffersDirectory file larger than one 1 KiB block: readShort, readInt
 * and readLong (and readShorts/readInts, which use them) throw IndexOutOfBoundsException instead of
 * EOFException. BaseDirectoryTestCase#testIntsUnderflow asserts EOFException for the same read on
 * every Directory, but its files are smaller than one block.
 */
public class TestByteBuffersReadPastEOF extends LuceneTestCase {

  private static Directory dirWithFile(int len) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    byte[] b = new byte[len];
    random().nextBytes(b);
    try (IndexOutput out = dir.createOutput("f", IOContext.DEFAULT)) {
      out.writeBytes(b, len);
    }
    return dir;
  }

  /** BaseDirectoryTestCase#testIntsUnderflow with the file moved past the first 1 KiB block. */
  public void testIntsUnderflowAfterFirstBlock() throws IOException {
    final int prefix = 1024;
    final int length = 4; // ints
    // 15 bytes after the prefix: four ints need 16
    try (Directory dir = dirWithFile(prefix + length * Integer.BYTES - 1);
        IndexInput in = dir.openInput("f", IOContext.DEFAULT)) {
      in.seek(prefix);
      expectThrows(EOFException.class, () -> in.readInts(new int[length], 0, length));
    }
  }

  public void testReadIntPastEOF() throws IOException {
    try (Directory dir = dirWithFile(1030);
        IndexInput in = dir.openInput("f", IOContext.DEFAULT)) {
      in.seek(1028); // 2 bytes left
      expectThrows(EOFException.class, in::readInt);
    }
  }

  public void testReadShortPastEOF() throws IOException {
    try (Directory dir = dirWithFile(1030);
        IndexInput in = dir.openInput("f", IOContext.DEFAULT)) {
      in.seek(1029); // 1 byte left
      expectThrows(EOFException.class, in::readShort);
    }
  }

  public void testReadLongPastEOF() throws IOException {
    try (Directory dir = dirWithFile(1030);
        IndexInput in = dir.openInput("f", IOContext.DEFAULT)) {
      in.seek(1025); // 5 bytes left
      expectThrows(EOFException.class, in::readLong);
    }
  }

  /** The same through ByteBuffersDataInput, whose readByte/readBytes/readLongs do throw EOF. */
  public void testDataInputReadIntPastEOF() throws IOException {
    ByteBuffersDataOutput out = new ByteBuffersDataOutput();
    out.writeBytes(new byte[1030]);
    ByteBuffersDataInput in = out.toDataInput();
    in.seek(1028);
    expectThrows(EOFException.class, in::readInt);
    in.seek(1028);
    expectThrows(EOFException.class, () -> in.readLongs(new long[1], 0, 1)); // control: passes
  }
}
