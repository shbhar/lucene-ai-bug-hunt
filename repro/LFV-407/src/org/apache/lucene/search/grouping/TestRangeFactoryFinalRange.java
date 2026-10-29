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

public class TestRangeFactoryFinalRange extends LuceneTestCase {

  // 22 is in the final range [20, 25) below max 25 ("may be up to this width"); actual [20, 30)
  public void testLongRangeFactory() {
    assertEquals(new LongRange(20, 25), new LongRangeFactory(10, 10, 25).getRange(22, null));
  }

  public void testDoubleRangeFactory() {
    assertEquals(new DoubleRange(20, 25), new DoubleRangeFactory(10, 10, 25).getRange(22, null));
  }
}
