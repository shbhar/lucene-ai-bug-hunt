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

public class TestBytesRefArrayIteratorNullComparator extends LuceneTestCase {

  public void testNullComparatorIteratesInAppendOrder() throws IOException {
    BytesRefArray array = new BytesRefArray(Counter.newCounter());
    array.append(new BytesRef("b"));
    array.append(new BytesRef("a"));
    BytesRefIterator it;
    try {
      it = array.iterator((Comparator<BytesRef>) null);
    } catch (NullPointerException e) {
      throw new AssertionError("iterator(null): expected append order b, a, got " + e, e);
    }
    assertEquals(new BytesRef("b"), it.next());
    assertEquals(new BytesRef("a"), it.next());
    assertNull(it.next());
  }
}
