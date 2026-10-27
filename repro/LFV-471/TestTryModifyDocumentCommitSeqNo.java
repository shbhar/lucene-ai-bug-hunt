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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndexWriter#commit: "All sequence numbers &lt;= this value will be reflected in the commit, and
 * all others will not." This must also hold for the expert tryDeleteDocument / tryUpdateDocValue
 * when they run while another thread commits.
 *
 * <p>Public API only. A FilterDirectory parks the committing thread at the first file it creates
 * (the flush of a buffered document, after commit has fixed its sequence number and before it
 * snapshots the segments) and runs the operation on a second thread meanwhile. No sleeps: the
 * committing thread waits until the operation returns, or at most 2 s if the writer makes the
 * operation wait for the commit.
 */
public class TestTryModifyDocumentCommitSeqNo extends LuceneTestCase {

  private interface Op {
    long apply(IndexWriter w, DirectoryReader nrt, int docId) throws IOException;
  }

  private static Document doc(String id) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.NO));
    doc.add(new NumericDocValuesField("v", 1));
    return doc;
  }

  private static IndexWriterConfig config() {
    return new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
  }

  /** The "v" value of the live document id:a, or 0 if it is deleted. */
  private static long valueOfA(IndexReader r) throws IOException {
    TopDocs hits = new IndexSearcher(r).search(new TermQuery(new Term("id", "a")), 1);
    if (hits.scoreDocs.length == 0) {
      return 0;
    }
    int doc = hits.scoreDocs[0].doc;
    LeafReaderContext leaf = r.leaves().get(ReaderUtil.subIndex(doc, r.leaves()));
    NumericDocValues dv = leaf.reader().getNumericDocValues("v");
    assertTrue(dv.advanceExact(doc - leaf.docBase));
    return dv.longValue();
  }

  private static int docIdOfA(IndexReader r) throws IOException {
    return new IndexSearcher(r).search(new TermQuery(new Term("id", "a")), 1).scoreDocs[0].doc;
  }

  /**
   * Runs op on doc a while commit() is inside its flush. Returns {op seqNo, commit seqNo, value of
   * a in the commit point, value of a in an NRT reader opened after the commit}.
   */
  private long[] runDuringCommit(Op op) throws Exception {
    Thread committer = Thread.currentThread();
    AtomicBoolean armed = new AtomicBoolean();
    AtomicLong opSeqNo = new AtomicLong();
    AtomicReference<Throwable> error = new AtomicReference<>();
    AtomicReference<Runnable> action = new AtomicReference<>();
    AtomicReference<Thread> opThread = new AtomicReference<>();
    Directory dir =
        new FilterDirectory(newDirectory()) {
          @Override
          public IndexOutput createOutput(String name, IOContext context) throws IOException {
            if (Thread.currentThread() == committer && armed.compareAndSet(true, false)) {
              Thread t = new Thread(action.get());
              opThread.set(t);
              t.start();
              try {
                t.join(2000);
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            }
            return super.createOutput(name, context);
          }
        };
    try (dir;
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(doc("a"));
      w.commit();
      try (DirectoryReader nrt = DirectoryReader.open(w)) {
        int docId = docIdOfA(nrt);
        action.set(
            () -> {
              try {
                opSeqNo.set(op.apply(w, nrt, docId));
              } catch (Throwable e) {
                error.set(e);
              }
            });
        w.addDocument(doc("b")); // buffered, so the commit flushes a segment
        armed.set(true);
        long commitSeqNo = w.commit();
        assertFalse("the hook did not run", armed.get());
        opThread.get().join();
        if (error.get() != null) {
          throw new AssertionError(error.get());
        }
        try (DirectoryReader committed = DirectoryReader.open(dir);
            DirectoryReader after = DirectoryReader.open(w)) {
          return new long[] {opSeqNo.get(), commitSeqNo, valueOfA(committed), valueOfA(after)};
        }
      }
    }
  }

  /** The op changes a's value to {@code changed}; check the result against the contract. */
  private static void assertCommitBoundary(String what, long[] r, long changed) {
    long opSeqNo = r[0];
    long commitSeqNo = r[1];
    boolean inCommit = r[2] == changed;
    String msg =
        what + " seqNo=" + opSeqNo + " commit seqNo=" + commitSeqNo + " inCommit=" + inCommit;
    if (VERBOSE) {
      System.out.println(msg);
    }
    if (opSeqNo == -1) { // documented "not applied" outcome of the try* methods
      assertFalse(msg + ": a -1 return must not change the index", inCommit || r[3] == changed);
    } else {
      assertTrue(msg, opSeqNo > 0);
      assertEquals("visible after reopen: " + msg, changed, r[3]);
      assertEquals(msg, opSeqNo <= commitSeqNo, inCommit);
    }
  }

  public void testTryDeleteDocumentDuringCommit() throws Exception {
    long[] r = runDuringCommit((w, nrt, doc) -> w.tryDeleteDocument(nrt, doc));
    assertCommitBoundary("tryDeleteDocument", r, 0);
  }

  public void testTryUpdateDocValueDuringCommit() throws Exception {
    long[] r =
        runDuringCommit(
            (w, nrt, doc) -> w.tryUpdateDocValue(nrt, doc, new NumericDocValuesField("v", 42)));
    assertCommitBoundary("tryUpdateDocValue", r, 42);
  }

  /** Control: the term-based delete in the same window stays out of the commit. */
  public void testDeleteByTermDuringCommit() throws Exception {
    long[] r = runDuringCommit((w, _, _) -> w.deleteDocuments(new Term("id", "a")));
    assertCommitBoundary("deleteDocuments(Term)", r, 0);
  }

  /** Control: the term-based doc-values update in the same window stays out of the commit. */
  public void testUpdateDocValueByTermDuringCommit() throws Exception {
    long[] r = runDuringCommit((w, _, _) -> w.updateNumericDocValue(new Term("id", "a"), "v", 42));
    assertCommitBoundary("updateNumericDocValue(Term)", r, 42);
  }

  /** Control: without a concurrent commit the try* calls succeed and respect the boundary. */
  public void testTryDeleteDocumentBeforeAndAfterCommit() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(doc("a"));
      w.addDocument(doc("x"));
      try (DirectoryReader nrt = DirectoryReader.open(w)) {
        long before = w.tryDeleteDocument(nrt, docIdOfA(nrt));
        long commitSeqNo = w.commit();
        int x =
            new IndexSearcher(nrt).search(new TermQuery(new Term("id", "x")), 1).scoreDocs[0].doc;
        long after = w.tryDeleteDocument(nrt, x);
        assertTrue(before > 0 && before <= commitSeqNo);
        assertTrue(after > commitSeqNo);
        try (DirectoryReader committed = DirectoryReader.open(dir)) {
          assertEquals(0, valueOfA(committed));
          assertEquals(1, new IndexSearcher(committed).count(new TermQuery(new Term("id", "x"))));
        }
      }
    }
  }
}
