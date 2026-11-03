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
package org.apache.lucene.codecs.compressing;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import org.apache.lucene.store.ByteBuffersDataInput;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCompressorInputOver4GiB extends LuceneTestCase {

  public void testCompressInputOver4GiB() throws IOException {
    ByteBuffer block = ByteBuffer.allocate(1 << 20);
    ByteBuffersDataInput in = new ByteBuffersDataInput(Collections.nCopies(4097, block));
    try (Compressor compressor = CompressionMode.FAST.newCompressor()) {
      compressor.compress(in, new ByteBuffersDataOutput());
    } catch (ArithmeticException ignored) {
      return;
    }
    // expected: all 2^32 + 2^20 bytes consumed (or an exception); actual: returns after consuming 2^20
    assertEquals(in.length(), in.position());
  }
}
