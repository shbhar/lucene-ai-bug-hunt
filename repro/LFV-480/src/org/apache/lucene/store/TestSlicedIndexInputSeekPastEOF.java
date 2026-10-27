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
 * IndexInput#seek: "If this is beyond the end of the file then this will throw EOFException". The
 * inputs returned by BufferedIndexInput.wrap, which is also the default BufferedIndexInput#slice,
 * accept any seek target.
 */
public class TestSlicedIndexInputSeekPastEOF extends LuceneTestCase {

  /** A minimal BufferedIndexInput that checks seeks itself but keeps the default slice(). */
  private static final class ArrayInput extends BufferedIndexInput {
    private final byte[] data;

    ArrayInput(byte[] data) {
      super("ArrayInput");
      this.data = data;
    }

    @Override
    protected void readInternal(ByteBuffer b) throws IOException {
      long pos = getFilePointer();
      if (pos + b.remaining() > data.length) {
        throw new EOFException("read past EOF");
      }
      b.put(data, (int) pos, b.remaining());
    }

    @Override
    protected void seekInternal(long pos) throws IOException {
      if (pos > data.length) {
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

  public void testWrapSeekPastEOF() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexOutput out = dir.createOutput("f", IOContext.DEFAULT)) {
        out.writeBytes(new byte[100], 100);
      }
      try (IndexInput in = dir.openInput("f", IOContext.DEFAULT)) {
        IndexInput wrapped = BufferedIndexInput.wrap("w", in, 10, 20);
        wrapped.seek(20); // seeking to the end is legal
        expectThrows(EOFException.class, () -> wrapped.seek(21));
      }
    }
  }

  public void testWrapSkipBytesPastEOF() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexOutput out = dir.createOutput("f", IOContext.DEFAULT)) {
        out.writeBytes(new byte[100], 100);
      }
      try (IndexInput in = dir.openInput("f", IOContext.DEFAULT)) {
        IndexInput wrapped = BufferedIndexInput.wrap("w", in, 10, 20);
        expectThrows(EOFException.class, () -> wrapped.skipBytes(21));
      }
    }
  }

  /** The default slice() of a BufferedIndexInput subclass: the subclass rejects the seek, its slice does not. */
  public void testDefaultSliceSeekPastEOF() throws IOException {
    ArrayInput in = new ArrayInput(new byte[100]);
    expectThrows(EOFException.class, () -> in.seek(101)); // control: the subclass checks
    IndexInput slice = in.slice("s", 0, 10);
    expectThrows(EOFException.class, () -> slice.seek(11));
  }
}
