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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestOverlongDeleteTermKeepsWriterUsable extends LuceneTestCase {

  public void testCommitAfterOverlongDeleteTerm() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new StringField("id", "1", Field.Store.NO));
      w.addDocument(doc);
      try {
        w.deleteDocuments(new Term("id", new BytesRef(new byte[IndexWriter.MAX_TERM_LENGTH + 1])));
      } catch (RuntimeException rejected) {
      }
      // Expected: commit succeeds with 1 doc; actual: commit throws, the failed delete stays queued.
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.numDocs());
      }
    }
  }
}
