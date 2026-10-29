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

public class TestIntroSorterHeapSortAtArrayEnd extends LuceneTestCase {

  public void testHeapSortFallbackStaysInSlice() {
    int[] values = {
      18, 55, 11, 58, 6, 15, 32, 0, 10, 21, 48, 7, 39, 16, 1, 53, 49, 23, 52, 17,
      51, 37, 46, 26, 42, 41, 40, 30, 38, 2, 36, 8, 34, 12, 56, 54, 3, 50, 9, 13,
      44, 19, 25, 27, 29, 31, 45, 59, 43, 22, 24, 28, 4, 14, 20, 57, 35, 47, 33, 5
    };
    int to = ArrayUtil.MAX_ARRAY_LENGTH, from = to - values.length;
    new IntroSorter() {
      int pivot;

      int value(int slot) {
        // expected: only slots in [from, to); actual: the heapSort fallback asks for slot < 0
        if (slot < from || slot >= to) {
          throw new AssertionError("slot " + slot + " is outside [" + from + ", " + to + ")");
        }
        return values[slot - from];
      }

      @Override
      protected void setPivot(int i) {
        pivot = value(i);
      }

      @Override
      protected int comparePivot(int j) {
        return Integer.compare(pivot, value(j));
      }

      @Override
      protected void swap(int i, int j) {
        int tmp = value(i);
        values[i - from] = value(j);
        values[j - from] = tmp;
      }
    }.sort(from, to);
  }
}
