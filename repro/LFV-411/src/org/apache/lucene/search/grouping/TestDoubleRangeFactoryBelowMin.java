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

public class TestDoubleRangeFactoryBelowMin extends LuceneTestCase {

  public void testBelowMinRangeContainsValue() {
    DoubleRange range = new DoubleRangeFactory(-10, 5, 10).getRange(-20, null);
    // expected a [min, -10.0) range that contains -20; main returns DoubleRange(4.9E-324, -10.0)
    assertTrue(range + " should contain -20.0", range.min <= -20 && -20 < range.max);
  }
}
