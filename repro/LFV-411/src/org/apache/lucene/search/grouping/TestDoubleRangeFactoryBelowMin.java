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

/**
 * DoubleRange is "a contiguous range of double values, with an inclusive minimum and exclusive
 * maximum"; DoubleRangeFactory groups "all doubles below" min into one range. The range returned
 * for a value must contain that value.
 */
public class TestDoubleRangeFactoryBelowMin extends LuceneTestCase {

  private static void assertContains(DoubleRange r, double v) {
    assertTrue(r + " should contain " + v, r.min <= v && v < r.max);
  }

  public void testBelowMin() {
    DoubleRangeFactory factory = new DoubleRangeFactory(-10, 5, 10);
    // Double.MIN_VALUE is the smallest positive double, so this range is [4.9E-324, -10.0)
    assertContains(factory.getRange(-20, null), -20);
  }
}
