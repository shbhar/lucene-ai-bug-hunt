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
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Controls for the commit sequence-number boundary. The operation under test runs on a second
 * thread while the committing thread is inside flushAllThreads (first createOutput of the flushed
 * segment). Contract (IndexWriter#commit @return): "All sequence numbers &lt;= this value will be
 * reflected in the commit, and all others will not."
 *
 * <p>Term-based operations in the same window are expected to honour the contract (controls); the
 * try* operations are the candidate.
 */
public class TestSeqNoCommitBoundaryControlV3 extends LuceneTestCase {

  interface Op {
    long run(IndexWriter w, DirectoryReader nrt) throws IOException;
  }

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new NumericDocValuesField("v", 1));
    return d;
  }

  /** Returns {opSeqNo, commitSeqNo, 1 if the change is visible in the commit else 0}. */
  private long[] runInCommitWindow(Op op, boolean checkDv) throws Exception {
    Thread mainThread = Thread.currentThread();
    AtomicBoolean arm = new AtomicBoolean(false);
    AtomicLong opSeqNo = new AtomicLong(-2);
    AtomicReference<Throwable> err = new AtomicReference<>();
    AtomicReference<IndexWriter> writerRef = new AtomicReference<>();
    AtomicReference<DirectoryReader> nrtRef = new AtomicReference<>();
    Directory dir =
        new FilterDirectory(newDirectory()) {
          @Override
          public IndexOutput createOutput(String name, IOContext context) throws IOException {
            if (Thread.currentThread() == mainThread && arm.compareAndSet(true, false)) {
              Thread t =
                  new Thread(
                      () -> {
                        try {
                          opSeqNo.set(op.run(writerRef.get(), nrtRef.get()));
                        } catch (Throwable e) {
                          err.set(e);
                        }
                      });
              t.start();
              try {
                t.join(30_000);
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
              assertFalse("op thread still running (blocked?)", t.isAlive());
            }
            return super.createOutput(name, context);
          }
        };
    IndexWriter w =
        new IndexWriter(
            dir,
            new IndexWriterConfig(new MockAnalyzer(random()))
                .setMergePolicy(NoMergePolicy.INSTANCE));
    writerRef.set(w);
    w.addDocument(doc("a"));
    w.commit();
    DirectoryReader nrt = DirectoryReader.open(w);
    nrtRef.set(nrt);
    w.addDocument(doc("b")); // buffered: commit's flushAllThreads flushes a DWPT (hook point)
    arm.set(true);
    long commitSeqNo = w.commit();
    assertFalse("hook did not run", arm.get());
    if (err.get() != null) {
      throw new AssertionError(err.get());
    }
    long changed;
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      if (checkDv) {
        // value of doc "a" in the commit point
        long val = -1;
        for (LeafReaderContext ctx : r.leaves()) {
          IndexSearcher s = new IndexSearcher(ctx.reader());
          var td = s.search(new TermQuery(new Term("id", "a")), 1);
          if (td.scoreDocs.length == 1) {
            NumericDocValues dv = ctx.reader().getNumericDocValues("v");
            assertTrue(dv.advanceExact(td.scoreDocs[0].doc));
            val = dv.longValue();
          }
        }
        changed = val == 42 ? 1 : 0;
      } else {
        changed = new IndexSearcher(r).count(new TermQuery(new Term("id", "a"))) == 0 ? 1 : 0;
      }
    }
    nrt.close();
    w.close();
    dir.close();
    return new long[] {opSeqNo.get(), commitSeqNo, changed};
  }

  private static void assertBoundary(String what, long[] res) {
    long op = res[0], commit = res[1];
    boolean inCommit = res[2] == 1;
    System.out.println(
        "[v3] " + what + ": opSeqNo=" + op + " commitSeqNo=" + commit + " inCommit=" + inCommit);
    assertTrue(what + " op must succeed: " + op, op > 0);
    assertEquals(
        what + " opSeqNo=" + op + " commitSeqNo=" + commit + " inCommit=" + inCommit,
        op <= commit,
        inCommit);
  }

  // ---- controls (expected to pass): ordinary queue-based operations in the same window ----

  public void testControlDeleteByTermSameWindow() throws Exception {
    assertBoundary(
        "deleteDocuments(Term)",
        runInCommitWindow((w, r) -> w.deleteDocuments(new Term("id", "a")), false));
  }

  public void testControlDeleteByQuerySameWindow() throws Exception {
    assertBoundary(
        "deleteDocuments(Query)",
        runInCommitWindow((w, r) -> w.deleteDocuments(new TermQuery(new Term("id", "a"))), false));
  }

  public void testControlUpdateNumericDocValueSameWindow() throws Exception {
    assertBoundary(
        "updateNumericDocValue(Term)",
        runInCommitWindow((w, r) -> w.updateNumericDocValue(new Term("id", "a"), "v", 42), true));
  }

  // ---- candidate (expected to fail on stock jars): try* in the same window ----

  public void testCandidateTryDeleteSameWindow() throws Exception {
    assertBoundary("tryDeleteDocument", runInCommitWindow((w, r) -> w.tryDeleteDocument(r, 0), false));
  }

  public void testCandidateTryUpdateDocValueSameWindow() throws Exception {
    assertBoundary(
        "tryUpdateDocValue",
        runInCommitWindow(
            (w, r) -> w.tryUpdateDocValue(r, 0, new NumericDocValuesField("v", 42)), true));
  }
}
