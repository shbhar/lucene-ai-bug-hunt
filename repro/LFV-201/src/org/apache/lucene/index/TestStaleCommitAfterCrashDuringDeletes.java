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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * After a commit, IndexFileDeleter removes the previous commit: it deletes segments_N first and
 * then the files only that commit referenced ("We do these first to be certain stale commit points
 * are removed before we remove any files they reference, in case we crash right now",
 * FileDeleter). No Directory.syncMetaData() separates the two passes, so the order is not durable.
 *
 * <p>File-system model (the one Lucene adopted in LUCENE-8048, "Filesystems do not guarantee order
 * of directories updates"): directory operations issued since the last directory fsync may reach
 * stable storage in any combination. The crash state replayed here: the new commit segments_2 is
 * durable, the deletion of the old segments_1 did not reach the disk, the deletion of one file
 * that only segments_1 referenced did. DirectoryReader opens segments_2 fine, but IndexWriter
 * cannot open the index and CheckIndex reports it as corrupt.
 */
@LuceneTestCase.SuppressFileSystems("*")
public class TestStaleCommitAfterCrashDuringDeletes extends LuceneTestCase {

  /** Records the directory operations since the last syncMetaData() and keeps deleted bytes. */
  static final class OpTracker extends FilterDirectory {
    TreeSet<String> durableNames = new TreeSet<>();
    final List<String> deletedSinceDirSync = new ArrayList<>();
    final Map<String, byte[]> deletedBytes = new HashMap<>();

    OpTracker(Directory in) {
      super(in);
    }

    @Override
    public void deleteFile(String name) throws IOException {
      try (IndexInput in = openInput(name, IOContext.READONCE)) {
        byte[] b = new byte[(int) in.length()];
        in.readBytes(b, 0, b.length);
        deletedBytes.put(name, b);
      }
      super.deleteFile(name);
      deletedSinceDirSync.add(name);
    }

    @Override
    public void syncMetaData() throws IOException {
      super.syncMetaData();
      durableNames = new TreeSet<>(List.of(listAll()));
      durableNames.remove(IndexWriter.WRITE_LOCK_NAME);
      deletedSinceDirSync.clear();
    }

    byte[] bytes(String name) throws IOException {
      byte[] b = deletedBytes.get(name);
      if (b != null) return b;
      try (IndexInput in = openInput(name, IOContext.READONCE)) {
        b = new byte[(int) in.length()];
        in.readBytes(b, 0, b.length);
        return b;
      }
    }
  }

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  /**
   * Runs the workload; returns the crash state's directory: the names durable at the last
   * syncMetaData() (all their bytes are fsynced), minus the deletions in {@code persisted}.
   */
  private Path crashState(OpTracker[] trackerOut, String... persisted) throws IOException {
    Path crashed = createTempDir("crashed");
    try (OpTracker dir = new OpTracker(FSDirectory.open(createTempDir("live")))) {
      trackerOut[0] = dir;
      IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(new LogDocMergePolicy());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc("a"));
        w.commit(); // segments_1 -> _0
        w.addDocument(doc("b"));
        w.forceMerge(1);
        w.commit(); // segments_2 -> _2; then segments_1, _0.*, _1.* are deleted
        for (String name : persisted) {
          // each deletion we let reach the disk must still be pending (not yet covered by a
          // directory fsync), or already gone from the durable state
          assertTrue(
              name, dir.deletedSinceDirSync.contains(name) || !dir.durableNames.contains(name));
        }
        try (Directory out = FSDirectory.open(crashed)) {
          for (String name : dir.durableNames) {
            if (List.of(persisted).contains(name)) continue;
            byte[] b = dir.bytes(name);
            try (IndexOutput o = out.createOutput(name, IOContext.DEFAULT)) {
              o.writeBytes(b, b.length);
            }
          }
        }
      }
    }
    return crashed;
  }

  private void assertRecovers(Path crashed) throws IOException {
    List<String> problems = new ArrayList<>();
    try (Directory dir = FSDirectory.open(crashed)) {
      String files = List.of(dir.listAll()).toString();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals("latest commit is intact", 2, r.numDocs());
      }
      try {
        DirectoryReader.listCommits(dir);
      } catch (IOException e) {
        problems.add("DirectoryReader.listCommits throws " + e);
      }
      try (CheckIndex ci = new CheckIndex(dir)) {
        ci.setInfoStream(null);
        if (ci.checkIndex().clean == false) problems.add("CheckIndex reports the index as not clean");
      }
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(doc("c"));
        w.commit();
      } catch (IOException e) {
        problems.add("new IndexWriter fails: " + e);
      }
      assertTrue("files=" + files + "\n" + String.join("\n", problems), problems.isEmpty());
    }
  }

  /** Control: none of the deletions reached the disk. */
  public void testControlNoDeletionDurable() throws Exception {
    assertRecovers(crashState(new OpTracker[1]));
  }

  /** Control: all deletions reached the disk (the order Lucene intends). */
  public void testControlAllDeletionsDurable() throws Exception {
    OpTracker[] t = new OpTracker[1];
    Path p = crashState(t);
    try (Directory dir = FSDirectory.open(p)) {
      for (String f : t[0].deletedSinceDirSync) dir.deleteFile(f);
    }
    assertRecovers(p);
  }

  /** The deletion of segments_1 is lost; the deletion of _0.si (referenced only by it) is not. */
  public void testStaleCommitMissingSegmentInfo() throws Exception {
    assertRecovers(crashState(new OpTracker[1], "_0.si"));
  }

  /** The deletion of segments_1 is lost; the deletion of _0.cfs (referenced only by it) is not. */
  public void testStaleCommitMissingDataFile() throws Exception {
    assertRecovers(crashState(new OpTracker[1], "_0.cfs"));
  }
}
