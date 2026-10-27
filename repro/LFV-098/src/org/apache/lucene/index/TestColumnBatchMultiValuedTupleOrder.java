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
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.column.BinaryColumn;
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatch;
import org.apache.lucene.document.column.LongColumn;
import org.apache.lucene.document.column.LongTupleCursor;
import org.apache.lucene.document.column.ObjectTupleCursor;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * Tuple cursors must return batch doc-ids in non-decreasing order. addBatch rejects a violation
 * with IllegalArgumentException for the row pass, for vectors and for NUMERIC / BINARY / SORTED doc
 * values, but for SORTED_NUMERIC and SORTED_SET doc values the order is only checked by a Java
 * assert in the doc-values writer: with assertions enabled the AssertionError closes the
 * IndexWriter, without assertions the batch is accepted and corrupts the writer's buffered state.
 */
public class TestColumnBatchMultiValuedTupleOrder extends LuceneTestCase {

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

  /** Sparse SORTED_NUMERIC column whose cursor returns doc 1 before doc 0. */
  private static LongColumn outOfOrderSortedNumeric() {
    final int[] docs = {1, 0};
    final long[] values = {11, 10};
    return new LongColumn("sn", SortedNumericDocValuesField.TYPE, Column.Density.SPARSE) {
      @Override
      public LongTupleCursor tuples() {
        return new LongTupleCursor() {
          int pos = -1;

          @Override
          public int nextDoc() {
            pos++;
            return pos < docs.length ? docs[pos] : DocIdSetIterator.NO_MORE_DOCS;
          }

          @Override
          public long longValue() {
            return values[pos];
          }
        };
      }
    };
  }

  /** Sparse SORTED_SET column whose cursor returns doc 1 before doc 0. */
  private static BinaryColumn outOfOrderSortedSet() {
    final int[] docs = {1, 0};
    final String[] values = {"b", "a"};
    return new BinaryColumn("ss", SortedSetDocValuesField.TYPE, Column.Density.SPARSE) {
      @Override
      public ObjectTupleCursor<BytesRef> tuples() {
        return new ObjectTupleCursor<>() {
          int pos = -1;

          @Override
          public int nextDoc() {
            pos++;
            return pos < docs.length ? docs[pos] : DocIdSetIterator.NO_MORE_DOCS;
          }

          @Override
          public BytesRef value() {
            return new BytesRef(values[pos]);
          }
        };
      }
    };
  }

  public void testOutOfOrderSortedNumericTuplesAreRejected() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      expectThrows(
          IllegalArgumentException.class, () -> w.addBatch(batch(2, outOfOrderSortedNumeric())));
      assertTrue("a rejected batch must not close the writer", w.isOpen());
    }
  }

  public void testOutOfOrderSortedSetTuplesAreRejected() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      expectThrows(
          IllegalArgumentException.class, () -> w.addBatch(batch(2, outOfOrderSortedSet())));
      assertTrue("a rejected batch must not close the writer", w.isOpen());
    }
  }

  /**
   * Whatever addBatch does with the malformed batch, documents indexed afterwards must be indexed
   * correctly (and the writer must stay usable).
   */
  public void testLaterDocumentsKeepTheirValues() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      try {
        w.addBatch(batch(2, outOfOrderSortedNumeric()));
      } catch (Throwable t) {
        // rejecting the batch is fine (whatever the exception); the checks below are what matter
      }
      assertTrue("a malformed batch must not close the writer", w.isOpen());
      long[] later = {20, 30};
      int accepted = 0;
      for (long v : later) {
        Document doc = new Document();
        doc.add(new SortedNumericDocValuesField("sn", v));
        w.addDocument(doc); // valid document: must not throw
        accepted++;
      }
      assertEquals(2, accepted);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        LeafReader leaf = reader.leaves().get(0).reader();
        SortedNumericDocValues dv = leaf.getSortedNumericDocValues("sn");
        int maxDoc = leaf.maxDoc();
        // the last two documents are the valid ones, in indexing order
        assertTrue(dv.advanceExact(maxDoc - 2));
        assertEquals(1, dv.docValueCount());
        assertEquals(20, dv.nextValue());
        assertTrue(dv.advanceExact(maxDoc - 1));
        assertEquals(1, dv.docValueCount());
        assertEquals(30, dv.nextValue());
      }
    }
  }
}
