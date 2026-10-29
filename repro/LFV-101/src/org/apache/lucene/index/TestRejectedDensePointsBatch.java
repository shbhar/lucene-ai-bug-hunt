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
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.column.BinaryColumn;
import org.apache.lucene.document.column.BytesRefValuesCursor;
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatch;
import org.apache.lucene.document.column.ObjectTupleCursor;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

public class TestRejectedDensePointsBatch extends LuceneTestCase {

  public void testRejectedBatchLeavesNoPointValues() throws Exception {
    int numDocs = 1025;
    FieldType type = new FieldType();
    type.setDimensions(1, Integer.BYTES);
    type.freeze();
    BinaryColumn column =
        new BinaryColumn("p", type, Column.Density.DENSE) {
          BytesRef bytes(int i) {
            return i < numDocs - 1 ? IntPoint.pack(1000) : new BytesRef(new byte[3]);
          }

          @Override
          public ObjectTupleCursor<BytesRef> tuples() {
            return new ObjectTupleCursor<>() {
              int doc = -1;

              @Override
              public int nextDoc() {
                return ++doc < numDocs ? doc : DocIdSetIterator.NO_MORE_DOCS;
              }

              @Override
              public BytesRef value() {
                return bytes(doc);
              }
            };
          }

          @Override
          public BytesRefValuesCursor values() {
            return new BytesRefValuesCursor(numDocs) {
              int i = 0;

              @Override
              public BytesRef nextValue() {
                return bytes(i++);
              }
            };
          }
        };
    ColumnBatch batch =
        new ColumnBatch() {
          @Override
          public int numDocs() {
            return numDocs;
          }

          @Override
          public Iterable<Column> columns() {
            return List.of(column);
          }
        };
    IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      expectThrows(IllegalArgumentException.class, () -> w.addBatch(batch));
      Document doc = new Document();
      doc.add(new IntPoint("p", 7));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        assertEquals(
            "live docs matching p=7, p=1000",
            List.of(1, 0),
            List.of(
                s.count(IntPoint.newExactQuery("p", 7)), s.count(IntPoint.newExactQuery("p", 1000))));
      }
    }
  }
}
