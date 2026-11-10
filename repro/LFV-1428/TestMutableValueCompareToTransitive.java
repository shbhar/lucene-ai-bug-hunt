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
package org.apache.lucene.util.mutable;

import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMutableValueCompareToTransitive extends LuceneTestCase {

  // Expected: a < b and b < c imply a < c; actual on OpenJ9 (negative identity hashes): a > c.
  public void testCrossClassOrderIsTransitive() {
    MutableValue[] values = {
      new MutableValueBool(),
      new MutableValueDate(),
      new MutableValueDouble(),
      new MutableValueFloat(),
      new MutableValueInt(),
      new MutableValueLong(),
      new MutableValueStr()
    };
    for (MutableValue a : values) {
      for (MutableValue b : values) {
        for (MutableValue c : values) {
          if (a.compareTo(b) < 0 && b.compareTo(c) < 0) {
            assertTrue(name(a) + " < " + name(b) + " < " + name(c), a.compareTo(c) < 0);
          }
        }
      }
    }
  }

  private static String name(MutableValue v) {
    return v.getClass().getSimpleName() + "(class hash " + v.getClass().hashCode() + ")";
  }
}
