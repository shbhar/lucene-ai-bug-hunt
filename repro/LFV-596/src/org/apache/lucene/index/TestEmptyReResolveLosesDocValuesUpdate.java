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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

/**
 * A doc-values update is lost when the packet that carries it re-resolves after a merge, finds no
 * segment left to resolve against, and a second merge commits before the packet is marked
 * finished.
 *
 * <p>Three segments: S1 = {id 1, key a}, S2 = {id 2, key a}, S3 = {id 3, key b, dv 30}. Two
 * background merges are registered together: A = S1 + S2 and B = S3 (a singleton merge, as a
 * merge policy may propose to reclaim deletes); both hold in OneMerge.wrapForMerge, after their
 * merge readers were pulled.
 *
 * <ol>
 *   <li>deleteDocuments(key:a) + flush(): ids 1 and 2 are deleted while merge A runs.
 *   <li>Thread T: updateNumericDocValue(key:b, dv, 7) + flush(). Its packet resolves against S1,
 *       S2, S3 (pending update for id 3 on S3). Right after that, merge A commits: its result is
 *       100% deleted and dropped, and mergeFinishedGen moves, so the packet iterates again.
 *   <li>The second iteration finds no unseen segment (S3 was seen, A's result was dropped):
 *       IndexWriter#forceApply breaks out with "packet matches no segments" and marks the packet
 *       finished afterwards, outside the IndexWriter lock and without re-checking
 *       mergeFinishedGen. Merge B commits in that window; its carry-over skips the pending update
 *       of id 3 because the packet is still running ("they re-resolve against the merged segment
 *       once they complete"), but the packet then finishes without ever resolving against B's
 *       result.
 * </ol>
 *
 * The window is pinned deterministically: when T logs "packet matches no segments" (T holds the
 * IndexWriter monitor and has already decided to break; it reads no shared state before marking
 * the packet finished), the callback releases merge B and waits on the writer's monitor until B
 * has committed. That is the same interleaving as T being descheduled right after leaving the
 * monitor and before BufferedUpdatesStream#finished.
 */
public class TestEmptyReResolveLosesDocValuesUpdate extends LuceneTestCase {

