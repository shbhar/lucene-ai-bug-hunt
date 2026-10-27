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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatch;
import org.apache.lucene.document.column.LongColumn;
import org.apache.lucene.document.column.LongTupleCursor;
import org.apache.lucene.document.column.LongValuesCursor;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;

/**
 * When the values cursor of a dense LongColumn throws while addBatch feeds NUMERIC or
 * SORTED_NUMERIC doc values, the values already copied into the in-memory doc-values buffer are not
 * rolled back. The batch is rejected (non-aborting), but later documents of the same in-memory
 * segment then read back another document's value.
 */
public class TestColumnBatchDenseDocValuesFailure extends LuceneTestCase {

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

  /** Dense column with values -1, -2, ...; the values cursor throws when asked for value throwAt. */
  private static LongColumn failingDenseColumn(
      String name, IndexableFieldType type, int numDocs, int throwAt) {
    return new LongColumn(name, type, Column.Density.DENSE) {
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
            return -1 - pos;
          }
        };
      }

      @Override
      public LongValuesCursor values() {
        return new LongValuesCursor(numDocs) {
          int pos = 0;

          @Override
          public long nextLong() {
            if (pos == throwAt) {
              throw new IllegalStateException("data source failed at value " + pos);
            }
            return -1 - pos++;
          }
        };
      }
    };
  }

  private static long liveValueOfGoodDoc(DirectoryReader reader, String field, boolean sorted)
      throws Exception {
    assertEquals(1, reader.leaves().size());
    LeafReader leaf = reader.leaves().get(0).reader();
    Bits live = leaf.getLiveDocs();
    int goodDoc = leaf.maxDoc() - 1; // the document added last
    assertTrue(live == null || live.get(goodDoc));
    if (sorted) {
      SortedNumericDocValues dv = leaf.getSortedNumericDocValues(field);
      assertTrue(dv.advanceExact(goodDoc));
      assertEquals(1, dv.docValueCount());
      return dv.nextValue();
    } else {
      NumericDocValues dv = leaf.getNumericDocValues(field);
      assertTrue(dv.advanceExact(goodDoc));
      return dv.longValue();
    }
  }

  /**
   * 255 earlier documents fill all but one slot of the doc-values buffer's current page; a 2-doc
   * dense batch whose cursor fails on its second value then leaves one orphan value behind.
   */
  public void testNumericSmallBatchAfterEarlierDocs() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      for (int i = 0; i < 255; i++) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("v", i));
        w.addDocument(doc);
      }
      expectThrows(
          IllegalStateException.class,
          () -> w.addBatch(batch(2, failingDenseColumn("v", NumericDocValuesField.TYPE, 2, 1))));
      assertTrue(w.isOpen());

      Document good = new Document();
      good.add(new NumericDocValuesField("v", 42));
      good.add(new StringField("id", "good", Field.Store.NO));
      w.addDocument(good);

      try (DirectoryReader reader = DirectoryReader.open(w)) {
        assertEquals(256, reader.numDocs()); // 255 + good; the 2 batch docs are deleted
        assertEquals(42, liveValueOfGoodDoc(reader, "v", false));
      }
    }
  }

  /** A 257-doc dense SORTED_NUMERIC batch whose cursor fails on its last value. */
  public void testSortedNumericBatchFailingAfterFirstPage() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      expectThrows(
          IllegalStateException.class,
          () ->
              w.addBatch(
                  batch(
                      257, failingDenseColumn("sv", SortedNumericDocValuesField.TYPE, 257, 256))));
      assertTrue(w.isOpen());

      Document good = new Document();
      good.add(new SortedNumericDocValuesField("sv", 42));
      w.addDocument(good);

      try (DirectoryReader reader = DirectoryReader.open(w)) {
        assertEquals(1, reader.numDocs());
        assertEquals(42, liveValueOfGoodDoc(reader, "sv", true));
      }
    }
  }
}
