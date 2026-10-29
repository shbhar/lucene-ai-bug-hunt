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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSoftValueRemovalRevivesHardDeletedDoc extends LuceneTestCase {

  public void testHardDeletedDocStaysDeleted() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig().setSoftDeletesField("soft").setMergePolicy(NoMergePolicy.INSTANCE);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (String id : new String[] {"a", "b", "c"}) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.NO));
        w.addDocument(doc);
      }
      w.updateDocValues(new Term("id", "b"), new NumericDocValuesField("soft", 1));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        w.deleteDocuments(new Term("id", "a"));
        w.tryUpdateDocValue(r, 0, new NumericDocValuesField("soft", (Long) null));
      }
      try (DirectoryReader r = DirectoryReader.open(w)) {
        // a was hard-deleted, then its (absent) soft value was removed: expected 0 hits, actual 1
        assertEquals(0, new IndexSearcher(r).count(new TermQuery(new Term("id", "a"))));
      }
    }
  }
}
