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
package org.apache.lucene.internal.hppc;

import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFloatArrayListSignedZeroHashCode extends LuceneTestCase {

  public void testEqualListsHaveEqualHashCodes() {
    FloatArrayList a = FloatArrayList.from(0.0f);
    FloatArrayList b = FloatArrayList.from(-0.0f);
    // expected: equal lists have equal hash codes; actual: equals is true but hashCode is 31 vs 1832674751
    if (a.equals(b)) {
      assertEquals(a.hashCode(), b.hashCode());
    }
  }
}
