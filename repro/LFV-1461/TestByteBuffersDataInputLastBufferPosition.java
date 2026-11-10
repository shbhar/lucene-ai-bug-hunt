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

import java.nio.ByteBuffer;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestByteBuffersDataInputLastBufferPosition extends LuceneTestCase {

  public void testPositionedLastBuffer() throws Exception {
    // Expected: IllegalArgumentException or bytes {1, 2, 3}; actual: bytes {1, 2, 1}.
    byte[] data = {1, 2, 3};
    ByteBuffersDataInput in;
    try {
      in =
          new ByteBuffersDataInput(
              List.of(ByteBuffer.wrap(data, 0, 2), ByteBuffer.wrap(data, 2, 1)));
    } catch (IllegalArgumentException e) {
      return;
    }
    byte[] read = new byte[3];
    in.readBytes(read, 0, 3);
    assertArrayEquals(data, read);
  }
}
