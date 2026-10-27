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
 * BaseDirectoryTestCase#testSeekPastEOF ("Seeking past EOF should always throw EOFException"), on
 * an input with several mmap chunks that has already hit EOF once.
 */
public class TestMultiChunkSeekPastEOFAfterEOF extends LuceneTestCase {

  private static final int CHUNK_SIZE = 16;
  private static final int FILE_LENGTH = 40; // chunks of 16, 16 and 8 bytes

  private static Directory newMultiChunkDirectory() throws IOException {
    Directory dir = new MMapDirectory(createTempDir("seekPastEOF"), CHUNK_SIZE);
    try (IndexOutput out = dir.createOutput("out", IOContext.DEFAULT)) {
      for (int i = 0; i < FILE_LENGTH; i++) {
        out.writeByte((byte) i);
      }
    }
    return dir;
  }

  public void testSeekPastEOFAfterReadPastEOF() throws Exception {
    try (Directory dir = newMultiChunkDirectory();
        IndexInput in = dir.openInput("out", IOContext.DEFAULT)) {
      in.seek(FILE_LENGTH);
      expectThrows(EOFException.class, in::readByte);
      // Seeking past EOF should always throw EOFException
      expectThrows(EOFException.class, () -> in.seek(FILE_LENGTH + 10));
    }
  }

  public void testReadAfterSeekPastEOF() throws Exception {
    try (Directory dir = newMultiChunkDirectory();
        IndexInput in = dir.openInput("out", IOContext.DEFAULT)) {
      byte[] all = new byte[FILE_LENGTH + 1];
      expectThrows(EOFException.class, () -> in.readBytes(all, 0, all.length));
      try {
        in.seek(FILE_LENGTH + 10);
      } catch (EOFException _) {
        return; // correct
      }
      long fp = in.getFilePointer();
      byte b = in.readByte();
      fail(
          "seek("
              + (FILE_LENGTH + 10)
              + ") on a "
              + FILE_LENGTH
              + "-byte file succeeded: getFilePointer()="
              + fp
              + ", readByte()="
              + b);
    }
  }
}
