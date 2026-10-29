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

public class TestFixedBitSetApproximateCardinalityOverflow extends LuceneTestCase {

  public void testIntegerMaxValueBitsOneSet() {
    FixedBitSet bits = new FixedBitSet(Integer.MAX_VALUE);
    bits.set(0);
    int approx = bits.approximateCardinality();
    // Expected: a non-negative estimate for 1 set bit; actual: -64 (64 * numWords overflows int).
    assertTrue("expected approximateCardinality() >= 0 but was " + approx, approx >= 0);
  }
}
