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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatch;
import org.apache.lucene.document.column.LongColumn;
import org.apache.lucene.document.column.LongTupleCursor;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A field indexed with two field types (a common pattern: StringField plus SortedDocValuesField
 * under one name) gets the FieldInfo "postings + sorted doc values". A later document that has
 * only the StringField part must be rejected (the per-document schema check, FieldSchema javadoc
 * in IndexingChain), and it is, unless the StringField happened to be the first value of the
 * field in an earlier document of the same in-memory segment: then the frozen-FieldType cache
 * skips the check and the document is accepted.
 *
 * <p>Only public API is used.
 */
public class TestFrozenTypeCacheMultiTypeField extends LuceneTestCase {

  private static void addBoth(IndexWriter w, boolean stringFirst) throws Exception {
    Field s = new StringField("id", "a", Field.Store.NO);
    Field dv = new SortedDocValuesField("id", new BytesRef("a"));
    w.addDocument(stringFirst ? List.of(s, dv) : List.of(dv, s));
  }

  /** Control: value order dv-first; the postings-only document is rejected. */
  public void testRejectedWhenDocValuesValueComesFirst() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      addBoth(w, false);
      expectThrows(
          IllegalArgumentException.class,
          () -> w.addDocument(List.of(new StringField("id", "b", Field.Store.NO))));
    }
  }

  /** Control: an unfrozen copy of the StringField type; the postings-only document is rejected. */
  public void testRejectedWithUnfrozenType() throws Exception {
    FieldType unfrozen = new FieldType(StringField.TYPE_NOT_STORED);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(
          List.of(new Field("id", "a", unfrozen), new SortedDocValuesField("id", new BytesRef("a"))));
      expectThrows(
          IllegalArgumentException.class,
          () -> w.addDocument(List.of(new Field("id", "b", unfrozen))));
    }
  }

  /** Bug: value order string-first; the same postings-only document is accepted. */
  public void testRejectedWhenStringValueComesFirst() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      addBoth(w, true);
      expectThrows(
          IllegalArgumentException.class,
          () -> w.addDocument(List.of(new StringField("id", "b", Field.Store.NO))));
    }
  }

  /** Bug, other direction: a stored-only document for a field that also has doc values. */
  public void testStoredOnlyDocumentRejected() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(
          List.of(new StoredField("id", "a"), new SortedDocValuesField("id", new BytesRef("a"))));
      expectThrows(
          IllegalArgumentException.class,
          () -> w.addDocument(List.of(new StoredField("id", "b"))));
    }
  }

  /** The outcome must not depend on whether the in-memory segment was flushed in between. */
  public void testSameOutcomeBeforeAndAfterFlush() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      addBoth(w, true);
      w.flush();
      addBoth(w, true);
      boolean acceptedBeforeFlush;
      try {
        w.addDocument(List.of(new StringField("id", "b", Field.Store.NO)));
        acceptedBeforeFlush = true;
      } catch (IllegalArgumentException e) {
        acceptedBeforeFlush = false;
      }
      w.flush();
      boolean acceptedAfterFlush;
      try {
        w.addDocument(List.of(new StringField("id", "c", Field.Store.NO)));
        acceptedAfterFlush = true;
      } catch (IllegalArgumentException e) {
        acceptedAfterFlush = false;
      }
      assertEquals(
          "accepted before flush=" + acceptedBeforeFlush + ", after flush=" + acceptedAfterFlush,
          acceptedAfterFlush,
          acceptedBeforeFlush);
    }
  }

  /**
   * Second symptom of the same cache: after a rejected addBatch, the cached type's fast path keeps
   * the batch's schema state, so a later valid multi-type document is checked against it and
   * rejected.
   */
  public void testValidMultiTypeDocumentAcceptedAfterRejectedBatch() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(List.of(new StringField("id", "a", Field.Store.NO)));

      // Correctly rejected: "id" is a postings-only field.
      LongColumn numeric =
          new LongColumn("id", NumericDocValuesField.TYPE, Column.Density.SPARSE) {
            @Override
            public LongTupleCursor tuples() {
              return new LongTupleCursor() {
                int doc = -1;

                @Override
                public int nextDoc() {
                  return ++doc == 0 ? 0 : DocIdSetIterator.NO_MORE_DOCS;
                }

                @Override
                public long longValue() {
                  return 1;
                }
              };
            }
          };
      expectThrows(
          IllegalArgumentException.class,
          () ->
              w.addBatch(
                  new ColumnBatch() {
                    @Override
                    public int numDocs() {
                      return 1;
                    }

                    @Override
                    public Iterable<Column> columns() {
                      return List.of(numeric);
                    }
                  }));

      // Valid: same postings schema as the first document, plus a stored value.
      w.addDocument(
          List.of(new StringField("id", "b", Field.Store.NO), new StoredField("id", "b")));
    }
  }
}

