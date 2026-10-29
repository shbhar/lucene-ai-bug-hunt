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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestReopenAfterTwoRollbacks extends LuceneTestCase {

  public void testReopenNrtReaderOnCommitAfterTwoRollbacks() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w1 = new IndexWriter(dir, new IndexWriterConfig());
      w1.addDocument(new Document());
      DirectoryReader r = DirectoryReader.open(w1);
      w1.rollback();
      new IndexWriter(dir, new IndexWriterConfig()).rollback();
      IndexWriter w3 = new IndexWriter(dir, new IndexWriterConfig());
      w3.addDocument(new Document());
      w3.addDocument(new Document());
      w3.close();
      IndexCommit commit = DirectoryReader.listCommits(dir).get(0);
      // expected a new 2-doc reader on w3's commit, actual IllegalStateException "same segment _0"
      DirectoryReader r2 = DirectoryReader.openIfChanged(r, commit);
      assertEquals(2, r2.numDocs());
      r2.close();
      r.close();
    }
  }
}
