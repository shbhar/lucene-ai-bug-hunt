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

public class TestByteBuffersDataOutputBlockBits31 extends LuceneTestCase {

  // Expected: 31 block bits is rejected up front or holds a byte; actual: IAE "capacity < 0".
  public void testBlockBits31() {
    ByteBuffersDataOutput out;
    try {
      out =
          new ByteBuffersDataOutput(
              31, 31, ByteBuffersDataOutput.ALLOCATE_BB_ON_HEAP, ByteBuffersDataOutput.NO_REUSE);
    } catch (IllegalArgumentException _) {
      return;
    }
    out.writeByte((byte) 1);
  }
}
