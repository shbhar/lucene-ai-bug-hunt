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
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Constants;

public class TestMultiChunkSliceReadAdvice extends LuceneTestCase {

  public void testRandomAdviceStaysInsideTheSlice() throws Exception {
    assumeTrue("needs Linux madvise", Constants.LINUX && MMapDirectory.supportsMadvise());
    int chunk = 1 << 20;
    Path path = createTempDir();
    try (MMapDirectory dir = new MMapDirectory(path, chunk)) {
      dir.setReadAdvice(MMapDirectory.ADVISE_BY_CONTEXT);
      try (IndexOutput out = dir.createOutput("data", IOContext.DEFAULT)) {
        out.writeBytes(new byte[2 * chunk], 2 * chunk);
      }
      try (IndexInput in = dir.openInput("data", IOContext.DEFAULT)) {
        long start = chunk / 2, end = start + chunk;
        in.slice("inner", start, chunk, IOContext.DEFAULT.withHints(DataAccessHint.RANDOM));
        String file = path.resolve("data").toRealPath().toString();
        long mapStart = -1, mapEnd = -1;
        for (String line : Files.readAllLines(Path.of("/proc/self/smaps"))) {
          String[] parts = line.split("\\s+");
          if (line.endsWith(" " + file)) {
            String[] addr = parts[0].split("-");
            mapStart = Long.parseLong(parts[2], 16);
            mapEnd = mapStart + Long.parseLong(addr[1], 16) - Long.parseLong(addr[0], 16);
          } else if (line.startsWith("VmFlags:") && mapStart >= 0) {
            assertTrue(
                "expected RANDOM advice only inside the slice ["
                    + start
                    + ", "
                    + end
                    + "), but file bytes ["
                    + mapStart
                    + ", "
                    + mapEnd
                    + ") got it",
                (line + " ").contains(" rr ") == false || (mapStart >= start && mapEnd <= end));
            mapStart = -1;
          }
        }
      }
    }
  }
}
