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
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortedNumericSortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * IndexWriter#updateDocValues rejects some calls with IllegalArgumentException (an index-sort field,
 * or a doc-values type that cannot be updated), but only after it has registered the field, as a
 * doc-values-only field, in the writer's global field schema. A later, legal document that indexes
 * the field with another schema is then rejected for the rest of the writer session, although no
 * document or accepted update ever used the field.
 *
 * <p>Public API only.
 */
public class TestRejectedDocValuesUpdateRegistersField extends LuceneTestCase {

  /** Index sorted on "price"; a rejected update of "price" breaks indexing of "price". */
  public void testIndexSortRejection() throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setIndexSort(new Sort(new SortedNumericSortField("price", SortField.Type.LONG)));
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      // documented rejection: "cannot update docvalues field involved in the index sort"
      expectThrows(
          IllegalArgumentException.class,
          () ->
              w.updateDocValues(
                  new Term("id", "0"), new SortedNumericDocValuesField("price", 5L)));
      // a legal document: points + SORTED_NUMERIC doc values, which the index sort accepts
      Document doc = new Document();
      doc.add(new StringField("id", "0", Store.NO));
      doc.add(new LongField("price", 3L, Store.NO));
      w.addDocument(doc);
      assertEquals(1, w.getDocStats().numDocs);
    }
  }

  /** A rejected update with a non-updatable doc-values type breaks indexing of that field. */
  public void testUnsupportedTypeRejection() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
      // documented rejection: SORTED doc values cannot be updated
      expectThrows(
          IllegalArgumentException.class,
          () ->
              w.updateDocValues(
                  new Term("id", "0"), new SortedDocValuesField("tag", new BytesRef("a"))));
      // a legal document: an indexed keyword with SORTED doc values
      Document doc = new Document();
      doc.add(new StringField("id", "0", Store.NO));
      doc.add(new StringField("tag", "a", Store.NO));
      doc.add(new SortedDocValuesField("tag", new BytesRef("a")));
      w.addDocument(doc);
      assertEquals(1, w.getDocStats().numDocs);
    }
  }

  /** softUpdateDocuments shares the argument checks: a rejected soft-delete marker field. */
  public void testSoftUpdateDocumentsRejection() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig(new MockAnalyzer(random())).setSoftDeletesField("soft");
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      expectThrows(
          IllegalArgumentException.class,
          () ->
              w.softUpdateDocuments(
                  new Term("id", "0"),
                  List.of(new Document()),
                  new SortedDocValuesField("tag", new BytesRef("a"))));
      Document doc = new Document();
      doc.add(new StringField("id", "0", Store.NO));
      doc.add(new StringField("tag", "a", Store.NO));
      doc.add(new SortedDocValuesField("tag", new BytesRef("a")));
      w.addDocument(doc);
      assertEquals(1, w.getDocStats().numDocs);
    }
  }

  /** Same root cause with two fields: the first one is registered before the second is rejected. */
  public void testMultiFieldRejection() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
      expectThrows(
          IllegalArgumentException.class,
          () ->
              w.updateDocValues(
                  new Term("id", "0"),
                  new SortedNumericDocValuesField("price", 5L),
                  new SortedDocValuesField("tag", new BytesRef("a"))));
      Document doc = new Document();
      doc.add(new StringField("id", "0", Store.NO));
      doc.add(new LongField("price", 3L, Store.NO));
      w.addDocument(doc);
      assertEquals(1, w.getDocStats().numDocs);
    }
  }
}
