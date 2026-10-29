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

public class TestBufferedIndexInputWrapSeekPastEOF extends LuceneTestCase {

  public void testWrapSeekPastEOF() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexOutput out = dir.createOutput("f", IOContext.DEFAULT)) {
        out.writeBytes(new byte[30], 30);
      }
      try (IndexInput in = dir.openInput("f", IOContext.DEFAULT)) {
        IndexInput wrapped = BufferedIndexInput.wrap("w", in, 0, 20);
        // IndexInput#seek: EOFException beyond the end; actual: seek(21) on 20 bytes succeeds
        expectThrows(EOFException.class, () -> wrapped.seek(21));
      }
    }
  }
}
