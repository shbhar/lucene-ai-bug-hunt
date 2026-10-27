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
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatch;
import org.apache.lucene.document.column.DictionaryColumn;
import org.apache.lucene.document.column.OrdinalsTupleCursor;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * ColumnBatch#numDocs(): "All column doc-ids must be in the range [0, numDocs())". Every column
 * kind checks this (ColumnValidation#checkDocID) except DictionaryColumn doc values: an
 * out-of-range batch doc-id is not rejected, and the value is written to another document of the
 * segment (the next document for doc-id == numDocs, an earlier one for doc-id -1).
 */
public class TestColumnBatchDictionaryDocIdRange extends LuceneTestCase {

  private static IndexWriterConfig config() {
    return new IndexWriterConfig()
        .setCodec(TestUtil.getDefaultCodec())
        .setMergePolicy(NoMergePolicy.INSTANCE);
  }

  private static final List<BytesRef> DICT = List.of(new BytesRef("x"), new BytesRef("y"));

  private static DictionaryColumn dictionaryColumn(
      String name, DocValuesType dvType, int[] docs, int[] ords) {
    FieldType ft = new FieldType();
    ft.setDocValuesType(dvType);
    ft.freeze();
    return new DictionaryColumn(name, ft, Column.Density.SPARSE, DICT) {
      @Override
      public OrdinalsTupleCursor tuples() {
        return new OrdinalsTupleCursor() {
          int pos = -1;

          @Override
          public int nextDoc() {
            pos++;
            return pos < docs.length ? docs[pos] : DocIdSetIterator.NO_MORE_DOCS;
          }

          @Override
          public int ordValue() {
            return ords[pos];
          }
        };
      }
    };
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

  private static Document idDoc(String id) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    return doc;
  }

  /** batch doc-id 2 in a 2-doc batch must be rejected, as it is for every other column kind. */
  public void testSortedDocIdPastEndIsRejected() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      DictionaryColumn column =
          dictionaryColumn("tag", DocValuesType.SORTED, new int[] {0, 2}, new int[] {0, 1});
      expectThrows(IllegalArgumentException.class, () -> w.addBatch(batch(2, column)));
    }
  }

  /** Consequence: the out-of-range value lands on the next document, which never had the field. */
  public void testSortedDocIdPastEndDoesNotLeakIntoNextDocument() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      DictionaryColumn column =
          dictionaryColumn("tag", DocValuesType.SORTED, new int[] {0, 2}, new int[] {0, 1});
      try {
        w.addBatch(batch(2, column));
      } catch (IllegalArgumentException expected) {
        // rejecting the batch is the correct outcome
      }
      w.addDocument(idDoc("next")); // no "tag" field
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        LeafReader leaf = reader.leaves().get(0).reader();
        int next = leaf.maxDoc() - 1;
        SortedDocValues dv = leaf.getSortedDocValues("tag");
        assertFalse(
            "document 'next' has no tag field but reads one", dv != null && dv.advanceExact(next));
      }
    }
  }

  /** batch doc-id -1 writes onto the document indexed before the batch. */
  public void testSortedSetNegativeDocIdDoesNotTouchEarlierDocument() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(idDoc("before")); // no "tag" field
      DictionaryColumn column =
          dictionaryColumn("tag", DocValuesType.SORTED_SET, new int[] {-1}, new int[] {1});
      try {
        w.addBatch(batch(1, column));
      } catch (IllegalArgumentException expected) {
        // rejecting the batch is the correct outcome
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        LeafReader leaf = reader.leaves().get(0).reader();
        SortedSetDocValues dv = leaf.getSortedSetDocValues("tag");
        assertFalse(
            "document 'before' has no tag field but reads one", dv != null && dv.advanceExact(0));
      }
    }
  }
}
