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
package org.apache.lucene.index;

import java.io.IOException;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BitUtil;
import org.apache.lucene.util.BytesRef;

public class TestSortedSetTermsEnumSeekExactLongOrd extends LuceneTestCase {

  public void testSeekExactOrdAbove2Pow32() throws IOException {
    long ord = (1L << 32) + 5;
    SortedSetDocValues values =
        new SortedSetDocValues() {
          @Override
          public long getValueCount() {
            return ord + 1;
          }

          @Override
          public BytesRef lookupOrd(long o) {
            byte[] term = new byte[Long.BYTES];
            BitUtil.VH_BE_LONG.set(term, 0, o);
            return new BytesRef(term);
          }

          @Override
          public long nextOrd() {
            throw new UnsupportedOperationException();
          }

          @Override
          public int docValueCount() {
            throw new UnsupportedOperationException();
          }

          @Override
          public boolean advanceExact(int target) {
            return false;
          }

          @Override
          public int docID() {
            return -1;
          }

          @Override
          public int nextDoc() {
            return DocIdSetIterator.NO_MORE_DOCS;
          }

          @Override
          public int advance(int target) {
            return DocIdSetIterator.NO_MORE_DOCS;
          }

          @Override
          public long cost() {
            return 0;
          }
        };
    TermsEnum te = values.termsEnum();
    te.seekExact(ord);
    // expected: positioned on ord 4294967301; actual: silently positioned on ord 5
    assertEquals("ord() after seekExact(" + ord + ")", ord, te.ord());
  }
}
