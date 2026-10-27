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
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A document that is rejected because it adds a doc-values skip index to a field leaves that skip
 * index type behind in the field's per-segment schema state, so later, valid documents with the
 * same field are rejected until the in-memory segment is flushed.
 *
 * <p>Only public API is used: IndexWriter.addDocument, flush, and stock field classes.
 */
public class TestRejectedDocSkipIndexPoisonsField extends LuceneTestCase {

  /** The field already exists in the in-memory segment as a postings-only field. */
  public void testValidDocRejectedAfterRejectedSkipIndexDoc() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(List.of(new StringField("f", "a", Field.Store.NO)));

      // Correctly rejected: adds sorted doc values (with a skip index) to a postings-only field.
      expectThrows(
          IllegalArgumentException.class,
          () ->
              w.addDocument(
                  List.of(
                      new StringField("f", "b", Field.Store.NO),
                      SortedDocValuesField.indexedField("f", new BytesRef("b")))));

      // Same schema as the first document: must be accepted.
      w.addDocument(List.of(new StringField("f", "c", Field.Store.NO)));
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(2, r.numDocs());
      }
    }
  }

  /** The field is not yet in the in-memory segment; the rejected document fails on another field. */
  public void testFirstValidDocRejectedAfterRejectedSkipIndexDoc() throws Exception {
    FieldType termVectorsWithoutIndex = new FieldType();
    termVectorsWithoutIndex.setStored(true);
    termVectorsWithoutIndex.setStoreTermVectors(true); // invalid: not indexed
    termVectorsWithoutIndex.freeze();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      // Correctly rejected because of field "g"; field "f" on its own is fine.
      expectThrows(
          IllegalArgumentException.class,
          () ->
              w.addDocument(
                  List.of(
                      SortedDocValuesField.indexedField("f", new BytesRef("a")),
                      new Field("g", "x", termVectorsWithoutIndex))));

      // The first valid document for "f" in this index: must be accepted.
      w.addDocument(List.of(new StringField("f", "b", Field.Store.NO)));
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.numDocs());
      }
    }
  }

  /** The field exists only in an earlier, committed segment (cross-segment schema check). */
  public void testValidDocRejectedAfterCrossSegmentRejection() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(List.of(new StringField("f", "a", Field.Store.NO)));
      w.commit();

      // Correctly rejected by the index-wide schema of "f" (postings only).
      expectThrows(
          IllegalArgumentException.class,
          () ->
              w.addDocument(
                  List.of(
                      new StringField("f", "b", Field.Store.NO),
                      SortedDocValuesField.indexedField("f", new BytesRef("b")))));

      // Every later valid document with "f" in the same in-memory segment must be accepted.
      int rejected = 0;
      for (int i = 0; i < 5; i++) {
        try {
          w.addDocument(List.of(new StringField("f", "c" + i, Field.Store.NO)));
        } catch (IllegalArgumentException e) {
          rejected++;
        }
      }
      // After a flush (a new in-memory segment) the same documents are accepted again.
      w.flush();
      w.addDocument(List.of(new StringField("f", "d", Field.Store.NO)));
      assertEquals("valid documents rejected before the flush", 0, rejected);
    }
  }
}
