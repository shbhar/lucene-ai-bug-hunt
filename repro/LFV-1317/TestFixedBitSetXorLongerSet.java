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

public class TestFixedBitSetXorLongerSet extends LuceneTestCase {

  public void testXorWithLongerSetInSameWord() {
    FixedBitSet a = new FixedBitSet(10);
    FixedBitSet b = new FixedBitSet(20);
    b.set(15);
    a.xor(b);
    // expected: a stays empty (cardinality 0); actual: b's bit 15 lands in a's ghost bits
    // (cardinality 1)
    assertEquals(0, a.cardinality());
  }
}
