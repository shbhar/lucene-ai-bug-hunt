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

public class TestL2NormalizeSquaredNormOverflow extends LuceneTestCase {

  public void testFiniteVectorWithOverflowingSquaredNorm() {
    // Expected the unit vector [0.5, 0.5, 0.5, 0.5]; actual [0, 0, 0, 0] (the float sum of squares overflows).
    float[] v = {1e19f, 1e19f, 1e19f, 1e19f};
    VectorUtil.l2normalize(v);
    assertArrayEquals(new float[] {0.5f, 0.5f, 0.5f, 0.5f}, v, 1e-6f);
  }
}
