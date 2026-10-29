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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIndexWriterHasDeletionsSoftDeletes extends LuceneTestCase {

  public void testCommittedSoftDeleteAfterWriterReopen() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      Document doc = new Document();
      doc.add(new StringField("id", "1", Field.Store.NO));
      IndexWriterConfig iwc = new IndexWriterConfig().setSoftDeletesField("soft");
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc);
        w.softUpdateDocument(new Term("id", "1"), doc, new NumericDocValuesField("soft", 1));
      }
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setSoftDeletesField("soft"))) {
        IndexWriter.DocStats stats = w.getDocStats();
        // expected true like getDocStats() (maxDoc 2, numDocs 1), actual false
        assertEquals(
            "hasDeletions() vs getDocStats() maxDoc=" + stats.maxDoc + " numDocs=" + stats.numDocs,
            stats.maxDoc > stats.numDocs,
            w.hasDeletions());
      }
    }
  }
}
