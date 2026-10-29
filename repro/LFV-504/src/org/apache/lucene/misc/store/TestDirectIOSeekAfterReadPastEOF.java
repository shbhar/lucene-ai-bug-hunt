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
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDirectIOSeekAfterReadPastEOF extends LuceneTestCase {

  public void testLegalSeekAfterReadPastEOF() throws Exception {
    Path path = createTempDir();
    int block = Math.toIntExact(Files.getFileStore(path).getBlockSize());
    try (Directory dir = new DirectIODirectory(FSDirectory.open(path), block, 0)) {
      try (IndexOutput out = dir.createOutput("f", IOContext.DEFAULT)) {
        for (int i = 0; i < block + 4; i++) {
          out.writeByte((byte) (i % 251));
        }
      }
      try (IndexInput in = dir.openInput("f", IOContext.merge())) {
        in.seek(block - 1);
        expectThrows(EOFException.class, () -> in.readBytes(new byte[17], 0, 17));
        in.seek(block + 1);
        // expected: the byte at position block + 1; actual: the byte at position 1 (stale first block)
        assertEquals((byte) ((block + 1) % 251), in.readByte());
      }
    }
  }
}
