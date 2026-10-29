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
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestReopenedPersistentSnapshotGetIndexCommit extends LuceneTestCase {

  public void testGetIndexCommitBeforeWriterOpens() throws Exception {
    try (Directory dir = newDirectory()) {
      PersistentSnapshotDeletionPolicy psdp =
          new PersistentSnapshotDeletionPolicy(
              new KeepOnlyLastCommitDeletionPolicy(), dir, OpenMode.CREATE);
      long gen;
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setIndexDeletionPolicy(psdp))) {
        w.addDocument(new Document());
        w.commit();
        gen = psdp.snapshot().getGeneration();
      }

      PersistentSnapshotDeletionPolicy reopened =
          new PersistentSnapshotDeletionPolicy(
              new KeepOnlyLastCommitDeletionPolicy(), dir, OpenMode.APPEND);
      assertEquals(1, reopened.getSnapshotCount());
      // expected: the snapshotted commit (null only if "not currently snapshotted"); actual: null
      assertNotNull(
          "expected the IndexCommit of snapshotted generation " + gen + ", got null",
          reopened.getIndexCommit(gen));
    }
  }
}
