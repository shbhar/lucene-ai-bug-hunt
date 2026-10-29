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

import java.io.IOException;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortFieldProvider;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

public class TestDerivedSortKeyRangeQuery extends LuceneTestCase {

  public static final class KeyFromA extends BinarySortField {
    public KeyFromA() {
      super("key", false, null, "KeyFromA");
    }

    @Override
    protected BinaryDocValues getSortKeyDocValues(LeafReader reader) throws IOException {
      return DocValues.getBinary(reader, "a");
    }
  }

  public static final class KeyFromAProvider extends SortFieldProvider {
    public KeyFromAProvider() {
      super("KeyFromA");
    }

    @Override
    public SortField readSortField(DataInput in) {
      return new KeyFromA();
    }

    @Override
    public void writeSortField(SortField sf, DataOutput out) {}
  }

  public void testRangeOnSecondSortField() throws IOException {
    Sort sort = new Sort(new KeyFromA(), new SortField("f", SortField.Type.LONG));
    IndexWriterConfig iwc =
        new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()).setIndexSort(sort);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int[] af : new int[][] {{1, 1}, {0, 2}}) {
        Document doc = new Document();
        doc.add(new BinaryDocValuesField("a", new BytesRef(new byte[] {(byte) af[0]})));
        doc.add(NumericDocValuesField.indexedField("f", af[1]));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        Query query = NumericDocValuesField.newSlowRangeQuery("f", 1, 1);
        // expected 1 hit (f = 1); actual 0: the segment is in (a, f) order (f = 2, 1), not f order
        assertEquals(1, new IndexSearcher(reader).count(query));
      }
    }
  }
}
