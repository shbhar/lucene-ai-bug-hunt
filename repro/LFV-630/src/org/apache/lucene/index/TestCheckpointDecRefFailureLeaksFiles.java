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
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A single failed Directory.deleteFile inside IndexFileDeleter.checkpoint makes the writer lose
 * track of the reference it just took on every file of the current segments: IndexFileDeleter
 * increments those files, then the decRef of the previous checkpoint's files throws, the finally
 * block clears lastFiles, and the line that records the current files as lastFiles is skipped. The
 * extra reference is never released, so once those segments are merged away their files are never
 * deleted by this writer: not by later checkpoints and commits, not by deleteUnusedFiles(), not by
 * rollback() or close(). Only the next IndexWriter on the directory removes them.
 *
 * <p>Public API only: a FilterDirectory whose deleteFile throws once (Directory#deleteFile declares
 * "@throws IOException in case of I/O error"). The writer stays open and reports no tragic event.
 */
public class TestCheckpointDecRefFailureLeaksFiles extends LuceneTestCase {

  /** Throws from the first deleteFile of a non-temporary file after it is armed. */
  static final class DeleteFailsOnceDirectory extends FilterDirectory {
    volatile boolean armed;
    volatile String failedOn;

    DeleteFailsOnceDirectory(Directory in) {
      super(in);
    }

    @Override
    public void deleteFile(String name) throws IOException {
      // temporary files (.tmp) are deleted inside the segment flush, where a failure aborts the
      // flush by design; we target the IndexFileDeleter's deletion of an unreferenced segment file
      if (armed && name.endsWith(".tmp") == false) {
        armed = false;
        failedOn = name;
        throw new IOException("simulated I/O error deleting " + name);
      }
      super.deleteFile(name);
    }
  }

  static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  static Set<String> filesNotInLastCommit(Directory dir) throws IOException {
    Set<String> live = new HashSet<>(SegmentInfos.readLatestCommit(dir).files(true));
    Set<String> extra = new TreeSet<>();
    for (String f : dir.listAll()) {
      if (live.contains(f) == false && f.equals("write.lock") == false) {
        extra.add(f);
      }
    }
    return extra;
  }

  public void testFailedDeleteInCheckpointLeaksCurrentSegmentFiles() throws Exception {
    DeleteFailsOnceDirectory dir = new DeleteFailsOnceDirectory(new ByteBuffersDirectory());
    IndexWriterConfig iwc =
        new IndexWriterConfig(new StandardAnalyzer())
            .setMergePolicy(new LogDocMergePolicy())
            .setMergeScheduler(new SerialMergeScheduler())
            .setMaxBufferedDocs(100)
            .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            .setCommitOnClose(false);
    IndexWriter w = new IndexWriter(dir, iwc);

    w.addDocument(doc("a"));
    w.commit(); // segment _0, referenced by commit segments_1 and by the writer's checkpoint

    // The next flushed segment is 100% deleted, so IndexWriter drops it right after publishing it
    // and checkpoints; that checkpoint deletes the dropped segment's files. Make that delete fail.
    w.addDocument(doc("b"));
    w.deleteDocuments(new Term("id", "b"));
    dir.armed = true;
    IOException expected = expectThrows(IOException.class, w::flush);
    assertTrue(expected.getMessage(), expected.getMessage().startsWith("simulated I/O error"));
    assertTrue("the failure is not tragic: the writer stays open", w.isOpen());
    assertNull(w.getTragicException());

    // Keep using the writer: merge _0 away and commit, so _0 is referenced by nothing any more.
    w.addDocument(doc("c"));
    w.forceMerge(1);
    w.commit();
    w.deleteUnusedFiles();
    Set<String> duringSession = filesNotInLastCommit(dir);

    w.close(); // commitOnClose=false: rollback to the last commit, which removes unreferenced files
    Set<String> afterClose = filesNotInLastCommit(dir);

    // Only a new writer cleans them up:
    new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()).setCommitOnClose(false))
        .close();
    Set<String> afterReopen = filesNotInLastCommit(dir);
    dir.close();

    assertEquals("a fresh IndexWriter deletes the leftovers (control)", Set.of(), afterReopen);
    assertEquals(
        "files of merged-away segment _0 are left on disk after the injected delete failure on "
            + dir.failedOn
            + " (after commit + deleteUnusedFiles: "
            + duringSession
            + ")",
        Set.of(),
        afterClose);
  }
}
