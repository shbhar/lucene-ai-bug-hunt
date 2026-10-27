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

import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.util.LuceneTestCase;

/**
 * NoMergeScheduler's javadoc: "Use it if you want to prevent an IndexWriter from ever executing
 * merges, regardless of the MergePolicy used." With the default merge policy, once the policy
 * selects a merge, the merge stays in IndexWriter's pendingMerges forever (NoMergeScheduler neither
 * runs nor removes it), and close() (waitForMerges) and forceMerge(1) wait for pendingMerges to
 * become empty, so they never return.
 */
public class TestNoMergeSchedulerWaits extends LuceneTestCase {

  private static final long BOUND_MS = 10_000;

  private interface WriterCall {
    void run(IndexWriter w) throws Exception;
  }

  /** Runs the call in a thread; returns true if it finished within BOUND_MS. */
  private static boolean finishes(IndexWriter w, WriterCall call, String name) throws Exception {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread t =
        new Thread(
            () -> {
              try {
                call.run(w);
              } catch (Throwable e) {
                failure.set(e);
              }
            },
            name);
    t.start();
    t.join(BOUND_MS);
    boolean finished = t.isAlive() == false;
    if (finished == false) {
      // unblock the waiting thread (doWait turns the interrupt into ThreadInterruptedException)
      t.interrupt();
      t.join(BOUND_MS);
    }
    System.out.println(
        name + ": finished within " + BOUND_MS + " ms = " + finished + ", exception = " + failure.get());
    if (failure.get() != null) {
      StackTraceElement[] st = failure.get().getStackTrace();
      for (int i = 0; i < Math.min(6, st.length); i++) {
        System.out.println("    at " + st[i]);
      }
    }
    return finished;
  }

  private IndexWriter newWriter(Directory dir, MergePolicy mp) throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig(new MockAnalyzer(random()))
            .setMergeScheduler(NoMergeScheduler.INSTANCE)
            .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            .setRAMBufferSizeMB(64);
    if (mp != null) {
      iwc.setMergePolicy(mp); // else: the default merge policy
    }
    IndexWriter w = new IndexWriter(dir, iwc);
    for (int i = 0; i < 15; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.YES));
      w.addDocument(doc);
      w.commit(); // one small segment per commit; the commit's maybeMerge asks the policy
    }
    return w;
  }

  /** close() with NoMergeScheduler and the default merge policy. */
  public void testCloseWithDefaultMergePolicy() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = newWriter(dir, null);
      System.out.println(
          "merge policy " + w.getConfig().getMergePolicy().getClass().getSimpleName()
              + ", hasPendingMerges=" + w.hasPendingMerges());
      boolean closed = finishes(w, IndexWriter::close, "close");
      if (w.isOpen()) {
        w.rollback();
      }
      assertTrue("IndexWriter.close() did not return with NoMergeScheduler", closed);
    }
  }

  /** forceMerge(1) with NoMergeScheduler and the default merge policy. */
  public void testForceMergeWithDefaultMergePolicy() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = newWriter(dir, null);
      boolean done = finishes(w, x -> x.forceMerge(1), "forceMerge(1)");
      w.rollback();
      assertTrue("IndexWriter.forceMerge(1) did not return with NoMergeScheduler", done);
    }
  }

  /** forceMergeDeletes() (blocking) with NoMergeScheduler; no natural merges (merge factor 100). */
  public void testForceMergeDeletesWithLogDocMergePolicy() throws Exception {
    try (Directory dir = newDirectory()) {
      LogDocMergePolicy mp = new LogDocMergePolicy();
      mp.setMergeFactor(100);
      IndexWriter w =
          new IndexWriter(
              dir,
              new IndexWriterConfig(new MockAnalyzer(random()))
                  .setMergeScheduler(NoMergeScheduler.INSTANCE)
                  .setMergePolicy(mp));
      for (int i = 0; i < 4; i++) { // 2 segments of 2 documents
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(i), Field.Store.YES));
        w.addDocument(doc);
        if (i % 2 == 1) {
          w.commit();
        }
      }
      w.deleteDocuments(new Term("id", "0")); // 50% deleted in each segment
      w.deleteDocuments(new Term("id", "2"));
      w.commit();
      assertEquals(2, w.getSegmentCount());
      assertFalse(w.hasPendingMerges());
      boolean done = finishes(w, IndexWriter::forceMergeDeletes, "forceMergeDeletes()");
      w.rollback();
      assertTrue("IndexWriter.forceMergeDeletes() did not return with NoMergeScheduler", done);
    }
  }

  /** Control: NoMergeScheduler together with NoMergePolicy closes normally. */
  public void testControlCloseWithNoMergePolicy() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = newWriter(dir, NoMergePolicy.INSTANCE);
      assertFalse(w.hasPendingMerges());
      boolean closed = finishes(w, IndexWriter::close, "close (NoMergePolicy)");
      if (w.isOpen()) {
        w.rollback();
      }
      assertTrue(closed);
    }
  }
}
