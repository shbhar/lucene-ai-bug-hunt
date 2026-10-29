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
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.column.BinaryColumn;
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatch;
import org.apache.lucene.document.column.ObjectTupleCursor;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

public class TestEmptyColumnBatchTermVectors extends LuceneTestCase {

  public void testEmptyBatchWithTermVectorField() throws Exception {
    FieldType withTermVectors = new FieldType(TextField.TYPE_NOT_STORED);
    withTermVectors.setStoreTermVectors(true);
    Column body =
        new BinaryColumn("body", withTermVectors, Column.Density.SPARSE) {
          @Override
          public ObjectTupleCursor<BytesRef> tuples() {
            return new ObjectTupleCursor<>() {
              @Override
              public int nextDoc() {
                return DocIdSetIterator.NO_MORE_DOCS;
              }

              @Override
              public BytesRef value() {
                throw new IllegalStateException();
              }
            };
          }
        };
    ColumnBatch emptyBatch =
        new ColumnBatch() {
          @Override
          public int numDocs() {
            return 0;
          }

          @Override
          public Iterable<Column> columns() {
            return List.of(body);
          }
        };
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        w.addDocument(new Document());
        w.addBatch(emptyBatch);
      }
      // expected: the commit opens with 1 doc; actual: CorruptIndexException, no term-vector files
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.numDocs());
      }
    }
  }
}
