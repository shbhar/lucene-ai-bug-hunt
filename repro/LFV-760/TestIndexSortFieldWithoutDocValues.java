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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * With an index sort on "sort", one document that indexes "sort" without doc values must not make
 * the next commit close the IndexWriter and drop the other buffered documents. The document may be
 * rejected by addDocument (as a doc-values field of the wrong type is) or kept; either is fine.
 */
public class TestIndexSortFieldWithoutDocValues extends LuceneTestCase {

  public void testStringSortFieldIndexedOnlyAsStringField() throws Exception {
    assertBadDocumentDoesNotCloseWriter(
        new SortField("sort", SortField.Type.STRING), new StringField("sort", "x", Field.Store.NO));
  }

  public void testLongSortFieldIndexedOnlyAsLongPoint() throws Exception {
    assertBadDocumentDoesNotCloseWriter(
        new SortField("sort", SortField.Type.LONG), new LongPoint("sort", 42L));
  }

  /** Control: a doc-values field of the wrong type is rejected by addDocument (LUCENE-8441). */
  public void testWrongDocValuesTypeIsRejectedUpFront() throws Exception {
    assertBadDocumentDoesNotCloseWriter(
        new SortField("sort", SortField.Type.STRING), new NumericDocValuesField("sort", 42L));
  }

  private static Document doc(String id) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    return doc;
  }

  private void assertBadDocumentDoesNotCloseWriter(SortField sortField, IndexableField badField)
      throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setIndexSort(new Sort(sortField)));
      w.addDocument(doc("0"));
      w.commit(); // doc 0 is committed; no document has the "sort" field yet
      w.addDocument(doc("1")); // doc 1 is buffered
      Document bad = doc("2");
      bad.add(badField);
      boolean accepted = true;
      try {
        w.addDocument(bad);
      } catch (IllegalArgumentException _) {
        accepted = false; // rejected up front, the writer stays usable: fine
      }
      try {
        w.commit();
      } catch (IllegalStateException e) {
        int inIndex;
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          inIndex = r.numDocs();
        }
        fail(
            sortField
                + " with "
                + badField
                + ": addDocument accepted doc 2, then commit threw "
                + e
                + "; writer open="
                + w.isOpen()
                + ", tragic="
                + (w.getTragicException() != null)
                + ", documents in the index: "
                + inIndex
                + " (docs 0 and 1 were added before doc 2)");
      }
      assertTrue(w.isOpen());
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(accepted ? 3 : 2, r.numDocs());
      }
      w.close();
    }
  }
}
