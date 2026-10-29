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
package org.apache.lucene.search;

import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTopFieldCollectorManagerMaxNumHits extends LuceneTestCase {

  public void testNumHitsIntegerMaxValue() {
    Sort sort = new Sort(new SortField("price", SortField.Type.LONG));
    // Expected: PriorityQueue's IllegalArgumentException (as in 10.x); actual: OutOfMemoryError.
    expectThrows(
        IllegalArgumentException.class,
        () -> new TopFieldCollectorManager(sort, Integer.MAX_VALUE, null, 1).newCollector());
  }
}
