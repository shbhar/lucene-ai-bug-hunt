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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * IndexWriter#deleteAll removes all segments from the writer's SegmentInfos first and then closes
 * the pooled segment readers. If closing a pooled reader throws, deleteAll throws but skips the rest
 * of its work, including counting the change: the writer stays open, new NRT readers already see an
 * empty index, yet hasUncommittedChanges() is false and commit()/close() write nothing, so the index
 * on disk keeps every document. Whether the "failed" deleteAll reaches disk then depends on whether
 * some later, unrelated change happens to be committed.
 */
public class TestFailedDeleteAllHalfApplied extends LuceneTestCase {

  private static IndexWriterConfig config() {
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    return iwc;
  }

  private static void addDoc(IndexWriter w, String id) throws IOException {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    w.addDocument(doc);
  }

  private static int docsOnDisk(MockDirectoryWrapper dir) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      return r.numDocs();
    }
  }

  private static int docsInNewNrtReader(IndexWriter w) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(w)) {
      return r.numDocs();
    }
  }

  /**
   * Shared check: the writer's view and what commit() persists must agree after a failed deleteAll.
   */
  private static void checkConsistent(MockDirectoryWrapper dir, IndexWriter w) throws IOException {
    assertTrue(w.isOpen());
    assertNull(w.getTragicException());
    int nrtDocs = docsInNewNrtReader(w);
    boolean uncommitted = w.hasUncommittedChanges();
    w.commit();
    int committedDocs = docsOnDisk(dir);
    w.close();
    int docsAfterClose = docsOnDisk(dir);
    dir.close();
    // either deleteAll took effect (NRT empty, and then it must be counted as a change and be
    // committed) or it did not (NRT still shows both documents)
    assertEquals(
        "new NRT reader shows "
            + nrtDocs
            + " docs, hasUncommittedChanges()="
            + uncommitted
            + ", but commit() persisted "
            + committedDocs
            + " docs",
        nrtDocs,
        committedDocs);
    assertEquals(nrtDocs, docsAfterClose);
  }

  /** An IOException while closing a file of a pooled segment reader. */
  public void testIOExceptionClosingPooledReader() throws Exception {
    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    dir.setRandomIOExceptionRate(0.0);
    dir.setRandomIOExceptionRateOnOpen(0.0);
    final boolean[] armed = new boolean[1];
    dir.failOn(
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper d) throws IOException {
            if (armed[0]
                && callStackContains(IndexWriter.class, "deleteAll")
                && callStackContains(ReaderPool.class, "dropAll")) {
              armed[0] = false;
              throw new IOException("simulated I/O error closing a file");
            }
          }
        });
    IndexWriter w = new IndexWriter(dir, config());
    addDoc(w, "a");
    addDoc(w, "b");
    w.commit();
    DirectoryReader.open(w).close(); // pools the segment readers in the writer
    assertFalse(w.hasUncommittedChanges());

    armed[0] = true;
    expectThrows(IOException.class, w::deleteAll);
    assertFalse("the failure was not injected", armed[0]);
    checkConsistent(dir, w);
  }

  /** A segment-core ClosedListener (allowed to throw IOException) throws. */
  public void testCoreClosedListenerThrows() throws Exception {
    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    dir.setRandomIOExceptionRate(0.0);
    dir.setRandomIOExceptionRateOnOpen(0.0);
    IndexWriter w = new IndexWriter(dir, config());
    addDoc(w, "a");
    addDoc(w, "b");
    w.commit();
    final boolean[] armed = new boolean[1];
    try (DirectoryReader r = DirectoryReader.open(w)) {
      for (LeafReaderContext ctx : r.leaves()) {
        ctx.reader()
            .getCoreCacheHelper()
            .addClosedListener(
                key -> {
                  if (armed[0]) {
                    armed[0] = false;
                    throw new IOException("simulated cache eviction failure");
                  }
                });
      }
    }
    assertFalse(w.hasUncommittedChanges());

    armed[0] = true;
    expectThrows(IOException.class, w::deleteAll);
    assertFalse("the failure was not injected", armed[0]);
    checkConsistent(dir, w);
  }

  /**
   * Windows-like file-system semantics (MockDirectoryWrapper#setAssertNoDeleteOpenFile): deleteAll
   * deletes the files of an uncommitted segment before it drops the pooled reader that still has
   * them open, so the delete throws, after the segments were already removed.
   */
  public void testDeleteOfOpenFileFails() throws Exception {
    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    dir.setRandomIOExceptionRate(0.0);
    dir.setRandomIOExceptionRateOnOpen(0.0);
    dir.setAssertNoDeleteOpenFile(true);
    IndexWriter w = new IndexWriter(dir, config());
    addDoc(w, "a");
    w.commit();
    addDoc(w, "b");
    w.flush(); // an uncommitted segment
    DirectoryReader.open(w).close(); // pools the segment readers in the writer

    Throwable deleteAllFailure = null;
    try {
      w.deleteAll();
    } catch (IOException e) {
      deleteAllFailure = e;
    }
    assertNotNull("expected the delete of an open file to fail", deleteAllFailure);
    assertTrue(w.isOpen());
    assertNull(w.getTragicException());
    int nrtDocs = docsInNewNrtReader(w);
    Throwable commitFailure = null;
    try {
      w.commit();
    } catch (IOException e) {
      commitFailure = e;
    }
    int committedDocs = docsOnDisk(dir);
    w.rollback();
    dir.close();
    if (commitFailure != null) {
      throw new AssertionError(
          "commit() after the failed deleteAll fails as well (new NRT reader shows "
              + nrtDocs
              + " docs)",
          commitFailure);
    }
    assertEquals(nrtDocs, committedDocs);
  }
}
