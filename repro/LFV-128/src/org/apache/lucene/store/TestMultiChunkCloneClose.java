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

import java.io.IOException;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The clone/close scenarios of BaseChunkedDirectoryTestCase#testCloneClose and
 * TestMultiMMap#testCloneSafety, but on a file that is larger than the mmap chunk size, so that
 * the input is backed by several memory segments.
 */
public class TestMultiChunkCloneClose extends LuceneTestCase {

  private static final int CHUNK_SIZE = 16;
  private static final int FILE_LENGTH = 64; // 4 full chunks

  private static MMapDirectory newDirectory(String name) throws IOException {
    MMapDirectory dir = new MMapDirectory(createTempDir(name), CHUNK_SIZE);
    for (String file : new String[] {"bytes", "_0.cfs"}) {
      try (IndexOutput out = dir.createOutput(file, IOContext.DEFAULT)) {
        for (int i = 0; i < FILE_LENGTH; i++) {
          out.writeByte((byte) i);
        }
      }
    }
    return dir;
  }

  /** Closing a clone must not affect the original or other clones. */
  public void testCloseCloneThenReadOriginal() throws Exception {
    try (MMapDirectory dir = newDirectory("closeClone")) {
      IndexInput one = dir.openInput("bytes", IOContext.DEFAULT);
      IndexInput two = one.clone();
      IndexInput three = one.clone();
      two.close();
      one.seek(40); // a position in another chunk
      assertEquals(40, one.readByte());
      three.seek(50);
      assertEquals(50, three.readByte());
      one.close();
    }
  }

  /** A slice covering the whole file is a clone too. */
  public void testCloseFullLengthSliceThenReadOriginal() throws Exception {
    try (MMapDirectory dir = newDirectory("closeSlice")) {
      IndexInput one = dir.openInput("bytes", IOContext.DEFAULT);
      IndexInput all = one.slice("all", 0, one.length());
      all.close();
      one.seek(40);
      assertEquals(40, one.readByte());
      one.close();
    }
  }

  /** IndexInput#clone: reading a clone after the original was closed throws AlreadyClosedException. */
  public void testReadCloneAfterOriginalClosed() throws Exception {
    try (MMapDirectory dir = newDirectory("closeOriginal")) {
      IndexInput one = dir.openInput("bytes", IOContext.DEFAULT);
      IndexInput two = one.clone();
      one.close();
      expectThrows(AlreadyClosedException.class, two::readByte);
      expectThrows(AlreadyClosedException.class, () -> two.readBytes(new byte[40], 0, 40));
    }
  }

  /** Once every input is closed, no shared arena is left open (as in TestMMapDirectory#testArenas). */
  public void testArenaReleasedAfterCloneClose() throws Exception {
    try (MMapDirectory dir = newDirectory("arena")) {
      IndexInput one = dir.openInput("_0.cfs", IOContext.DEFAULT);
      IndexInput two = one.clone();
      two.close();
      try {
        one.seek(40);
      } catch (RuntimeException _) {
        // the failure reported by testCloseCloneThenReadOriginal
      }
      one.close();
      assertEquals(0, dir.arenas.size());
    }
  }
}
