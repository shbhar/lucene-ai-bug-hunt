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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatch;
import org.apache.lucene.document.column.LongColumn;
import org.apache.lucene.document.column.LongTupleCursor;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestColumnBatchThirtyNineRowColumns extends LuceneTestCase {

  public void testThirtyNineStoredColumns() throws IOException {
    List<Column> columns = new ArrayList<>();
    for (int i = 0; i < 39; i++) {
      columns.add(
          new LongColumn("f" + i, StoredField.TYPE, Column.Density.SPARSE) {
            @Override
            public LongTupleCursor tuples() {
              return new LongTupleCursor() {
                int doc = -1;

                @Override
                public int nextDoc() {
                  return doc = (doc == -1) ? 0 : DocIdSetIterator.NO_MORE_DOCS;
                }

                @Override
                public long longValue() {
                  return 0;
                }
              };
            }
          });
    }
    ColumnBatch batch =
        new ColumnBatch() {
          @Override
          public int numDocs() {
            return 1;
          }

          @Override
          public Iterable<Column> columns() {
            return columns;
          }
        };
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      // -XX:-UseCompressedOops: expected 39 stored fields; actual ArrayIndexOutOfBoundsException
      w.addBatch(batch);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        assertEquals(39, reader.storedFields().document(0).getFields().size());
      }
    }
  }
}
