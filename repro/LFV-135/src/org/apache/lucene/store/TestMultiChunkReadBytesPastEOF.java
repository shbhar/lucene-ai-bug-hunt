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
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMultiChunkReadBytesPastEOF extends LuceneTestCase {

  public void testReadBytesPastEOFThrowsEOFException() throws Exception {
    // 128-byte chunks, a 191-byte file and a 143-byte view at offset 33, as in TestMultiMMap
    try (Directory dir = new MMapDirectory(createTempDir(), 128)) {
      try (IndexOutput out = dir.createOutput("a", IOContext.DEFAULT)) {
        out.writeBytes(new byte[191], 191);
      }
      try (IndexInput in = dir.openInput("a", IOContext.DEFAULT)) {
        RandomAccessInput view = in.randomAccessSlice(33, 143);
        EOFException e =
            expectThrows(EOFException.class, () -> view.readBytes(228, new byte[1], 0, 1));
        assertTrue(e.getMessage(), e.getMessage().contains("(pos=228)"));
      }
    }
  }
}
