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
package org.apache.lucene.search;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;

/**
 * A multi-dimensional PointInSetQuery must match every document whose point is in the set. The
 * segment-level early exit added by GITHUB#14268 compares, per dimension, the first and the last
 * set point in sorted (lexicographic) order with the segment's min/max packed values. Those two
 * points bound only the first dimension, so for a set such as {(0,1), (1,0)} the second
 * dimension's "lower bound" is 1 although the set contains a point with 0 there: a segment whose
 * only point is (1,0) is skipped.
 */
public class TestPointInSetQueryMultiDimEarlyExit extends LuceneTestCase {

  /** Same shape as TestPointQueries#newMultiDimIntSetQuery: points given as flat (x, y) pairs. */
  private static Query newMultiDimIntSetQuery(String field, int numDims, int... flat) {
    List<byte[]> packed = new ArrayList<>();
    for (int i = 0; i < flat.length; i += numDims) {
      byte[] p = new byte[numDims * Integer.BYTES];
      for (int d = 0; d < numDims; d++) {
        NumericUtils.intToSortableBytes(flat[i + d], p, d * Integer.BYTES);
      }
      packed.add(p);
    }
    packed.sort((a, b) -> new BytesRef(a).compareTo(new BytesRef(b)));
    Iterator<byte[]> it = packed.iterator();
    return new PointInSetQuery(
        field,
        numDims,
        Integer.BYTES,
        new PointInSetQuery.Stream() {
          @Override
          public BytesRef next() {
            return it.hasNext() ? new BytesRef(it.next()) : null;
          }
        }) {
      @Override
      protected String toString(byte[] value) {
        StringBuilder sb = new StringBuilder();
        for (int d = 0; d < numDims; d++) {
          sb.append(d == 0 ? "" : ",").append(NumericUtils.sortableBytesToInt(value, d * 4));
        }
        return sb.toString();
      }
    };
  }

  public void testAntiCorrelatedSetPointsDoNotSkipMatchingSegment() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new IntPoint("p", 1, 0)); // the only point of the segment: (1, 0)
      w.addDocument(doc);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        // the point alone matches ...
        assertEquals(1, s.count(newMultiDimIntSetQuery("p", 2, 1, 0)));
        // ... so a set that also contains (0, 1) must still match it
        Query q = newMultiDimIntSetQuery("p", 2, 0, 1, 1, 0);
        assertEquals(q.toString(), 1, s.count(q));
      }
    }
  }
}
