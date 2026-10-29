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

import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFixedBitSetEnsureCapacityOverflow extends LuceneTestCase {

  public void testEnsureCapacityTo2BillionBits() {
    // Expected: length() > 2,000,000,000; actual: negative (-da), or AIOOBE from the -ea check.
    FixedBitSet bits;
    try {
      bits = FixedBitSet.ensureCapacity(new FixedBitSet(64), 2_000_000_000);
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new AssertionError("ensureCapacity(bits, 2000000000) threw " + e, e);
    }
    assertTrue(
        "expected length() > 2000000000 but was " + bits.length(), bits.length() > 2_000_000_000);
  }
}
