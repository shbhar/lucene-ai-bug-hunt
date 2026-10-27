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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Constants;

/**
 * IndexInput#slice(String, long, long, IOContext) applies the context's read advice to the pages of
 * the slice. For a slice that spans two mmap chunks, check which pages of the file got the advice,
 * using the random-read flag ("rr") that Linux reports per mapping in /proc/self/smaps.
 */
public class TestMultiChunkSliceReadAdvice extends LuceneTestCase {

  public void testRandomAdviceStaysInsideTheSlice() throws Exception {
    assumeTrue("needs madvise", MMapDirectory.supportsMadvise());
    assumeTrue("reads /proc/self/smaps", Constants.LINUX);
    final int chunkSize = 1 << 20; // a multiple of the page size
    final Path path = createTempDir("sliceReadAdvice");
    try (MMapDirectory dir = new MMapDirectory(path, chunkSize)) {
      dir.setReadAdvice(MMapDirectory.ADVISE_BY_CONTEXT);
      try (IndexOutput out = dir.createOutput("data", IOContext.DEFAULT)) {
        out.writeBytes(new byte[3 * chunkSize], 3 * chunkSize);
      }
      // IOContext.DEFAULT maps to the NORMAL advice: nothing is advised when the file is opened
      try (IndexInput in = dir.openInput("data", IOContext.DEFAULT)) {
        final long sliceStart = chunkSize / 2;
        final long sliceEnd = sliceStart + chunkSize; // the slice spans chunks 0 and 1
        IndexInput slice =
            in.slice(
                "inner",
                sliceStart,
                sliceEnd - sliceStart,
                IOContext.DEFAULT.withHints(DataAccessHint.RANDOM));
        assertEquals(chunkSize, slice.length());

        List<long[]> advised = randomReadFileRanges(path.resolve("data").toRealPath().toString());
        assertFalse("nothing was advised", advised.isEmpty());
        for (long[] range : advised) {
          assertTrue(
              "file bytes ["
                  + range[0]
                  + ", "
                  + range[1]
                  + ") got the RANDOM advice, but the slice is ["
                  + sliceStart
                  + ", "
                  + sliceEnd
                  + ")",
              range[0] >= sliceStart && range[1] <= sliceEnd);
        }
      }
    }
  }

  /** File offset ranges [start, end) of the mappings of {@code file} that carry the "rr" flag. */
  private static List<long[]> randomReadFileRanges(String file) throws Exception {
    List<long[]> ranges = new ArrayList<>();
    long[] current = null;
    for (String line : Files.readAllLines(Path.of("/proc/self/smaps"))) {
      String[] parts = line.trim().split("\\s+");
      if (parts.length >= 6 && parts[0].matches("[0-9a-f]+-[0-9a-f]+")) {
        current = null;
        if (line.endsWith(" " + file)) {
          String[] addr = parts[0].split("-");
          long size = Long.parseUnsignedLong(addr[1], 16) - Long.parseUnsignedLong(addr[0], 16);
          long fileOffset = Long.parseUnsignedLong(parts[2], 16);
          current = new long[] {fileOffset, fileOffset + size};
        }
      } else if (current != null && line.startsWith("VmFlags:")) {
        if (List.of(line.substring("VmFlags:".length()).trim().split("\\s+")).contains("rr")) {
          ranges.add(current);
        }
        current = null;
      }
    }
    return ranges;
  }
}
