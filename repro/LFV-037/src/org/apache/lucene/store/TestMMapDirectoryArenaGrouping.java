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
import java.lang.foreign.MemorySegment;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

public class TestMMapDirectoryArenaGrouping extends LuceneTestCase {

  public void testOpenAfterOldArenaClosedSharesNewerArena() throws IOException {
    try (MMapDirectory dir = new MMapDirectory(createTempDir())) {
      try (IndexOutput out = dir.createOutput("_0.dat", IOContext.DEFAULT)) {
        out.writeLong(42);
      }
      IndexInput inA1 = dir.openInput("_0.dat", IOContext.DEFAULT);
      for (int i = 1; i < RefCountedSharedArena.DEFAULT_MAX_PERMITS; i++) {
        dir.openInput("_0.dat", IOContext.DEFAULT).close();
      }
      IndexInput inA2 = dir.openInput("_0.dat", IOContext.DEFAULT);
      inA1.close();
      IndexInput next = dir.openInput("_0.dat", IOContext.DEFAULT);
      // expected: next shares inA2's arena (closing A1 must not unregister A2); actual: new arena
      assertTrue(
          "expected the next open of _0 to share inA2's open arena, but it got a new arena",
          arena(inA2).equals(arena(next)));
      IOUtils.close(inA2, next);
    }
  }

  private static MemorySegment.Scope arena(IndexInput in) throws IOException {
    return ((MemorySegmentAccessInput) in).segmentSliceOrNull(0, Long.BYTES).scope();
  }
}
