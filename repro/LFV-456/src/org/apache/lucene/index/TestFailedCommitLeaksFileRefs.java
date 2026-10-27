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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * IndexWriter#prepareCommit takes IndexFileDeleter references on every file of the commit it is
 * preparing, then runs more steps that can throw (the doAfterFlush hook, merge-on-commit setup:
 * MergePolicy#findFullFlushMerges and opening the merge readers, the merge scheduler, the event
 * listener) before the only code that releases those references on failure. A commit that fails in
 * one of these steps leaves the writer open, as it should, but the references are never released:
 * this writer never deletes those files, even after their segments are merged away and later
 * commits no longer reference them.
 */
public class TestFailedCommitLeaksFileRefs extends LuceneTestCase {

  /** Merges every not-yet-merging segment on commit (merge-on-commit), never otherwise. */
  private static class MergeOnCommitPolicy extends FilterMergePolicy {
    volatile boolean throwInFindFullFlushMerges;

    MergeOnCommitPolicy() {
      super(new LogDocMergePolicy());
    }

    @Override
    public MergeSpecification findMerges(
        MergeTrigger trigger, SegmentInfos infos, MergeContext context) {
      return null;
    }

    @Override
    public MergeSpecification findFullFlushMerges(
        MergeTrigger trigger, SegmentInfos infos, MergeContext context) throws IOException {
      if (throwInFindFullFlushMerges) {
        throw new IOException("simulated MergePolicy failure");
      }
      List<SegmentCommitInfo> segments = new ArrayList<>();
      for (SegmentCommitInfo info : infos) {
        if (context.getMergingSegments().contains(info) == false) {
          segments.add(info);
        }
      }
      if (segments.size() < 2) {
        return null;
      }
      MergeSpecification spec = new MergeSpecification();
      spec.add(new OneMerge(segments));
      return spec;
    }
  }

  private static IndexWriterConfig config(MergePolicy mp) {
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(mp);
    iwc.setMergeScheduler(new SerialMergeScheduler());
    iwc.setMaxFullFlushMergeWaitMillis(500); // the default
    iwc.setUseCompoundFile(false);
    return iwc;
  }

  private static MockDirectoryWrapper newDir() {
    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    dir.setRandomIOExceptionRate(0.0);
    dir.setRandomIOExceptionRateOnOpen(0.0);
    return dir;
  }

  private static void addSegments(IndexWriter w, int count) throws IOException {
    for (int i = 0; i < count; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", "doc" + i, Field.Store.YES));
      w.addDocument(doc);
      w.flush();
    }
  }

  /** Files in the directory that the latest commit does not reference (besides write.lock). */
  private static Set<String> filesNotInLatestCommit(MockDirectoryWrapper dir) throws IOException {
    Set<String> files = new TreeSet<>(Arrays.asList(dir.listAll()));
    files.removeAll(SegmentInfos.readLatestCommit(dir).files(true));
    files.remove(IndexWriter.WRITE_LOCK_NAME);
    return files;
  }

