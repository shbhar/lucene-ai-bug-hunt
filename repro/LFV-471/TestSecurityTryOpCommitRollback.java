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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * Security-assessment probe: what does the try* commit-boundary bug change for an application that
 * uses commit sequence numbers to decide which changes IndexWriter#rollback() discards?
 *
 * <p>Contract (IndexWriter#commit @return): "All sequence numbers &lt;= this value will be
 * reflected in the commit, and all others will not." IndexWriter#rollback discards "any changes
 * that have occurred since the last commit". So an application may expect that rollback() undoes
 * every change whose seqNo is above the last commit's seqNo.
 *
 * <p>Each test runs one try* change at a chosen point relative to commit(), then calls rollback(),
 * then opens the index from the directory and checks the change against the contract. It also runs
 * CheckIndex on the index left by rollback (the commit point). Public API only. The "during" tests
 * pause the committing thread with a FilterDirectory when it creates the .si file of the segment
 * that commit flushes, and run the try* call from a second thread meanwhile.
 *
 * <p>Expected: the before/after controls pass; the "during" tests fail if the bug is present.
 */
public class TestSecurityTryOpCommitRollback extends LuceneTestCase {

  private static final String SOFT = "soft";

  enum When {
    BEFORE,
    DURING,
    AFTER
  }

  interface IOAction {
    void run() throws Exception;
  }

  /** Pauses one chosen thread on its first .si creation and runs an action on a helper thread. */
  static final class PauseOnSegmentInfo extends FilterDirectory {
    volatile Thread threadToPause;
    volatile IOAction action;
    final AtomicBoolean fired = new AtomicBoolean();
    final AtomicReference<Throwable> actionError = new AtomicReference<>();

    PauseOnSegmentInfo(Directory in) {
      super(in);
    }

    @Override
    public IndexOutput createOutput(String name, IOContext context) throws IOException {
      if (Thread.currentThread() == threadToPause
          && action != null
          && name.endsWith(".si")
          && fired.compareAndSet(false, true)) {
        final IOAction a = action;
        Thread t =
            new Thread(
                () -> {
                  try {
                    a.run();
                  } catch (Throwable e) {
                    actionError.set(e);
                  }
                },
                "security-probe-try-op");
        t.start();
        try {
          t.join(20_000);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        if (t.isAlive()) {
          actionError.compareAndSet(
              null, new AssertionError("try* call blocked while the commit was paused"));
        }
      }
      return super.createOutput(name, context);
    }
  }

  private static IndexWriterConfig config() {
    return new IndexWriterConfig(new MockAnalyzer(random()))
        .setSoftDeletesField(SOFT)
        .setRAMBufferSizeMB(256)
        .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
        .setMergePolicy(NoMergePolicy.INSTANCE)
        .setCommitOnClose(false);
  }

  private static Document doc(String id, boolean softDeleted) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.NO));
    if (softDeleted) {
      d.add(new NumericDocValuesField(SOFT, 1));
    }
    return d;
  }

  /** Global docID of the document with this id, ignoring live docs (it may be soft-deleted). */
  private static int rawDocId(IndexReader r, String id) throws IOException {
    int found = -1;
    for (LeafReaderContext ctx : r.leaves()) {
      Terms terms = ctx.reader().terms("id");
      if (terms == null) {
        continue;
      }
      TermsEnum te = terms.iterator();
      if (te.seekExact(new BytesRef(id))) {
        PostingsEnum pe = te.postings(null, PostingsEnum.NONE);
        for (int d = pe.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = pe.nextDoc()) {
          assertEquals("more than one doc with id=" + id, -1, found);
          found = ctx.docBase + d;
        }
      }
    }
    assertTrue("no doc with id=" + id, found >= 0);
    return found;
  }

  /** Search visibility in the on-disk commit, with soft deletes applied as an application would. */
  private static boolean visibleOnDisk(Directory dir, String id) throws IOException {
    try (DirectoryReader r =
        new SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), SOFT)) {
      return new IndexSearcher(r).count(new TermQuery(new Term("id", id))) == 1;
    }
  }

  private static boolean visible(IndexReader r, String id) throws IOException {
    return new IndexSearcher(r).count(new TermQuery(new Term("id", id))) == 1;
  }

  interface TryOp {
    long apply(IndexWriter w, DirectoryReader nrt, int docId) throws IOException;
  }

  /**
   * Runs one scenario. Returns a line describing it and asserts the rollback expectation that the
   * commit seqNo contract implies: the change survives rollback iff its seqNo <= commit seqNo.
   */
  private void scenario(When when, boolean targetSoftDeleted, TryOp op, String opName)
      throws Exception {
    PauseOnSegmentInfo dir = new PauseOnSegmentInfo(newDirectory());
    final IndexWriter writer = new IndexWriter(dir, config());
    IndexWriter w = writer; // set to null once rolled back
    DirectoryReader nrt = null;
    try {
      w.addDocument(doc("a", targetSoftDeleted));
      w.addDocument(doc("b", false));
      w.commit(); // a baseline commit, so rollback() always has a commit to return to
      nrt = DirectoryReader.open(w);
      assertEquals(
          "precondition: a is visible to the NRT reader iff it is not soft-deleted",
          !targetSoftDeleted,
          visible(nrt, "a"));
      final int target = rawDocId(nrt, "a");
      final DirectoryReader reader = nrt;
      w.addDocument(doc("c", false)); // buffered, so commit() flushes a segment and writes a .si

      AtomicLong trySeq = new AtomicLong(Long.MIN_VALUE);
      long commitSeq;
      switch (when) {
        case BEFORE:
          trySeq.set(op.apply(w, reader, target));
          commitSeq = w.commit();
          break;
        case DURING:
          dir.action = () -> trySeq.set(op.apply(writer, reader, target));
          dir.threadToPause = Thread.currentThread();
          commitSeq = w.commit();
          dir.threadToPause = null;
          assertTrue("hook did not fire", dir.fired.get());
          assertNull("try* call failed: " + dir.actionError.get(), dir.actionError.get());
          break;
        case AFTER:
          commitSeq = w.commit();
          trySeq.set(op.apply(w, reader, target));
          break;
        default:
          throw new AssertionError();
      }
      assertTrue(opName + " returned " + trySeq.get(), trySeq.get() > 0);
      assertTrue("commit returned " + commitSeq, commitSeq > 0);

      // The application now aborts: rollback() discards changes since the last commit. By the
      // contract it expects exactly the changes with seqNo > commitSeq to be undone.
      w.rollback();
      w = null;

      CheckIndex.Status status = TestUtil.checkIndex(dir);
      boolean aVisible = visibleOnDisk(dir, "a");
      boolean bVisible = visibleOnDisk(dir, "b");
      boolean cVisible = visibleOnDisk(dir, "c");
      // the try* op flips a's visibility: revive makes it visible, delete makes it invisible
      boolean changeSurvived = targetSoftDeleted ? aVisible : !aVisible;
      boolean contractSaysSurvives = trySeq.get() <= commitSeq;
      String msg =
          when
              + " "
              + opName
              + " seqNo="
              + trySeq.get()
              + " commitSeqNo="
              + commitSeq
              + " afterRollback: aVisible="
              + aVisible
              + " changeSurvivedRollback="
              + changeSurvived
              + " contractSaysSurvives="
              + contractSaysSurvives
              + " checkIndexClean="
              + status.clean;
      System.out.println("SECURITY-PROBE " + getTestName() + ": " + msg);
      assertTrue("CheckIndex not clean: " + msg, status.clean);
      assertTrue("b must be visible: " + msg, bVisible);
      assertTrue("c (added before the commit) must be visible: " + msg, cVisible);
      assertEquals(msg, contractSaysSurvives, changeSurvived);
    } finally {
      if (nrt != null) {
        nrt.close();
      }
      if (w != null) {
        w.rollback();
      }
      dir.close();
    }
  }

  private static final TryOp REVIVE =
      (w, nrt, docId) -> w.tryUpdateDocValue(nrt, docId, new NumericDocValuesField(SOFT, null));

  private static final TryOp DELETE = (w, nrt, docId) -> w.tryDeleteDocument(nrt, docId);

  // tryUpdateDocValue(soft field := null) on a soft-deleted doc: documented un-delete
  public void testReviveBeforeCommitControl() throws Exception {
    scenario(When.BEFORE, true, REVIVE, "tryUpdateDocValue(soft=null)");
  }

  public void testReviveAfterCommitControl() throws Exception {
    scenario(When.AFTER, true, REVIVE, "tryUpdateDocValue(soft=null)");
  }

  public void testReviveDuringCommit() throws Exception {
    scenario(When.DURING, true, REVIVE, "tryUpdateDocValue(soft=null)");
  }

  // tryDeleteDocument on a live doc
  public void testDeleteBeforeCommitControl() throws Exception {
    scenario(When.BEFORE, false, DELETE, "tryDeleteDocument");
  }

  public void testDeleteAfterCommitControl() throws Exception {
    scenario(When.AFTER, false, DELETE, "tryDeleteDocument");
  }

  public void testDeleteDuringCommit() throws Exception {
    scenario(When.DURING, false, DELETE, "tryDeleteDocument");
  }
}
