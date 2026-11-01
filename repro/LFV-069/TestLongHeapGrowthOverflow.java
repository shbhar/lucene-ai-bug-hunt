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

public class TestLongHeapGrowthOverflow extends LuceneTestCase {

  // push(long): "Grows unbounded as needed to accommodate new values."
  public void testPushGrowsFullHeap() {
    // long[715_827_883] holding 715_827_882 values: full, so the next push must grow the array
    LongHeap heap = new LongHeap(715_827_882, 0L);
    heap.push(1L);
    assertEquals(715_827_883, heap.size());
    assertEquals(0L, heap.top());
  }
}
