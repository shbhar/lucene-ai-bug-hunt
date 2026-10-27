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
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A crash while PersistentSnapshotDeletionPolicy writes a new save file (snapshots_N) loses the
 * snapshots that an earlier, completed snapshot() call had persisted.
 *
 * <p>The crash state replayed here: snapshot() #1 has returned (snapshots_0 written and fsynced,
 * and its directory entry made durable by the following commit's directory fsync); then, during
 * snapshot() #2, persist() has created snapshots_1 but the machine fails before its bytes reach
 * stable storage (persist() fsyncs the file only after closing it). The durable state is every
 * file of the index plus snapshots_1 with empty or partial content.
 */
@LuceneTestCase.SuppressFileSystems("*")
public class TestPersistentSnapshotBrokenSaveFile extends LuceneTestCase {

  private static IndexWriterConfig config(Directory dir) throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setIndexDeletionPolicy(
        new PersistentSnapshotDeletionPolicy(
            new KeepOnlyLastCommitDeletionPolicy(), dir, OpenMode.CREATE_OR_APPEND));
    return iwc;
  }

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  private static void copy(Directory from, Directory to, String name, long length)
      throws IOException {
    try (IndexInput in = from.openInput(name, IOContext.READONCE);
        IndexOutput out = to.createOutput(name, IOContext.DEFAULT)) {
      out.copyBytes(in, length);
    }
  }

  /**
   * Builds the durable state: all files of the live index after commit #2, plus the save file of
   * the interrupted snapshot() #2 truncated to {@code keepBytes} bytes (-1: drop the file).
   * Returns the generation that snapshot() #1 returned.
   */
  private long buildCrashState(Path live, Path crashed, int keepBytes) throws IOException {
    long snapshotGen;
    try (Directory dir = FSDirectory.open(live);
        Directory out = FSDirectory.open(crashed)) {
      IndexWriterConfig iwc = config(dir);
      PersistentSnapshotDeletionPolicy psdp =
          (PersistentSnapshotDeletionPolicy) iwc.getIndexDeletionPolicy();
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc("a"));
        w.commit(); // segments_1
        snapshotGen = psdp.snapshot().getGeneration(); // returns: snapshots_0 is persisted
        w.addDocument(doc("b"));
        w.commit(); // segments_2; its directory fsync also makes snapshots_0's name durable

        // Every file in the directory is durable at this point. Copy them all:
        for (String f : dir.listAll()) {
          if (f.equals(IndexWriter.WRITE_LOCK_NAME)) continue;
          copy(dir, out, f, dir.fileLength(f));
        }
        // snapshot() #2 runs; its save file is snapshots_1. In the crash state only its name
        // (and keepBytes of its content) reached the disk:
        psdp.snapshot();
        assertEquals("snapshots_1", psdp.getLastSaveFile());
        if (keepBytes >= 0) {
          copy(dir, out, "snapshots_1", Math.min(keepBytes, dir.fileLength("snapshots_1")));
        }
      }
    }
    return snapshotGen;
  }

  private void checkAfterRestart(Path crashed, long snapshotGen) throws IOException {
    List<String> problems = new ArrayList<>();
    try (Directory dir = FSDirectory.open(crashed)) {
      IndexWriterConfig iwc = config(dir);
      PersistentSnapshotDeletionPolicy psdp =
          (PersistentSnapshotDeletionPolicy) iwc.getIndexDeletionPolicy();
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        if (psdp.getIndexCommit(snapshotGen) == null) {
          problems.add(
              "snapshot of generation "
                  + snapshotGen
                  + " taken by a completed snapshot() call is gone after restart (snapshot count="
                  + psdp.getSnapshotCount()
                  + ")");
        }
      }
      boolean commitExists = false;
      for (IndexCommit c : DirectoryReader.listCommits(dir)) {
        commitExists |= c.getGeneration() == snapshotGen;
      }
      if (commitExists == false) {
        problems.add(
            "the snapshotted commit segments_"
                + snapshotGen
                + " was deleted when IndexWriter opened; remaining commits="
                + DirectoryReader.listCommits(dir).stream().map(IndexCommit::getGeneration).toList()
                + "; files="
                + List.of(dir.listAll()));
      }
    }
    assertTrue(String.join("\n", problems), problems.isEmpty());
  }

  /** Control: the crash happened before snapshots_1's name reached the disk. */
  public void testControlSaveFileNameNotDurable() throws Exception {
    Path live = createTempDir("live"), crashed = createTempDir("crashed");
    long gen = buildCrashState(live, crashed, -1);
    checkAfterRestart(crashed, gen);
  }

  /** Name of the new save file is durable, its content is not (zero length). */
  public void testCrashLeavesEmptySaveFile() throws Exception {
    Path live = createTempDir("live"), crashed = createTempDir("crashed");
    long gen = buildCrashState(live, crashed, 0);
    checkAfterRestart(crashed, gen);
  }

  /** Name of the new save file is durable, half of its content is. */
  public void testCrashLeavesTruncatedSaveFile() throws Exception {
    Path live = createTempDir("live"), crashed = createTempDir("crashed");
    long gen = buildCrashState(live, crashed, 12);
    checkAfterRestart(crashed, gen);
  }
}