  static Document doc(String id, String key, long dv) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new StringField("key", key, Field.Store.NO));
    d.add(new NumericDocValuesField("dv", dv));
    return d;
  }

  /** A merge that holds after its readers were pulled and reports when it is done. */
  static final class HeldMerge extends MergePolicy.OneMerge {
    final CountDownLatch reached = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    final CountDownLatch done = new CountDownLatch(1);
    volatile boolean dropped;

    HeldMerge(List<SegmentCommitInfo> segments) {
      super(segments);
    }

    @Override
    public CodecReader wrapForMerge(CodecReader reader) throws IOException {
      if (reached.getCount() > 0) {
        reached.countDown();
        try {
          assertTrue("merge not released", release.await(30, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      return reader;
    }

    @Override
    public void mergeFinished(boolean success, boolean segmentDropped) throws IOException {
      dropped = segmentDropped;
      done.countDown();
    }
  }

  static final boolean DEBUG = Boolean.getBoolean("h170.debug");
  volatile HeldMerge mergeA, mergeB;
  volatile boolean mergeNow;

  // hooks in the thread that resolves the doc-values update packet
  volatile Thread updater;
  final CountDownLatch resolvedOnce = new CountDownLatch(1);
  final CountDownLatch emptyIteration = new CountDownLatch(1);
  volatile IndexWriter writer;

  public void testDocValuesUpdateLostWhenEmptyIterationRacesMergeCommit() throws Exception {
    run(false);
  }

  /**
   * Same window with softUpdateDocument: the soft delete of the old version of key b is lost, so
   * the old and the new version are both live.
   */
  public void testSoftUpdateLosesSoftDeleteWhenEmptyIterationRacesMergeCommit() throws Exception {
    run(true);
  }

  void run(boolean soft) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    if (soft) iwc.setSoftDeletesField("soft");
    iwc.setCommitOnClose(false);
    iwc.setMaxBufferedDocs(100);
    iwc.setRAMBufferSizeMB(64);
    ConcurrentMergeScheduler cms = new ConcurrentMergeScheduler();
    cms.setMaxMergesAndThreads(4, 4);
    iwc.setMergeScheduler(cms);
    iwc.setMergePolicy(
        new FilterMergePolicy(NoMergePolicy.INSTANCE) {
          @Override
          public MergeSpecification findMerges(
              MergeTrigger trigger, SegmentInfos infos, MergeContext ctx) {
            if (mergeNow == false || infos.size() != 3) return null; // S1, S2, S3
            mergeNow = false;
            // the writer's live segments, in flush order: S1, S2, S3
            MergeSpecification spec = new MergeSpecification();
            mergeA = new HeldMerge(List.of(infos.info(0), infos.info(1)));
            mergeB = new HeldMerge(List.of(infos.info(2)));
            spec.add(mergeA);
            spec.add(mergeB);
            return spec;
          }
        });
    iwc.setInfoStream(
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            if (DEBUG && ("IW".equals(component) || "MS".equals(component))) {
              System.out.println(Thread.currentThread().getName() + " " + component + ": " + message);
            }
            if ("BD".equals(component) == false || Thread.currentThread() != updater) return;
            try {
              if (message.startsWith("applyDocValuesUpdates") && resolvedOnce.getCount() > 0) {
                // the update packet has resolved against S1, S2, S3 (no IndexWriter lock held):
                // let merge A commit now, so the packet must iterate again
                resolvedOnce.countDown();
                mergeA.release.countDown();
                assertTrue(mergeA.done.await(30, TimeUnit.SECONDS));
              } else if (message.equals("packet matches no segments")
                  && emptyIteration.getCount() > 0) {
                // second iteration found nothing to resolve; let merge B commit before the packet
                // is marked finished (we hold the writer monitor; wait() hands it to merge B)
                emptyIteration.countDown();
                mergeB.release.countDown();
                synchronized (writer) {
                  long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                  while (mergeB.done.getCount() > 0 && System.nanoTime() < deadline) {
                    writer.wait(10);
                  }
                }
              }
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return "BD".equals(component) || (DEBUG && ("IW".equals(component) || "MS".equals(component)));
          }

          @Override
          public void close() {}
        });
    IndexWriter w = new IndexWriter(dir, iwc);
    writer = w;
    w.addDocument(doc("1", "a", 10));
    w.flush();
    w.addDocument(doc("2", "a", 20));
    w.flush();
    w.addDocument(doc("3", "b", 30));
    w.flush();
    w.commit();
    DirectoryReader nrt = DirectoryReader.open(w); // reader pooling on, as with NRT search

    assertEquals(3, w.cloneSegmentInfos().size());
    mergeNow = true;
    w.maybeMerge();
    assertTrue(mergeA.reached.await(30, TimeUnit.SECONDS));
    assertTrue(mergeB.reached.await(30, TimeUnit.SECONDS));

    // ids 1 and 2 are deleted while merge A runs: A's result will be 100% deleted
    w.deleteDocuments(new Term("key", "a"));
    w.flush();

    AtomicReference<Throwable> failure = new AtomicReference<>();
    long[] seqNo = new long[1];
    Thread t =
        new Thread(
            () -> {
              try {
                if (soft) {
                  seqNo[0] =
                      w.softUpdateDocument(
                          new Term("key", "b"), doc("3", "b", 7), new NumericDocValuesField("soft", 1));
                } else {
                  seqNo[0] = w.updateNumericDocValue(new Term("key", "b"), "dv", 7);
                }
                w.flush();
              } catch (Throwable e) {
                failure.set(e);
              }
            },
            "dv-updater");
    updater = t;
    t.start();
    t.join(60_000);
    assertFalse("updater hung", t.isAlive());
    if (failure.get() != null) throw new AssertionError(failure.get());
    cms.sync();

    assertEquals("the packet must re-resolve once", 0, resolvedOnce.getCount());
    assertEquals("the second iteration must find no segment", 0, emptyIteration.getCount());
    assertTrue("merge A's result must be dropped (100% deleted)", mergeA.dropped);
    assertEquals("merge B must have committed", 0, mergeB.done.getCount());
    assertTrue(seqNo[0] > 0);

    nrt.close();
    try (DirectoryReader r = DirectoryReader.open(w)) {
      System.out.println("soft=" + soft + " live docs after the update: " + liveIds(r));
      assertEquals("live docs (one version of id 3): " + liveIds(r), 1, r.numDocs());
      assertEquals("acknowledged update of id 3 (seqNo " + seqNo[0] + ") is visible",
          7, dvOfId(r, "3"));
    } finally {
      w.commit();
      w.close();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        System.out.println("soft=" + soft + " after commit + reopen: live docs " + liveIds(r));
      }
      dir.close();
    }
  }

  static String liveIds(DirectoryReader r) throws IOException {
    StringBuilder sb = new StringBuilder();
    for (LeafReaderContext ctx : r.leaves()) {
      StoredFields sf = ctx.reader().storedFields();
      NumericDocValues dv = ctx.reader().getNumericDocValues("dv");
      for (int i = 0; i < ctx.reader().maxDoc(); i++) {
        if (ctx.reader().getLiveDocs() != null && ctx.reader().getLiveDocs().get(i) == false) {
          continue;
        }
        long v = dv != null && dv.advanceExact(i) ? dv.longValue() : -1;
        sb.append("[id=").append(sf.document(i).get("id")).append(" dv=").append(v).append("]");
      }
    }
    return sb.toString();
  }

  static long dvOfId(DirectoryReader r, String id) throws IOException {
    for (LeafReaderContext ctx : r.leaves()) {
      StoredFields sf = ctx.reader().storedFields();
      NumericDocValues dv = ctx.reader().getNumericDocValues("dv");
      for (int i = 0; i < ctx.reader().maxDoc(); i++) {
        if (ctx.reader().getLiveDocs() != null && ctx.reader().getLiveDocs().get(i) == false) {
          continue;
        }
        if (id.equals(sf.document(i).get("id"))) {
          assertTrue(dv.advanceExact(i));
          return dv.longValue();
        }
      }
    }
    throw new AssertionError("no live doc " + id);
  }
}
