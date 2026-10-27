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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.IntPoint;
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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;

/**
 * IndexWriter#addBatch copies a dense points column into the field's in-memory points buffer in
 * chunks of 4 KiB (1024 int points). When the batch fails, it is rejected and its documents are
 * deleted, but the writer stays open: documents added afterwards to the same in-memory segment must
 * keep their own point values.
 */
public class TestColumnBatchDensePointsFailure extends LuceneTestCase {

  /** The batch's values are FIRST_VALUE, FIRST_VALUE + 1, ... */
  private static final int FIRST_VALUE = 1000;

  /** The application's cursor fails at value 1025, after the first chunk of 1024 values. */
  public void testCursorThrowsAfterFirstChunk() throws IOException {
    assertLaterDocumentsKeepTheirValues(1025, throwingIntColumn(1025), IllegalStateException.class);
  }

  /** Lucene's own width check rejects value 1025 (3 bytes instead of 4). */
  public void testWrongLengthValueAfterFirstChunk() throws IOException {
    assertLaterDocumentsKeepTheirValues(
        1025, wrongLengthBinaryColumn(1025), IllegalArgumentException.class);
  }

  /** Control: the same failure at value 1024, inside the first chunk, leaves no trace. */
  public void testFailureInsideFirstChunkLeavesNoTrace() throws IOException {
    assertLaterDocumentsKeepTheirValues(1024, throwingIntColumn(1024), IllegalStateException.class);
  }

  private static void assertLaterDocumentsKeepTheirValues(
      int numDocs, Column column, Class<? extends RuntimeException> expected) throws IOException {
    // default config and codec: no flush between the failed batch and the next documents
    IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      expectThrows(expected, () -> w.addBatch(batch(numDocs, column)));
      assertTrue(w.isOpen());

      for (int value : new int[] {7, 8, 9}) {
        Document doc = new Document();
        doc.add(new IntPoint("p", value));
        w.addDocument(doc);
      }

      try (DirectoryReader reader = DirectoryReader.open(w)) {
        assertEquals(3, reader.numDocs());
        assertEquals(
            "point values of the 3 documents added after the rejected batch",
            List.of(7, 8, 9),
            livePointValues(reader));
        IndexSearcher searcher = new IndexSearcher(reader);
        assertEquals(1, searcher.count(IntPoint.newExactQuery("p", 7)));
        assertEquals(
            0, searcher.count(IntPoint.newRangeQuery("p", FIRST_VALUE, FIRST_VALUE + numDocs)));
      }
    }
  }

  /** The value of field "p" of every live document, in doc ID order. */
  private static List<Integer> livePointValues(DirectoryReader reader) throws IOException {
    List<Integer> values = new ArrayList<>();
    for (LeafReaderContext ctx : reader.leaves()) {
      PointValues points = ctx.reader().getPointValues("p");
      if (points == null) {
        continue;
      }
      Integer[] byDoc = new Integer[ctx.reader().maxDoc()];
      points.intersect(
          new PointValues.IntersectVisitor() {
            @Override
            public void visit(int docID) {
              throw new AssertionError("only called for CELL_INSIDE_QUERY");
            }

            @Override
            public void visit(int docID, byte[] packedValue) {
              byDoc[docID] = IntPoint.decodeDimension(packedValue, 0);
            }

            @Override
            public PointValues.Relation compare(byte[] minPackedValue, byte[] maxPackedValue) {
              return PointValues.Relation.CELL_CROSSES_QUERY;
            }
          });
      Bits liveDocs = ctx.reader().getLiveDocs();
      for (int doc = 0; doc < byDoc.length; doc++) {
        if (byDoc[doc] != null && (liveDocs == null || liveDocs.get(doc))) {
          values.add(byDoc[doc]);
        }
      }
    }
    return values;
  }

  private static ColumnBatch batch(int numDocs, Column column) {
    return new ColumnBatch() {
      @Override
      public int numDocs() {
        return numDocs;
      }

      @Override
      public Iterable<Column> columns() {
        return List.of(column);
      }
    };
  }

  private static FieldType intPointType() {
    FieldType type = new FieldType();
    type.setDimensions(1, Integer.BYTES);
    type.freeze();
    return type;
  }

  /** A dense int points column whose values cursor throws at its last value. */
  private static LongColumn throwingIntColumn(int numDocs) {
    return new LongColumn("p", intPointType(), Column.Density.DENSE, LongColumn.NumericKind.INT) {
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
            return FIRST_VALUE + pos;
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
              throw new IllegalStateException("data source failed at value " + numDocs);
            }
            return FIRST_VALUE + pos++;
          }
        };
      }
    };
  }

  /** A dense binary column with 1-D int points whose last value has 3 bytes instead of 4. */
  private static BinaryColumn wrongLengthBinaryColumn(int numDocs) {
    BytesRef[] values = new BytesRef[numDocs];
    for (int i = 0; i < numDocs; i++) {
      byte[] packed = new byte[Integer.BYTES];
      NumericUtils.intToSortableBytes(FIRST_VALUE + i, packed, 0);
      values[i] = new BytesRef(packed);
    }
    values[numDocs - 1] = new BytesRef(new byte[3]);
    return new BinaryColumn("p", intPointType(), Column.Density.DENSE) {
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
  }
}
