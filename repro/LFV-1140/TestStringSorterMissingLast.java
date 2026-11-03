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

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestStringSorterMissingLast extends LuceneTestCase {

  public void testMissingKeyAboveGlobalOrdsPastIntRange() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
        w.addDocument(new Document());
        w.commit();
        w.addDocument(new Document());
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReader a = r.leaves().get(0).reader();
        LeafReader b = r.leaves().get(1).reader();
        int n = (1 << 30) + 1;
        IndexSorter sorter =
            new IndexSorter.StringSorter(
                SortField.Provider.NAME,
                SortField.STRING_LAST,
                false,
                leaf -> leaf == a ? new OneDoc(0, n, -1) : new OneDoc(n, n, n - 1));
        IndexSorter.ComparableProvider[] keys = sorter.getComparableProviders(List.of(a, b));
        long missing = keys[0].getAsComparableLong(0);
        long present = keys[1].getAsComparableLong(0);
        // expected: the missing key sorts after global ord 2^31 + 1; actual: it is
        // Integer.MAX_VALUE
        assertTrue("missing=" + missing + " present=" + present, missing > present);
      }
    }
  }

  private static final class OneDoc extends SortedDocValues {
    private final long first;
    private final int valueCount;
    private final int ord;
    private final BytesRef term = new BytesRef(new byte[8]);
    private int doc = -1;

    OneDoc(long first, int valueCount, int ord) {
      this.first = first;
      this.valueCount = valueCount;
      this.ord = ord;
    }

    @Override
    public BytesRef lookupOrd(int o) {
      long v = first + o;
      for (int i = 7; i >= 0; i--, v >>>= 8) {
        term.bytes[i] = (byte) v;
      }
      return term;
    }

    @Override
    public int getValueCount() {
      return valueCount;
    }

    @Override
    public int ordValue() {
      return ord;
    }

    @Override
    public boolean advanceExact(int target) {
      doc = target;
      return ord >= 0;
    }

    @Override
    public int docID() {
      return doc;
    }

    @Override
    public int nextDoc() {
      return advance(doc + 1);
    }

    @Override
    public int advance(int target) {
      return doc = (target == 0 && ord >= 0) ? 0 : NO_MORE_DOCS;
    }

    @Override
    public long cost() {
      return 1;
    }
  }
}
