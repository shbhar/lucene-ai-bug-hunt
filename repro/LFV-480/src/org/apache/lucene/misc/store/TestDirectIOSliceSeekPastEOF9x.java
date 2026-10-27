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
 * Releases up to 10.1 return BufferedIndexInput.wrap(...) from DirectIOIndexInput.slice, so a
 * direct-I/O slice (for example a compound-file entry read by a merge) accepts a seek far past its
 * end. Uses the 9.x/10.0/10.1 IOContext constructor.
 */
public class TestDirectIOSliceSeekPastEOF9x extends LuceneTestCase {
  public void testSliceSeekFarPastEOF() throws IOException {
    Path path = createTempDir();
    int blockSize = Math.toIntExact(Files.getFileStore(path).getBlockSize());
    try (Directory dir = new DirectIODirectory(FSDirectory.open(path), blockSize, 0L)) {
      try (IndexOutput out = dir.createOutput("f", IOContext.DEFAULT)) {
        out.writeBytes(new byte[3 * blockSize], 3 * blockSize);
      }
      try (IndexInput in = dir.openInput("f", new IOContext(new MergeInfo(1, 1, false, 1)))) {
        assumeTrue("direct I/O input expected", in.getClass().getName().contains("DirectIOIndexInput"));
        IndexInput slice = in.slice("s", 100, 1000);
        slice.seek(1000); // the end is legal
        expectThrows(EOFException.class, () -> slice.seek(1000 + 2L * blockSize));
      }
    }
  }
}
