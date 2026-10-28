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
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A commit's Directory.rename(pending_segments_N, segments_N) throws an IOException after the
 * rename took effect (Directory.rename need not be atomic: "both source and dest can be visible
 * temporarily"). SegmentInfos.finishCommit then deletes only pending_segments_N and leaves
 * segments_N, which IndexFileDeleter does not know about. When the writer rolls back (explicitly,
 * or inside close() because close's commit failed), it first deletes the failed commit's new
 * segment files and only afterwards, in IndexFileDeleter.refresh(), the unknown segments_N, with
 * no directory fsync in between. A crash that persists the directory operations in program order
 * up to that point leaves a newest segments_N that references deleted files, so the index does not
 * open although the last acknowledged commit is intact.
 *
 * <p>Crash states replayed: the ordered-metadata model (directory operations since the last
 * directory fsync persist as a prefix in program order). Every file in these states was fsynced
 * by the commits (the failed commit's startCommit synced all of its files), so only names matter:
 * each state is the directory listing just before one of the writer's deletes after the fault.
 *
 * <p>Oracle: DirectoryReader.open of every such state must show the acknowledged commit (doc "a")
 * or the complete failed commit (docs "a", "b").
 */
@LuceneTestCase.SuppressFileSystems("*")
public class TestCommitRenameFailureCrashState extends LuceneTestCase {

  enum Fault {
    NONE,
    /** rename(pending_segments_N, segments_N) throws without effect */
    RENAME_NO_EFFECT,
    /** rename(pending_segments_N, segments_N) takes effect, then throws */
    RENAME_TOOK_EFFECT,
    /** syncMetaData() after the segments_N rename throws (the LUCENE-9477 branch) */
    SYNC_AFTER_RENAME
  }

  /** Injects one commit fault; records the listing before every later delete (crash states). */
  static final class FaultyDirectory extends FilterDirectory {
    Fault fault = Fault.NONE;
    boolean armed, fired, renamedWhileArmed;
    /** crash states: copies of the durable files (bytes taken at the crash point). */
    final List<Directory> crashStates = new ArrayList<>();

    FaultyDirectory(Directory in) {
      super(in);
    }

    @Override
    public void rename(String source, String dest) throws IOException {
      if (armed && dest.startsWith(IndexFileNames.SEGMENTS)) {
        if (fault == Fault.RENAME_NO_EFFECT) {
          armed = false;
          fired = true;
          throw new IOException("injected: rename failed");
        }
        if (fault == Fault.RENAME_TOOK_EFFECT) {
          armed = false;
          fired = true;
          super.rename(source, dest);
          throw new IOException("injected: rename reported failure after taking effect");
        }
      }
      super.rename(source, dest);
      if (armed && dest.startsWith(IndexFileNames.SEGMENTS)) renamedWhileArmed = true;
    }

    @Override
    public void syncMetaData() throws IOException {
      if (armed && fault == Fault.SYNC_AFTER_RENAME && renamedWhileArmed) {
        armed = false;
        fired = true;
        throw new IOException("injected: directory fsync failed");
      }
      super.syncMetaData();
    }

    @Override
    public void deleteFile(String name) throws IOException {
      if (fired) {
        crashStates.add(snapshot());
      }
      super.deleteFile(name);
    }

    /** the current listing with its bytes: every directory operation so far persisted. */
    Directory snapshot() throws IOException {
      Directory copy = new ByteBuffersDirectory();
      for (String n : in.listAll()) {
        if (n.equals(IndexWriter.WRITE_LOCK_NAME) == false) {
          copy.copyFrom(in, n, n, IOContext.DEFAULT);
        }
      }
      return copy;
    }
  }

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  /** what DirectoryReader.open shows for a crash state (all its files fully synced). */
  private static String open(Directory crash) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(crash)) {
      TreeSet<String> ids = new TreeSet<>();
      StoredFields sf = r.storedFields();
      for (int i = 0; i < r.maxDoc(); i++) ids.add(sf.document(i).get("id"));
      return ids.toString();
    } catch (IOException e) {
      return "cannot open: " + e;
    }
  }

  /**
   * Commits doc "a" (acknowledged), adds doc "b", arms the fault and ends the session with close()
   * (useRollback = false; default commitOnClose) or with a failed commit() followed by rollback().
   * Returns the crash states that do not open to an allowed commit.
   */
  private List<String> run(Fault fault, boolean useRollback) throws IOException {
    List<String> bad = new ArrayList<>();
    try (FaultyDirectory dir = new FaultyDirectory(FSDirectory.open(createTempDir("live")))) {
      dir.fault = fault;
      IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
      IndexWriter w = new IndexWriter(dir, iwc);
      w.addDocument(doc("a"));
      w.commit(); // segments_1: acknowledged, references _0
      w.addDocument(doc("b"));
      dir.armed = fault != Fault.NONE;
      if (useRollback) {
        if (fault == Fault.NONE) {
          w.commit();
        } else {
          expectThrows(IOException.class, w::commit);
        }
        w.rollback();
      } else {
        if (fault == Fault.NONE) {
          w.close();
        } else {
          expectThrows(IOException.class, w::close); // close's commit fails; close rolls back
        }
      }
      assertEquals("fault fired", fault != Fault.NONE, dir.fired);
      // the final state (every directory operation persisted) is a crash state too
      dir.crashStates.add(dir.snapshot());
      for (Directory crash : dir.crashStates) {
        String files = List.of(crash.listAll()).toString();
        String got = open(crash);
        if (got.equals("[a]") == false && got.equals("[a, b]") == false) {
          bad.add("files=" + files + " -> " + got);
        }
        crash.close();
      }
    }
    return bad;
  }

  public void testControlNoFault() throws Exception {
    assertEquals(List.of(), run(Fault.NONE, false));
    assertEquals(List.of(), run(Fault.NONE, true));
  }

  /** Control: a rename that fails without effect leaves no broken crash state. */
  public void testControlRenameFailsWithoutEffect() throws Exception {
    assertEquals(List.of(), run(Fault.RENAME_NO_EFFECT, false));
    assertEquals(List.of(), run(Fault.RENAME_NO_EFFECT, true));
  }

  /**
   * Control: the directory fsync after the rename fails (LUCENE-9477): finishCommit deletes the
   * renamed segments_N before anything else, so every crash state opens.
   */
  public void testControlSyncMetaDataFailsAfterRename() throws Exception {
    assertEquals(List.of(), run(Fault.SYNC_AFTER_RENAME, false));
    assertEquals(List.of(), run(Fault.SYNC_AFTER_RENAME, true));
  }

  /** IndexWriter.close() whose commit's rename took effect and then threw. */
  public void testCloseAfterRenameTookEffectThenThrew() throws Exception {
    List<String> bad = run(Fault.RENAME_TOOK_EFFECT, false);
    assertEquals(
        "crash states after close() failed at the segments_N rename must open to the acknowledged"
            + " commit or the complete failed commit: "
            + bad,
        List.of(),
        bad);
  }

  /** commit() whose rename took effect and then threw, followed by rollback(). */
  public void testRollbackAfterRenameTookEffectThenThrew() throws Exception {
    List<String> bad = run(Fault.RENAME_TOOK_EFFECT, true);
    assertEquals(
        "crash states after commit() failed at the segments_N rename and rollback() must open to"
            + " the acknowledged commit or the complete failed commit: "
            + bad,
        List.of(),
        bad);
  }
}
