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

public class TestSparseFixedBitSetRamBytesUsed extends LuceneTestCase {

  public void testClearReleasesBlockBytes() {
    SparseFixedBitSet set = new SparseFixedBitSet(1);
    long fresh = set.ramBytesUsed();
    set.set(0);
    set.clear(0);
    // clear(0) empties the block and drops its long[], so the estimate should be the fresh value.
    // Expected: 88 (fresh); actual: 112 (the dropped array's 24 bytes stay counted).
    assertEquals(fresh, set.ramBytesUsed());
  }
}
