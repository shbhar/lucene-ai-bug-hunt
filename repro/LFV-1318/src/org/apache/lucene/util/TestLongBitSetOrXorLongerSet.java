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
package org.apache.lucene.util;

import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLongBitSetOrXorLongerSet extends LuceneTestCase {

  public void testOrXorWithLongerSetInSameWord() {
    LongBitSet other = new LongBitSet(64);
    other.set(20);
    LongBitSet or = new LongBitSet(10);
    or.or(other);
    LongBitSet xor = new LongBitSet(10);
    xor.xor(other);
    // expected: both 10-bit sets stay empty [-1, -1]; actual: nextSetBit(0) returns 20 in both [20, 20]
    assertEquals(List.of(-1L, -1L), List.of(or.nextSetBit(0), xor.nextSetBit(0)));
  }
}
