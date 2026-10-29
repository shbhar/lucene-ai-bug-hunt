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
package org.apache.lucene.search.grouping;

import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDoubleRangeFactoryContainsValue extends LuceneTestCase {

  public void testRangeContainsValue() {
    DoubleRange range = new DoubleRangeFactory(0, 0.1, 10).getRange(0.6, null);
    // expected: a range [min, max) that contains 0.6; actual: DoubleRange(0.5, 0.6)
    assertTrue(range + " should contain 0.6", range.min <= 0.6 && 0.6 < range.max);
  }
}
