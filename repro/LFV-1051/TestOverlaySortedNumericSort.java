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
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortedNumericSortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestOverlaySortedNumericSort extends LuceneTestCase {

  public void testMinSortAfterUpdate() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        w.addDocument(doc("0", 50, 60));
        w.addDocument(doc("1", 10, 90));
        w.addDocument(doc("2", 30, 40));
        w.commit();
        w.updateSortedNumericDocValue(new Term("id", "2"), "m", 70);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Sort sort = new Sort(new SortedNumericSortField("m", SortField.Type.LONG));
        TopDocs top = new IndexSearcher(r).search(MatchAllDocsQuery.INSTANCE, 1, sort);
        // expected: doc 1 (min 10) on top; actual on main: doc 2 (doc 1 is re-read as 90)
        assertEquals(1, top.scoreDocs[0].doc);
      }
    }
  }

  private static Document doc(String id, long... values) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.NO));
    for (long v : values) {
      doc.add(new SortedNumericDocValuesField("m", v));
    }
    return doc;
  }
}
