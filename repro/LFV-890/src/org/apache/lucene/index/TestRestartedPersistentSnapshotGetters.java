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
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A PersistentSnapshotDeletionPolicy re-created over its save file reports the persisted snapshot
 * count at once (TestPersistentSnapshotDeletionPolicy#testSnapshotRelease relies on that), but
 * lists none of those snapshots and cannot release them by generation until an IndexWriter has
 * been opened with it. The javadoc of getSnapshots(), getIndexCommit(long) and release(long) does
 * not say so.
 */
public class TestRestartedPersistentSnapshotGetters extends LuceneTestCase {

  private long snapshotOneCommit(Directory dir) throws Exception {
    PersistentSnapshotDeletionPolicy psdp =
        new PersistentSnapshotDeletionPolicy(
            new KeepOnlyLastCommitDeletionPolicy(), dir, OpenMode.CREATE);
    try (IndexWriter writer =
        new IndexWriter(
            dir, newIndexWriterConfig(new MockAnalyzer(random())).setIndexDeletionPolicy(psdp))) {
      writer.addDocument(new Document());
      writer.commit();
      return psdp.snapshot().getGeneration();
    }
  }

  /** getSnapshots(): "Returns all IndexCommits held by at least one snapshot." */
  public void testRestartedPolicyListsWhatItCounts() throws Exception {
    try (Directory dir = newDirectory()) {
      long gen = snapshotOneCommit(dir);

      PersistentSnapshotDeletionPolicy restarted =
          new PersistentSnapshotDeletionPolicy(
              new KeepOnlyLastCommitDeletionPolicy(), dir, OpenMode.APPEND);
      assertEquals(1, restarted.getSnapshotCount());
      assertEquals(
          "getSnapshots() must list the snapshot that getSnapshotCount() counts",
          restarted.getSnapshotCount(),
          restarted.getSnapshots().size());
      assertNotNull("getIndexCommit(" + gen + ")", restarted.getIndexCommit(gen));
    }
  }

  /** release(long): the class javadoc offers it to release a previous snapshot's generation. */
  public void testRestartedPolicyReleasesByGeneration() throws Exception {
    try (Directory dir = newDirectory()) {
      long gen = snapshotOneCommit(dir);

      PersistentSnapshotDeletionPolicy restarted =
          new PersistentSnapshotDeletionPolicy(
              new KeepOnlyLastCommitDeletionPolicy(), dir, OpenMode.APPEND);
      restarted.release(gen); // throws IllegalStateException on main
      assertEquals(0, restarted.getSnapshotCount());
    }
  }
}
