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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
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
 * A column batch that registers a term-vector field but never runs the per-document row pass (an
 * empty batch, or a batch rejected during field-schema validation) leaves the segment with
 * FieldInfos that declare term vectors but no term-vector files. The committed index then fails to
 * open.
 */
public class TestColumnBatchTermVectorsWithoutRows extends LuceneTestCase {

  private static IndexWriterConfig config() {
    return new IndexWriterConfig()
        .setCodec(TestUtil.getDefaultCodec())
        .setMergePolicy(NoMergePolicy.INSTANCE);
  }

  private static FieldType textWithTermVectors() {
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setStoreTermVectors(true);
    ft.freeze();
    return ft;
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

  /** Sparse text column; docs[i] gets values[i]. */
  private static BinaryColumn textColumn(String name, int[] docs, String[] values) {
    return new BinaryColumn(name, textWithTermVectors(), Column.Density.SPARSE) {
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

  private static Document idDoc(String id) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    return doc;
  }

  /** addBatch with numDocs() == 0 (an empty batch, as in TestColumnBatchIndexing#testEmptyBatch). */
  public void testEmptyBatchWithTermVectorColumn() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        w.addDocument(idDoc("0"));
        w.addBatch(batch(0, textColumn("body", new int[0], new String[0])));
        w.commit();
      }
      // The committed index must be readable and still contain the one document.
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.numDocs());
      }
    }
  }

  /**
   * A batch that addBatch rejects with IllegalArgumentException (here: a doc-values type that does
   * not match the existing field "n"). The rejection is non-aborting, so the writer stays open and
   * the earlier document must survive the commit.
   */
  public void testRejectedBatchWithTermVectorColumn() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        Document doc = idDoc("0");
        doc.add(new NumericDocValuesField("n", 1));
        w.addDocument(doc);

        FieldType sortedNumeric = new FieldType();
        sortedNumeric.setDocValuesType(DocValuesType.SORTED_NUMERIC);
        sortedNumeric.freeze();
        LongColumn n =
            new LongColumn("n", sortedNumeric, Column.Density.SPARSE) {
              @Override
              public LongTupleCursor tuples() {
                return new LongTupleCursor() {
                  int pos = -1;

                  @Override
                  public int nextDoc() {
                    pos++;
                    return pos == 0 ? 0 : DocIdSetIterator.NO_MORE_DOCS;
                  }

                  @Override
                  public long longValue() {
                    return 2;
                  }
                };
              }
            };
        IllegalArgumentException e =
            expectThrows(
                IllegalArgumentException.class,
                () ->
                    w.addBatch(
                        batch(1, textColumn("body", new int[] {0}, new String[] {"hello"}), n)));
        assertTrue(e.getMessage(), e.getMessage().contains("doc values type"));
        assertTrue(w.isOpen());
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.numDocs());
      }
    }
  }

  /**
   * A batch whose column cursor throws before the first row is framed (here: on its first
   * nextDoc()). The exception propagates (non-aborting), the writer stays open, and the earlier
   * document must survive the commit.
   */
  public void testBatchWhoseCursorFailsBeforeFirstRow() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        w.addDocument(idDoc("0"));
        BinaryColumn failing =
            new BinaryColumn("body", textWithTermVectors(), Column.Density.SPARSE) {
              @Override
              public ObjectTupleCursor<BytesRef> tuples() {
                return new ObjectTupleCursor<>() {
                  @Override
                  public int nextDoc() {
                    throw new IllegalStateException("data source failed");
                  }

                  @Override
                  public BytesRef value() {
                    throw new AssertionError();
                  }
                };
              }
            };
        expectThrows(IllegalStateException.class, () -> w.addBatch(batch(3, failing)));
        assertTrue(w.isOpen());
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.numDocs());
      }
    }
  }
}