  private static void checkNoLeakedFiles(MockDirectoryWrapper dir, IndexWriter w)
      throws IOException {
    // after the failure the writer is still usable: commit, merge everything away, commit again
    w.commit();
    w.forceMerge(1);
    w.commit();
    int numDocs;
    int numSegments;
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      numDocs = r.numDocs();
      numSegments = r.leaves().size();
    }
    // no reader is open and the default deletion policy keeps only the last commit, so every
    // file of the merged-away segments must be gone
    Set<String> leakedWhileOpen = filesNotInLatestCommit(dir);
    w.close();
    Set<String> leakedAfterClose = filesNotInLatestCommit(dir);
    dir.close();
    assertEquals(3, numDocs);
    assertEquals(1, numSegments);
    assertEquals(
        "files the writer should have deleted after the merge and commit", Set.of(), leakedWhileOpen);
    assertEquals("files still left unreferenced after IndexWriter.close()", Set.of(), leakedAfterClose);
  }

  /** An IOException while opening a merge reader for a merge-on-commit merge. */
  public void testIOExceptionOpeningMergeOnCommitReader() throws Exception {
    MockDirectoryWrapper dir = newDir();
    final boolean[] armed = new boolean[1];
    dir.failOn(
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper d) throws IOException {
            if (armed[0] && callStackContains(MergePolicy.OneMerge.class, "initMergeReaders")) {
              armed[0] = false;
              throw new IOException("simulated I/O error opening a file");
            }
          }
        });
    IndexWriter w = new IndexWriter(dir, config(new MergeOnCommitPolicy()));
    addSegments(w, 3);
    armed[0] = true;
    expectThrows(IOException.class, w::commit);
    assertFalse("the failure was not injected", armed[0]);
    assertTrue(w.isOpen());
    assertNull(w.getTragicException());
    checkNoLeakedFiles(dir, w);
  }

  /** The merge policy throws from findFullFlushMerges. */
  public void testMergePolicyThrowsFromFindFullFlushMerges() throws Exception {
    MockDirectoryWrapper dir = newDir();
    MergeOnCommitPolicy mp = new MergeOnCommitPolicy();
    IndexWriter w = new IndexWriter(dir, config(mp));
    addSegments(w, 3);
    mp.throwInFindFullFlushMerges = true;
    expectThrows(IOException.class, w::commit);
    mp.throwInFindFullFlushMerges = false;
    assertTrue(w.isOpen());
    assertNull(w.getTragicException());
    checkNoLeakedFiles(dir, w);
  }

  /**
   * Default IndexWriterConfig (TieredMergePolicy, ConcurrentMergeScheduler, 500 ms merge-on-commit
   * wait): eight flushed one-document segments, then a commit that flushes a ninth makes the default
   * merge policy merge on commit. An IOException while that merge opens its readers fails the commit.
   */
  public void testDefaultConfigIOExceptionOpeningMergeOnCommitReader() throws Exception {
    MockDirectoryWrapper dir = newDir();
    final boolean[] armed = new boolean[1];
    dir.failOn(
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper d) throws IOException {
            if (armed[0] && callStackContains(MergePolicy.OneMerge.class, "initMergeReaders")) {
              armed[0] = false;
              throw new IOException("simulated I/O error opening a file");
            }
          }
        });
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    assertTrue(iwc.getMergePolicy() instanceof TieredMergePolicy);
    assertTrue(iwc.getMergeScheduler() instanceof ConcurrentMergeScheduler);
    assertEquals(500, iwc.getMaxFullFlushMergeWaitMillis());
    IndexWriter w = new IndexWriter(dir, iwc);
    for (int i = 0; i < 8; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", "doc" + i, Field.Store.YES));
      w.addDocument(doc);
      w.flush();
    }
    Document doc = new Document();
    doc.add(new StringField("id", "doc8", Field.Store.YES));
    w.addDocument(doc);
    armed[0] = true;
    expectThrows(IOException.class, w::commit);
    assertFalse("the failure was not injected", armed[0]);
    assertTrue(w.isOpen());
    assertNull(w.getTragicException());
    w.commit();
    w.forceMerge(1);
    w.commit();
    int numDocs;
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      numDocs = r.numDocs();
    }
    Set<String> leakedWhileOpen = filesNotInLatestCommit(dir);
    w.close();
    Set<String> leakedAfterClose = filesNotInLatestCommit(dir);
    dir.close();
    assertEquals(9, numDocs);
    assertEquals(
        "files the writer should have deleted after the merge and commit", Set.of(), leakedWhileOpen);
    assertEquals("files still left unreferenced after IndexWriter.close()", Set.of(), leakedAfterClose);
  }

  /** The protected IndexWriter#doAfterFlush hook throws; merge-on-commit is disabled. */
  public void testDoAfterFlushThrows() throws Exception {
    MockDirectoryWrapper dir = newDir();
    final boolean[] fail = new boolean[1];
    IndexWriterConfig iwc = config(new MergeOnCommitPolicy());
    iwc.setMaxFullFlushMergeWaitMillis(0); // no merge-on-commit
    IndexWriter w =
        new IndexWriter(dir, iwc) {
          @Override
          protected void doAfterFlush() throws IOException {
            if (fail[0]) {
              fail[0] = false;
              throw new IOException("simulated failure in doAfterFlush");
            }
          }
        };
    addSegments(w, 3);
    fail[0] = true;
    expectThrows(IOException.class, w::commit);
    assertFalse("the failure was not injected", fail[0]);
    assertTrue(w.isOpen());
    assertNull(w.getTragicException());
    checkNoLeakedFiles(dir, w);
  }
}
