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

import java.util.AbstractList;
import java.util.Comparator;
import java.util.Objects;
import java.util.RandomAccess;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTimSortGallopOverflow extends LuceneTestCase {

  public void testGallopOverMoreThan2Pow30() {
    // Expected: 64 true then 2^30+8 false sorts fine; actual: IndexOutOfBoundsException (gallop step wraps negative).
    final int size = (1 << 30) + 72;
    final FixedBitSet bits = new FixedBitSet(size);
    bits.set(0, 64);
    class BitList extends AbstractList<Boolean> implements RandomAccess {
      @Override
      public Boolean get(int i) {
        return bits.get(Objects.checkIndex(i, size));
      }

      @Override
      public Boolean set(int i, Boolean value) {
        boolean old = get(i);
        if (value) bits.set(i);
        else bits.clear(i);
        return old;
      }

      @Override
      public int size() {
        return size;
      }
    }
    CollectionUtil.timSort(new BitList(), Comparator.naturalOrder());
    assertEquals(size - 64, bits.nextSetBit(0));
  }
}
