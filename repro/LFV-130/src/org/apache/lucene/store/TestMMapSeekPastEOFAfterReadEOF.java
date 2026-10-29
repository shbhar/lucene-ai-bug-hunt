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

public class TestMMapSeekPastEOFAfterReadEOF extends LuceneTestCase {

  public void testSeekPastEOFAfterReadEOF() throws Exception {
    try (Directory dir = new MMapDirectory(createTempDir(), 16)) {
      try (IndexOutput out = dir.createOutput("f", IOContext.DEFAULT)) {
        for (int i = 0; i < 40; i++) {
          out.writeByte((byte) i);
        }
      }
      try (IndexInput in = dir.openInput("f", IOContext.DEFAULT)) {
        in.seek(40);
        expectThrows(EOFException.class, in::readByte);
        try {
          in.seek(50);
        } catch (EOFException expected) {
          return;
        }
        fail(
            "expected EOFException from seek(50) on a 40-byte file, but seek succeeded and readByte()="
                + in.readByte());
      }
    }
  }
}
