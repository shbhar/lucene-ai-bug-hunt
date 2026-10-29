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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatch;
import org.apache.lucene.document.column.LongColumn;
import org.apache.lucene.document.column.LongTupleCursor;
import org.apache.lucene.document.column.LongValuesCursor;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestDenseLongColumnFailure extends LuceneTestCase {

  public void testFailedDenseBatchShiftsLaterValues() throws Exception {
    Column failing =
        new LongColumn("v", NumericDocValuesField.TYPE, Column.Density.DENSE) {
          @Override
          public LongTupleCursor tuples() {
            throw new UnsupportedOperationException();
          }

          @Override
          public LongValuesCursor values() {
            return new LongValuesCursor(2) {
              int pos = 0;

              @Override
              public long nextLong() {
                if (pos++ == 1) {
                  throw new IllegalStateException("data source failed");
                }
                return -1;
              }
            };
          }
        };
    ColumnBatch batch =
        new ColumnBatch() {
          @Override
          public int numDocs() {
            return 2;
          }

          @Override
          public Iterable<Column> columns() {
            return List.of(failing);
          }
        };
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      for (int i = 0; i < 255; i++) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("v", i));
        w.addDocument(doc);
      }
      expectThrows(IllegalStateException.class, () -> w.addBatch(batch));
      Document good = new Document();
      good.add(new NumericDocValuesField("v", 42));
      w.addDocument(good);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        LeafReader leaf = getOnlyLeafReader(reader);
        NumericDocValues values = leaf.getNumericDocValues("v");
        assertTrue(values.advanceExact(leaf.maxDoc() - 1));
        // expected: the last document reads its own value 42; actual: -1 from the failed batch
        assertEquals(42, values.longValue());
      }
    }
  }
}
