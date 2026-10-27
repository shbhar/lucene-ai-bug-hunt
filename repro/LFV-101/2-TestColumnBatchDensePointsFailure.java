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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.column.BinaryColumn;
import org.apache.lucene.document.column.BytesRefValuesCursor;
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatch;
import org.apache.lucene.document.column.LongColumn;
import org.apache.lucene.document.column.LongTupleCursor;
import org.apache.lucene.document.column.LongValuesCursor;
import org.apache.lucene.document.column.ObjectTupleCursor;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;

/**
 * When the dense points path of addBatch fails part-way, the point values it already copied into
 * the in-memory points buffer are not rolled back. The batch is rejected (non-aborting), but every
 * later point of that field in the same in-memory segment is read back with the wrong value.
 */
public class TestColumnBatchDensePointsFailure extends LuceneTestCase {

  private static IndexWriterConfig config() {
    return new IndexWriterConfig()
        .setCodec(TestUtil.getDefaultCodec())
        .setMergePolicy(NoMergePolicy.INSTANCE);
  }

  private static ColumnBatch batch(int numDocs, Column... columns) {
    return new ColumnBatch() {
      @Override
      public int numDocs() {
        return numDocs;
      }

      @Override
      public Iterable<Column> columns() {
        return List.of(columns);
      }
    };
  }

  private static Document goodDoc(String field, int value) {
    Document doc = new Document();
    doc.add(new IntPoint(field, value));
    doc.add(new StringField("id", "good", Field.Store.NO));
    return doc;
  }

  /**
   * Dense BinaryColumn feeding 1-D int points. The last of 1025 values has the wrong length, so
   * Lucene's own check (BytesRefValuesCursor#fillPackedPoints) rejects the batch with
   * IllegalArgumentException after the first 1024 values were copied.
   */
  public void testWrongLengthValueInDenseBinaryPointsColumn() throws Exception {
    FieldType pointType = new FieldType();
    pointType.setDimensions(1, Integer.BYTES);
    pointType.freeze();
    final int numDocs = 1025;
    final BytesRef[] values = new BytesRef[numDocs];
    for (int i = 0; i < numDocs; i++) {
      byte[] packed = new byte[Integer.BYTES];
      NumericUtils.intToSortableBytes(1000 + i, packed, 0);
      values[i] = new BytesRef(packed);
    }
    values[numDocs - 1] = new BytesRef(new byte[3]); // malformed: 3 bytes instead of 4
    BinaryColumn column =
        new BinaryColumn("p", pointType, Column.Density.DENSE) {
          @Override
          public ObjectTupleCursor<BytesRef> tuples() {
            return new ObjectTupleCursor<>() {
              int pos = -1;

              @Override
              public int nextDoc() {
                pos++;
                return pos < numDocs ? pos : DocIdSetIterator.NO_MORE_DOCS;
              }

              @Override
              public BytesRef value() {
                return values[pos];
              }
            };
          }

          @Override
          public BytesRefValuesCursor values() {
            return new BytesRefValuesCursor(numDocs) {
              int pos = 0;

              @Override
              public BytesRef nextValue() {
                return values[pos++];
              }
            };
          }
        };

    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      expectThrows(IllegalArgumentException.class, () -> w.addBatch(batch(numDocs, column)));
      assertTrue(w.isOpen());

      w.addDocument(goodDoc("p", 7));

      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        assertEquals(1, reader.numDocs());
        // the only live document indexed p=7; nothing indexed p=1000
        assertEquals(0, searcher.count(IntPoint.newExactQuery("p", 1000)));
        assertEquals(1, searcher.count(IntPoint.newExactQuery("p", 7)));
      }
    }
  }

  /**
   * Dense LongColumn feeding 1-D long points whose values cursor throws (for example because the
   * caller's data source fails) after the first 512-value chunk.
   */
  public void testThrowingDenseLongPointsColumn() throws Exception {
    FieldType pointType = new FieldType();
    pointType.setDimensions(1, Long.BYTES);
    pointType.freeze();
    final int numDocs = 513;
    LongColumn column =
        new LongColumn("lp", pointType, Column.Density.DENSE) {
          @Override
          public LongTupleCursor tuples() {
            return new LongTupleCursor() {
              int pos = -1;

              @Override
              public int nextDoc() {
                pos++;
                return pos < numDocs ? pos : DocIdSetIterator.NO_MORE_DOCS;
              }

              @Override
              public long longValue() {
                return 1000 + pos;
              }
            };
          }

          @Override
          public LongValuesCursor values() {
            return new LongValuesCursor(numDocs) {
              int pos = 0;

              @Override
              public long nextLong() {
                if (pos == numDocs - 1) {
                  throw new IllegalStateException("data source failed");
                }
                return 1000 + pos++;
              }
            };
          }
        };

    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      expectThrows(IllegalStateException.class, () -> w.addBatch(batch(numDocs, column)));
      assertTrue(w.isOpen());

      Document doc = new Document();
      doc.add(new LongPoint("lp", 7));
      w.addDocument(doc);

      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        assertEquals(1, reader.numDocs());
        assertEquals(0, searcher.count(LongPoint.newExactQuery("lp", 1000)));
        assertEquals(1, searcher.count(LongPoint.newExactQuery("lp", 7)));
      }
    }
  }
}
