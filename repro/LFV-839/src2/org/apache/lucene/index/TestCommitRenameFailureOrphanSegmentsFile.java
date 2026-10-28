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
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.util.LuceneTestCase;

/**
 * No crash needed: a commit's Directory.rename(pending_segments_N, segments_N) throws an IOException
 * after the rename took effect (Directory.rename need not be atomic: "both source and dest can be
 * visible temporarily"). SegmentInfos.finishCommit deletes only pending_segments_N, so segments_N
 * stays in the directory, while IndexWriter.finishCommit releases the failed commit's file
 * references. Files that only the failed commit referenced (segments merged away after the commit
 * point was taken) are deleted at once, so the newest commit in the directory now references
 * deleted files: until the writer commits again or rolls back, DirectoryReader.open fails, and if
 * the process stops in that window the index cannot be opened after a restart.
 *
 * <p>Oracle: after a commit() that threw, the directory must still open to the last acknowledged
 * commit (doc "a") or the complete failed commit (docs "a", "b").
 */
@LuceneTestCase.SuppressFileSystems("*")
public class TestCommitRenameFailureOrphanSegmentsFile extends LuceneTestCase {

  /** The next segments_N rename takes effect and then throws; optional hook inside sync(). */
  static final class RenameTookEffectThenThrows extends FilterDirectory {
    boolean armed;
    Runnable duringNextSync;

    RenameTookEffectThenThrows(Directory in) {
      super(in);
    }

    @Override
    public void sync(Collection<String> names) throws IOException {
      Runnable r = duringNextSync;
      if (r != null && names.size() > 1) { // IndexWriter.startCommit syncs the commit's files
        duringNextSync = null;
        r.run();
      }
      super.sync(names);
    }

    @Override
    public void rename(String source, String dest) throws IOException {
      super.rename(source, dest);
      if (armed && dest.startsWith(IndexFileNames.SEGMENTS)) {
        armed = false;
        throw new IOException("injected: rename reported failure after taking effect");
      }
    }
  }

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  private static String open(Directory d) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(d)) {
      TreeSet<String> ids = new TreeSet<>();

      for (int i = 0; i < r.maxDoc(); i++) ids.add(r.document(i).get("id"));
      return ids.toString();
    } catch (IOException e) {
      return "cannot open: " + e;
    }
  }

  /** a copy of every file: what a restarted process finds if this one stops now. */
  private Directory restartCopy(Directory live) throws IOException {
    Directory copy = FSDirectory.open(createTempDir("after-restart"));
    for (String n : live.listAll()) {
      if (n.equals(IndexWriter.WRITE_LOCK_NAME) == false) copy.copyFrom(live, n, n, IOContext.DEFAULT);
    }
    return copy;
  }

  private static String newWriter(Directory d) {
    try (IndexWriter w = new IndexWriter(d, new IndexWriterConfig())) {
      return "IndexWriter opened";
    } catch (IOException e) {
      return "IndexWriter cannot open: " + e;
    }
  }

  private void check(RenameTookEffectThenThrows dir, IndexWriter w) throws IOException {
    TreeSet<String> files = new TreeSet<>(List.of(dir.listAll()));
    String live = open(dir);
    String restarted;
    String writer;
    try (Directory copy = restartCopy(dir)) {
      restarted = open(copy);
      writer = newWriter(copy);
    }
    w.rollback();
    assertTrue(
        "after commit() threw at a rename that took effect, the index must still open to the"
            + " acknowledged commit [a] or the complete failed commit [a, b]; files="
            + files
            + " live open: "
            + live
            + "; after a process restart: "
            + restarted
            + "; new IndexWriter after restart: "
            + writer,
        live.equals("[a]") || live.equals("[a, b]"));
  }

  /** Two-phase commit: segments merged away between prepareCommit() and commit(). */
  public void testMergeBetweenPrepareCommitAndCommit() throws Exception {
    try (RenameTookEffectThenThrows dir =
        new RenameTookEffectThenThrows(FSDirectory.open(createTempDir("live")))) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
      w.addDocument(doc("a"));
      w.commit(); // segments_1 -> _0 (acknowledged)
      w.addDocument(doc("b"));
      w.flush(); // _1
      w.prepareCommit(); // pending_segments_2 -> _0, _1
      w.forceMerge(1); // _0 + _1 -> _2; only the pending commit still references _1
      dir.armed = true;
      expectThrows(IOException.class, w::commit);
      assertTrue("writer stays open after a non-tragic commit failure", w.isOpen());
      check(dir, w);
    }
  }

  /** Plain commit(): a merge finishes while the commit fsyncs its files (the case the comment in
   * IndexWriter.prepareCommitInternal describes), driven deterministically from sync(). */
  public void testMergeFinishesDuringCommitFsync() throws Exception {
    try (RenameTookEffectThenThrows dir =
        new RenameTookEffectThenThrows(FSDirectory.open(createTempDir("live")))) {
      IndexWriterConfig iwc = new IndexWriterConfig().setMergeScheduler(new SerialMergeScheduler());
      IndexWriter w = new IndexWriter(dir, iwc);
      w.addDocument(doc("a"));
      w.commit(); // segments_1 -> _0 (acknowledged)
      w.addDocument(doc("b"));
      w.flush(); // _1
      AtomicReference<Throwable> mergeFailure = new AtomicReference<>();
      dir.duringNextSync =
          () -> {
            Thread t =
                new Thread(
                    () -> {
                      try {
                        w.forceMerge(1); // _0 + _1 -> _2 while the commit (-> _0, _1) is in sync
                      } catch (Throwable e) {
                        mergeFailure.set(e);
                      }
                    });
            t.start();
            try {
              t.join();
            } catch (InterruptedException e) {
              throw new AssertionError(e);
            }
          };
      dir.armed = true;
      expectThrows(IOException.class, w::commit);
      assertNull("merge during the commit's fsync", mergeFailure.get());
      assertTrue("writer stays open after a non-tragic commit failure", w.isOpen());
      check(dir, w);
    }
  }
}
