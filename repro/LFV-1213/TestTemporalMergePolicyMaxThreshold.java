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
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTemporalMergePolicyMaxThreshold extends LuceneTestCase {

  public void testForceMergeDeletesWithMaxValueMaxThreshold() throws Exception {
    TemporalMergePolicy mp =
        new TemporalMergePolicy().setTemporalField("ts").setMaxThreshold(Integer.MAX_VALUE);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(mp))) {
      for (int i = 0; i < 4; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
        doc.add(new LongPoint("ts", 1_700_000_000L));
        w.addDocument(doc);
        if (i % 2 == 1) {
          w.commit();
        }
      }
      w.deleteDocuments(new Term("id", "0"), new Term("id", "2"));
      // expected: deletes expunged; actual: OutOfMemoryError (array size exceeds VM limit)
      w.forceMergeDeletes();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(0, r.numDeletedDocs());
      }
    }
  }
}
