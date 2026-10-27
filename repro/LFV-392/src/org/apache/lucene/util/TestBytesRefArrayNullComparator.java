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

import java.io.IOException;
import java.util.Comparator;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * BytesRefArray#iterator(Comparator) javadoc: "If a non null Comparator is provided the iterator
 * will iterate the byte values in the order specified by the comparator. Otherwise the order is the
 * same as the values were appended." Public API only.
 */
public class TestBytesRefArrayNullComparator extends LuceneTestCase {

  private static BytesRefArray threeValues() {
    final BytesRefArray array = new BytesRefArray(Counter.newCounter());
    array.append(new BytesRef("c"));
    array.append(new BytesRef("a"));
    array.append(new BytesRef("b"));
    return array;
  }

  private static void assertAppendOrder(BytesRefIterator it) throws IOException {
    assertEquals(new BytesRef("c"), it.next());
    assertEquals(new BytesRef("a"), it.next());
    assertEquals(new BytesRef("b"), it.next());
    assertNull(it.next());
  }

  public void testNullComparatorIteratesInAppendOrder() throws IOException {
    final BytesRefArray array = threeValues();
    assertAppendOrder(array.iterator((Comparator<BytesRef>) null));
  }

  /** Control: the no-arg sugar ("iterator(Comparator) with a null comparator") keeps append order. */
  public void testNoArgIteratorControl() throws IOException {
    assertAppendOrder(threeValues().iterator());
  }
}
