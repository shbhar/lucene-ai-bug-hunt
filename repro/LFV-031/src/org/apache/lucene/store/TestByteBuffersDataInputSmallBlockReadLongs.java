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

import org.apache.lucene.tests.util.LuceneTestCase;

public class TestByteBuffersDataInputSmallBlockReadLongs extends LuceneTestCase {

  public void testReadLongsWithTwoByteBlocks() throws Exception {
    ByteBuffersDataOutput out =
        new ByteBuffersDataOutput(
            1, 1, ByteBuffersDataOutput.ALLOCATE_BB_ON_HEAP, ByteBuffersDataOutput.NO_REUSE);
    out.writeBytes(new byte[3]);
    out.writeLong(42L);
    ByteBuffersDataInput in = out.toDataInput();
    in.skipBytes(3);
    long[] dst = new long[1];
    try {
      in.readLongs(dst, 0, 1);
    } catch (IllegalArgumentException e) {
      // expected: {42}, as readLong() returns; actual: IAE, a 2-byte block is positioned at 3
      throw new AssertionError("expected readLongs to return {42}, got " + e, e);
    }
    assertEquals(42L, dst[0]);
  }
}
