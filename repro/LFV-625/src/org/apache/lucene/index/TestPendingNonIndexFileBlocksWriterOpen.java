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

import java.nio.file.Path;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.tests.mockfile.WindowsFS;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A file that is not an index file but is pending deletion in the index directory (its delete was
 * refused because another process still had it open, as on Windows) makes IndexFileDeleter parse
 * its name as a segment name when the next IndexWriter opens. WindowsFS (test framework) gives the
 * Windows semantics: an open file cannot be deleted, so FSDirectory keeps it as a pending delete.
 */
public class TestPendingNonIndexFileBlocksWriterOpen extends LuceneTestCase {

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  /**
   * In-tree trigger: PersistentSnapshotDeletionPolicy on the index directory ("It is OK to use the
   * same Directory that holds the index"); release() writes snapshots_1 and deletes snapshots_0,
   * which a backup tool still has open.
   */
  public void testSnapshotSaveFilePendingDelete() throws Exception {
    Path path = createTempDir();
    WindowsFS provider = new WindowsFS(path.getFileSystem());
    Path root = provider.wrapPath(path);
    IndexInput heldByOtherProcess = null;
    // NIOFSDirectory keeps its file handle open (MMapDirectory closes it after mapping)
    try (FSDirectory dir = new NIOFSDirectory(root)) {
      PersistentSnapshotDeletionPolicy psdp =
          new PersistentSnapshotDeletionPolicy(new KeepOnlyLastCommitDeletionPolicy(), dir);
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setIndexDeletionPolicy(psdp))) {
        w.addDocument(doc("1"));
        w.commit();
        IndexCommit snapshot = psdp.snapshot(); // persists snapshots_0
        heldByOtherProcess = dir.openInput(psdp.getLastSaveFile(), IOContext.READONCE);
        psdp.release(snapshot); // persists snapshots_1, deleting snapshots_0 is refused
      }
      assertTrue(dir.getPendingDeletions().toString(), dir.getPendingDeletions().contains("snapshots_0"));

      // A new IndexWriter on the same, valid index must open:
      try (IndexWriter w =
          new IndexWriter(
              dir,
              new IndexWriterConfig()
                  .setIndexDeletionPolicy(
                      new PersistentSnapshotDeletionPolicy(
                          new KeepOnlyLastCommitDeletionPolicy(), dir)))) {
        w.addDocument(doc("2"));
        w.commit();
        assertEquals(2, w.getDocStats().numDocs);
      } finally {
        heldByOtherProcess.close();
      }
    }
  }

  /** Same with an application's own file kept in the index directory. */
  public void testApplicationFilePendingDelete() throws Exception {
    Path path = createTempDir();
    WindowsFS provider = new WindowsFS(path.getFileSystem());
    Path root = provider.wrapPath(path);
    IndexInput heldByOtherProcess = null;
    try (FSDirectory dir = new NIOFSDirectory(root)) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(doc("1"));
        w.commit();
      }
      try (IndexOutput out = dir.createOutput("app-notes.txt", IOContext.DEFAULT)) {
        out.writeInt(42);
      }
      heldByOtherProcess = dir.openInput("app-notes.txt", IOContext.READONCE);
      dir.deleteFile("app-notes.txt"); // refused while open: now pending
      assertTrue(dir.getPendingDeletions().contains("app-notes.txt"));

      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(doc("2"));
        w.commit();
        assertEquals(2, w.getDocStats().numDocs);
      } finally {
        heldByOtherProcess.close();
      }
    }
  }
}
