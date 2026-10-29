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
import org.apache.lucene.tests.util.TestUtil;

public class TestTryUndeleteThenSoftDelete extends LuceneTestCase {

  public void testSoftDeleteAfterTryUndeleteIsApplied() throws Exception {
    Term x = new Term("id", "x");
    IndexWriterConfig config =
        new IndexWriterConfig()
            .setSoftDeletesField("soft")
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setCodec(TestUtil.getDefaultCodec());
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config)) {
      for (String id : new String[] {"x", "y"}) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.NO));
        w.addDocument(doc);
      }
      w.commit();
      w.updateDocValues(x, new NumericDocValuesField("soft", 1));
      long undelete, delete;
      try (DirectoryReader r = DirectoryReader.open(w)) {
        undelete = w.tryUpdateDocValue(r, 0, new NumericDocValuesField("soft", null));
        delete = w.updateDocValues(x, new NumericDocValuesField("soft", 2));
      }
      try (DirectoryReader r = DirectoryReader.open(w)) {
        // expected: the soft delete issued after the un-delete deletes x; actual: x stays live
        assertEquals(
            "live x after un-delete (seq " + undelete + ") then soft delete (seq " + delete + ")",
            0,
            new IndexSearcher(r).count(new TermQuery(x)));
      }
    }
  }
}
