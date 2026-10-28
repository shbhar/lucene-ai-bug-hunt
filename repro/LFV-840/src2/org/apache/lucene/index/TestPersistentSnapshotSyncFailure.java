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

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.util.LuceneTestCase;

/**
 * PersistentSnapshotDeletionPolicy.persist() writes the next save file snapshots_N and deletes it
 * again if writing fails, but not if the following Directory.sync of that file throws. snapshot()
 * and release() undo their in-memory change when persist() throws, yet the complete new save file
 * stays in the directory and nextWriteGen is not advanced. Then (1) the next snapshot() or
 * release() fails as well, with FileAlreadyExistsException for snapshots_N (its cleanup deletes the
 * leftover, so the call after that works). This is the claim (testSnapshotAfterFailedSnapshot,
 * testReleaseAfterFailedRelease).
 *
 * <p>Secondary observations, contract open (testRestartAfter*): a policy loaded before that next
 * call (a restart) reads the leftover as the newest save file, so a snapshot() that threw is in
 * effect and a release() that threw has taken effect. Since the outcome of a failed fsync is
 * indeterminate, adopting the file can be argued to be acceptable.
 */
@LuceneTestCase.SuppressFileSystems("*")
public class TestPersistentSnapshotSyncFailure extends LuceneTestCase {

  /** Directory.sync of the policy's save file throws once (no effect). */
  static final class FailSaveFileSync extends FilterDirectory {
    boolean armed;

    FailSaveFileSync(Directory in) {
      super(in);
    }

    @Override
    public void sync(Collection<String> names) throws IOException {
      if (armed && names.stream().anyMatch(n -> n.startsWith("snapshots_"))) {
        armed = false;
        throw new IOException("injected: fsync of the save file failed");
      }
      super.sync(names);
    }
  }

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  private static PersistentSnapshotDeletionPolicy policy(Directory dir) throws IOException {
    return new PersistentSnapshotDeletionPolicy(
        new KeepOnlyLastCommitDeletionPolicy(), dir, IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
  }

  private static IndexWriter writer(Directory dir, PersistentSnapshotDeletionPolicy p)
      throws IOException {
    return new IndexWriter(dir, new IndexWriterConfig().setIndexDeletionPolicy(p));
  }

  /** Control: without a fault, snapshot() twice works and a restart sees both snapshots. */
  public void testControlNoFault() throws Exception {
    try (FailSaveFileSync dir = new FailSaveFileSync(FSDirectory.open(createTempDir()))) {
      PersistentSnapshotDeletionPolicy p = policy(dir);
      try (IndexWriter w = writer(dir, p)) {
        w.addDocument(doc("a"));
        w.commit();
        p.snapshot();
        p.snapshot();
        assertEquals(2, p.getSnapshotCount());
      }
      assertEquals(2, policy(dir).getSnapshotCount());
    }
  }

  /** One failed fsync of the save file makes the next, fault-free snapshot() fail too. */
  public void testSnapshotAfterFailedSnapshot() throws Exception {
    try (FailSaveFileSync dir = new FailSaveFileSync(FSDirectory.open(createTempDir()))) {
      PersistentSnapshotDeletionPolicy p = policy(dir);
      try (IndexWriter w = writer(dir, p)) {
        w.addDocument(doc("a"));
        w.commit();
        dir.armed = true;
        expectThrows(IOException.class, p::snapshot);
        assertEquals("the failed snapshot() was undone in memory", 0, p.getSnapshotCount());
        List<String> files = List.of(dir.listAll());
        try {
          p.snapshot(); // no fault injected any more
        } catch (IOException e) {
          fail(
              "snapshot() after one failed save-file fsync must work, but threw "
                  + e
                  + "; files before the call: "
                  + files);
        }
        assertEquals(1, p.getSnapshotCount());
      }
    }
  }

  /** One failed fsync of the save file makes the next, fault-free release() fail too. */
  public void testReleaseAfterFailedRelease() throws Exception {
    try (FailSaveFileSync dir = new FailSaveFileSync(FSDirectory.open(createTempDir()))) {
      PersistentSnapshotDeletionPolicy p = policy(dir);
      try (IndexWriter w = writer(dir, p)) {
        w.addDocument(doc("a"));
        w.commit();
        IndexCommit s = p.snapshot();
        dir.armed = true;
        expectThrows(IOException.class, () -> p.release(s));
        assertEquals("the failed release() was undone in memory", 1, p.getSnapshotCount());
        List<String> files = List.of(dir.listAll());
        try {
          p.release(s); // no fault injected any more
        } catch (IOException e) {
          fail(
              "release() after one failed save-file fsync must work, but threw "
                  + e
                  + "; files before the call: "
                  + files);
        }
        assertEquals(0, p.getSnapshotCount());
      }
    }
  }

  /** Secondary (contract open): a restart after a snapshot() that threw finds it in effect. */
  public void testRestartAfterFailedSnapshot() throws Exception {
    try (FailSaveFileSync dir = new FailSaveFileSync(FSDirectory.open(createTempDir()))) {
      PersistentSnapshotDeletionPolicy p = policy(dir);
      try (IndexWriter w = writer(dir, p)) {
        w.addDocument(doc("a"));
        w.commit();
        dir.armed = true;
        expectThrows(IOException.class, p::snapshot);
        assertEquals("the failed snapshot() was undone in memory", 0, p.getSnapshotCount());
      }
      List<String> files = List.of(dir.listAll());
      PersistentSnapshotDeletionPolicy reloaded = policy(dir);
      assertEquals(
          "after a restart, a snapshot() that threw must not be in effect; files=" + files,
          0,
          reloaded.getSnapshotCount());
    }
  }

  /** Secondary (contract open): a restart after a release() that threw finds it in effect. */
  public void testRestartAfterFailedRelease() throws Exception {
    try (FailSaveFileSync dir = new FailSaveFileSync(FSDirectory.open(createTempDir()))) {
      PersistentSnapshotDeletionPolicy p = policy(dir);
      try (IndexWriter w = writer(dir, p)) {
        w.addDocument(doc("a"));
        w.commit();
        IndexCommit s = p.snapshot();
        dir.armed = true;
        expectThrows(IOException.class, () -> p.release(s));
        assertEquals("the failed release() was undone in memory", 1, p.getSnapshotCount());
      }
      List<String> files = List.of(dir.listAll());
      PersistentSnapshotDeletionPolicy reloaded = policy(dir);
      assertEquals(
          "after a restart, a release() that threw must not be in effect; files=" + files,
          1,
          reloaded.getSnapshotCount());
    }
  }
}
