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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.LuceneTestCase;

/**
 * PersistentSnapshotDeletionPolicy.snapshot() returns ("Once this method returns, the snapshot
 * information is persisted in the directory") although the directory entry of the new save file
 * was never made durable: persist() fsyncs the file's bytes but never calls
 * Directory.syncMetaData(). A crash right after snapshot() returns can therefore lose the
 * snapshot.
 *
 * <p>File-system model (the one IndexWriter itself follows since LUCENE-5588 / LUCENE-8048): file
 * data is durable after Directory.sync of that file; a create, rename or delete is durable only
 * after the next Directory.syncMetaData() of its directory. The crash state replayed here keeps,
 * for each directory, exactly the names that existed at its last syncMetaData() (none of the
 * later directory operations reached the disk), with their synced content.
 */
@LuceneTestCase.SuppressFileSystems("*")
public class TestPersistentSnapshotSaveFileNotDirSynced extends LuceneTestCase {

  /** Tracks which names were durable at the last syncMetaData() and which files were synced. */
  static final class DurabilityTracker extends FilterDirectory {
    Set<String> durableNames = new TreeSet<>();
    final Set<String> synced = new HashSet<>();

    DurabilityTracker(Directory in) {
      super(in);
    }

    @Override
    public void sync(java.util.Collection<String> names) throws IOException {
      super.sync(names);
      synced.addAll(names);
    }

    @Override
    public void rename(String source, String dest) throws IOException {
      super.rename(source, dest);
      if (synced.remove(source)) synced.add(dest);
    }

    @Override
    public void syncMetaData() throws IOException {
      super.syncMetaData();
      durableNames = new TreeSet<>(List.of(listAll()));
      durableNames.remove(IndexWriter.WRITE_LOCK_NAME);
    }

    /** Copies the durable state (durable names whose bytes were synced) into {@code to}. */
    List<String> copyDurableStateTo(Directory to) throws IOException {
      List<String> copied = new ArrayList<>();
      for (String name : durableNames) {
        assertTrue("durable name " + name + " must have synced bytes", synced.contains(name));
        try (IndexInput in = openInput(name, IOContext.READONCE);
            IndexOutput out = to.createOutput(name, IOContext.DEFAULT)) {
          out.copyBytes(in, in.length());
        }
        copied.add(name);
      }
      return copied;
    }
  }

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  private static IndexWriterConfig config(Directory snapshotDir) throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setIndexDeletionPolicy(
        new PersistentSnapshotDeletionPolicy(
            new KeepOnlyLastCommitDeletionPolicy(), snapshotDir, OpenMode.CREATE_OR_APPEND));
    return iwc;
  }

  /** Protocol check: once snapshot() has returned, the save file's name must be durable. */
  public void testSaveFileNameDurableWhenSnapshotReturns() throws Exception {
    try (DurabilityTracker dir = new DurabilityTracker(FSDirectory.open(createTempDir("idx")))) {
      IndexWriterConfig iwc = config(dir);
      PersistentSnapshotDeletionPolicy psdp =
          (PersistentSnapshotDeletionPolicy) iwc.getIndexDeletionPolicy();
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc("a"));
        w.commit();
        psdp.snapshot();
        String saveFile = psdp.getLastSaveFile();
        assertTrue("save file bytes are fsynced", dir.synced.contains(saveFile));
        assertTrue(
            "snapshot() returned, but no Directory.syncMetaData() has made the name of its save file "
                + saveFile
                + " durable; durable names="
                + dir.durableNames,
            dir.durableNames.contains(saveFile));
      }
    }
  }

  /**
   * Snapshots kept in their own directory (documented as the normal setup): IndexWriter commits
   * fsync only the index directory, so the snapshot directory is never fsynced. After a crash the
   * snapshot is gone and IndexWriter deletes the snapshotted commit.
   */
  public void testCrashAfterSnapshotReturnedSeparateSnapshotDir() throws Exception {
    Path idxCrashed = createTempDir("idx-crashed"), snapCrashed = createTempDir("snap-crashed");
    long snapshotGen;
    try (DurabilityTracker idx = new DurabilityTracker(FSDirectory.open(createTempDir("idx")));
        DurabilityTracker snap = new DurabilityTracker(FSDirectory.open(createTempDir("snap")))) {
      IndexWriterConfig iwc = config(snap);
      PersistentSnapshotDeletionPolicy psdp =
          (PersistentSnapshotDeletionPolicy) iwc.getIndexDeletionPolicy();
      try (IndexWriter w = new IndexWriter(idx, iwc)) {
        w.addDocument(doc("a"));
        w.commit(); // segments_1
        snapshotGen = psdp.snapshot().getGeneration(); // returned: "persisted"
        w.addDocument(doc("b"));
        w.commit(); // segments_2 (durable: IndexWriter fsyncs the index directory)
        // crash now
        try (Directory a = FSDirectory.open(idxCrashed);
            Directory b = FSDirectory.open(snapCrashed)) {
          idx.copyDurableStateTo(a);
          snap.copyDurableStateTo(b);
        }
      }
    }
    try (Directory idx = FSDirectory.open(idxCrashed);
        Directory snap = FSDirectory.open(snapCrashed)) {
      IndexWriterConfig iwc = config(snap);
      PersistentSnapshotDeletionPolicy psdp =
          (PersistentSnapshotDeletionPolicy) iwc.getIndexDeletionPolicy();
      int count;
      try (IndexWriter w = new IndexWriter(idx, iwc)) {
        count = psdp.getSnapshotCount();
      }
      List<Long> gens = new ArrayList<>();
      for (IndexCommit c : DirectoryReader.listCommits(idx)) gens.add(c.getGeneration());
      assertTrue(
          "after the crash the snapshot of generation "
              + snapshotGen
              + " is lost (snapshot count="
              + count
              + ") and IndexWriter deleted its commit; remaining commits="
              + gens
              + " snapshot dir files="
              + List.of(snap.listAll()),
          count == 1 && gens.contains(snapshotGen));
    }
  }

  /** Snapshots in the index directory, crash right after snapshot() returned (no later commit). */
  public void testCrashAfterSnapshotReturnedSameDir() throws Exception {
    Path crashed = createTempDir("crashed");
    try (DurabilityTracker dir = new DurabilityTracker(FSDirectory.open(createTempDir("idx")))) {
      IndexWriterConfig iwc = config(dir);
      PersistentSnapshotDeletionPolicy psdp =
          (PersistentSnapshotDeletionPolicy) iwc.getIndexDeletionPolicy();
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc("a"));
        w.commit();
        psdp.snapshot();
        // crash now
        try (Directory a = FSDirectory.open(crashed)) {
          dir.copyDurableStateTo(a);
        }
      }
    }
    try (Directory dir = FSDirectory.open(crashed)) {
      IndexWriterConfig iwc = config(dir);
      PersistentSnapshotDeletionPolicy psdp =
          (PersistentSnapshotDeletionPolicy) iwc.getIndexDeletionPolicy();
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        assertEquals(
            "snapshot taken by a completed snapshot() call must survive a crash; files="
                + List.of(dir.listAll()),
            1,
            psdp.getSnapshotCount());
      }
    }
  }
}
