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
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A document whose index-sort field is indexed without doc values (here as a StringField) is
 * accepted by addDocument; the next flush then throws and closes the IndexWriter, losing the other
 * buffered documents. A doc-values field of the wrong type is rejected up front by addDocument
 * (TestIndexSorting#testWrongSortFieldType).
 */
public class TestIndexSortFieldWithoutDocValues extends LuceneTestCase {

  private static Document doc(String id, boolean withSortFieldAsKeyword) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    if (withSortFieldAsKeyword) {
      doc.add(new StringField("sort", "x", Field.Store.NO)); // no doc values
    }
    return doc;
  }

  private void check(SortField sortField) throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setIndexSort(new Sort(sortField)));
      w.addDocument(doc("0", false));
      w.commit();
      w.addDocument(doc("1", false)); // buffered, not yet committed
      boolean accepted = true;
      try {
        w.addDocument(doc("2", true));
      } catch (IllegalArgumentException rejected) {
        accepted = false; // rejected up front, like a doc-values field of the wrong type: fine
      }
      try {
        w.commit();
      } catch (IllegalStateException e) {
        int committed;
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          committed = r.numDocs();
        }
        fail(sortField + ": addDocument accepted the document, then commit threw " + e
            + "; writer open=" + w.isOpen() + ", tragic=" + (w.getTragicException() != null)
            + ", documents in the index: " + committed + " (doc 1 was added successfully before doc 2)");
      }
      assertTrue(w.isOpen());
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(accepted ? 3 : 2, r.numDocs());
      }
      w.close();
    }
  }

  public void testLongSort() throws Exception {
    check(new SortField("sort", SortField.Type.LONG));
  }

  public void testStringSort() throws Exception {
    check(new SortField("sort", SortField.Type.STRING));
  }
}
