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

/**
 * SortedSetDocValues ordinals are longs, but the default SortedSetDocValues#termsEnum() narrows the
 * ordinal to an int in seekExact(long) (and therefore in seekExact(BytesRef, TermState)), so for a
 * terms dictionary with more than 2^31 values it positions on a different (negative) ordinal.
 */
public class TestSortedSetDocValuesTermsEnumLongOrd extends LuceneTestCase {

  /**
   * A doc-values view with 5 * 2^30 (more than 2^32) distinct values and no documents; the value of ordinal o is the
   * 8-byte big-endian encoding of o, so values are sorted by ordinal. It keeps the default
   * termsEnum() and lookupTerm(), like MultiSortedSetDocValues and other views that do not provide
   * their own terms dictionary enum.
   */
  static final class ManyOrdsDocValues extends SortedSetDocValues {
    static final long VALUE_COUNT = 5L << 30;
    private final BytesRef scratch = new BytesRef(new byte[Long.BYTES]);
    private int doc = -1;

    static BytesRef encode(long ord) {
      byte[] b = new byte[Long.BYTES];
      BitUtil.VH_BE_LONG.set(b, 0, ord);
      return new BytesRef(b);
    }

    @Override
    public BytesRef lookupOrd(long ord) {
      if (ord < 0 || ord >= VALUE_COUNT) {
        throw new IllegalArgumentException("ord out of range: " + ord);
      }
      BitUtil.VH_BE_LONG.set(scratch.bytes, 0, ord);
      return scratch;
    }

    @Override
    public long getValueCount() {
      return VALUE_COUNT;
    }

    @Override
    public long nextOrd() {
      throw new IllegalStateException("no documents");
    }

    @Override
    public int docValueCount() {
      throw new IllegalStateException("no documents");
    }

    @Override
    public boolean advanceExact(int target) {
      doc = target;
      return false;
    }

    @Override
    public int docID() {
      return doc;
    }

    @Override
    public int nextDoc() {
      return doc = DocIdSetIterator.NO_MORE_DOCS;
    }

    @Override
    public int advance(int target) {
      return doc = DocIdSetIterator.NO_MORE_DOCS;
    }

    @Override
    public long cost() {
      return 0;
    }
  }

  private static final long ORD = (1L << 31) + 7; // within [0, getValueCount())

  public void testSeekExactByOrd() throws IOException {
    TermsEnum te = new ManyOrdsDocValues().termsEnum();
    te.seekExact(ORD);
    assertEquals("ord() after seekExact(" + ORD + ")", ORD, te.ord());
    assertEquals(ManyOrdsDocValues.encode(ORD), te.term());
  }

  /** From 2^32 on the narrowed ordinal is non-negative again: the enum silently lands on another term. */
  public void testSeekExactByOrdAbove2Pow32() throws IOException {
    long ord = (1L << 32) + 5;
    TermsEnum te = new ManyOrdsDocValues().termsEnum();
    te.seekExact(ord);
    assertEquals("ord() after seekExact(" + ord + ")", ord, te.ord());
    assertEquals(ManyOrdsDocValues.encode(ord), te.term());
  }

  public void testSeekExactByTermStateOfSameEnum() throws IOException {
    TermsEnum te = new ManyOrdsDocValues().termsEnum();
    BytesRef term = ManyOrdsDocValues.encode(ORD);
    assertEquals(TermsEnum.SeekStatus.FOUND, te.seekCeil(term)); // long arithmetic: correct
    assertEquals(ORD, te.ord());
    TermState state = te.termState(); // state of this same TermsEnum instance
    // move the same enum elsewhere
    assertEquals(TermsEnum.SeekStatus.FOUND, te.seekCeil(ManyOrdsDocValues.encode(0)));
    assertEquals(0, te.ord());
    te.seekExact(term, state); // back to the saved position
    assertEquals("ord() after seekExact(term, termState)", ORD, te.ord());
    assertEquals(term, te.term());
    assertEquals(ManyOrdsDocValues.encode(ORD + 1), te.next());
  }

}
