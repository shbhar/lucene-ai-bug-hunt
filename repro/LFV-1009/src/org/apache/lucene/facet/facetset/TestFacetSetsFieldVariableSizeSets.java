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
package org.apache.lucene.facet.facetset;

import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFacetSetsFieldVariableSizeSets extends LuceneTestCase {

  private static FacetSet lengthPrefixed(long value) {
    int n = Math.max(1, (Long.SIZE - Long.numberOfLeadingZeros(value) + 7) / 8);
    return new FacetSet(1) {
      @Override
      public long[] getComparableValues() {
        return new long[] {value};
      }

      @Override
      public int packValues(byte[] buf, int start) {
        buf[start] = (byte) n;
        for (int i = 0; i < n; i++) {
          buf[start + 1 + i] = (byte) (value >>> (8 * (n - 1 - i)));
        }
        return 1 + n;
      }

      @Override
      public int sizePackedBytes() {
        return 1 + n;
      }
    };
  }

  public void testSmallerSetFirst() {
    // expected: create() packs 2 + 7 bytes; actual: ArrayIndexOutOfBounds (buffer = 4 + 2 * 2)
    FacetSet small = lengthPrefixed(7);
    FacetSet large = lengthPrefixed(1L << 40);
    try {
      FacetSetsField.create("f", small, large);
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new AssertionError(
          "expected a field for sets of "
              + small.sizePackedBytes()
              + " and "
              + large.sizePackedBytes()
              + " packed bytes, got "
              + e,
          e);
    }
  }
}
