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

/**
 * The javadoc of ArrayUtil.hashCode(char[], int, int) says "Returns hash of chars in range start
 * (inclusive) to end (inclusive)", but the loop {@code for (int i = end - 1; i >= start; i--)}
 * treats end as exclusive, and the only in-tree caller (CharTermAttributeImpl.hashCode) passes an
 * exclusive end (termLength). This test asserts the documented (inclusive) reading, so it fails:
 * the defect is in the javadoc, not the code.
 */
public class TestArrayUtilHashCodeRangeJavadoc extends LuceneTestCase {

  public void testSingleCharRangeIsHashedPerJavadoc() {
    char[] chars = {'x', 'y'};
    // [0, 0] inclusive is the one char 'x'; any hash of a non-empty char range that the loop
    // computes is 'x' here (code = 0 * 31 + 'x'), and the empty range hashes to 0.
    assertEquals("hash of range [0, 0] (inclusive per javadoc)", 'x', ArrayUtil.hashCode(chars, 0, 0));
  }

  public void testLastCharOfInclusiveRangeContributes() {
    char[] a = {'x', 'y'};
    char[] b = {'x', 'z'};
    // Per the javadoc, [0, 1] inclusive covers both chars, so the second char must matter.
    assertNotEquals(
        "ranges [0, 1] of \"xy\" and \"xz\" hash the same",
        ArrayUtil.hashCode(a, 0, 1),
        ArrayUtil.hashCode(b, 0, 1));
  }
}
