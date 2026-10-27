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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * LogMergePolicy#findForcedDeletesMerges ignores MergeContext#getMergingSegments(): when a
 * registered (pending or running) merge already holds one of the segments of a run of segments
 * with deletes, the whole run is returned as one OneMerge, IndexWriter#registerMerge rejects it,
 * and the other segments of the run keep their deletes after forceMergeDeletes(true) returns.
 */
public class TestLogMergePolicyForceMergeDeletesWhileMerging extends LuceneTestCase {

  /** Runs merges only once enabled, so a natural merge can be registered but not yet executed. */
  static final class GatedMergeScheduler extends MergeScheduler {
    volatile boolean enabled;

    @Override
    public synchronized void merge(MergeSource mergeSource, MergeTrigger trigger)
        throws IOException {
      if (enabled == false) {
        return;
      }
      MergePolicy.OneMerge merge;
      while ((merge = mergeSource.getNextMerge()) != null) {
        mergeSource.merge(merge);
      }
    }

    @Override
    public void close() {}
  }

  static void addSegment(IndexWriter w, String prefix, int numDocs, int numDeletes)
      throws IOException {
    for (int i = 0; i < numDocs; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", prefix + i, Field.Store.NO));
      w.addDocument(doc);
    }
    w.commit();
    for (int i = 0; i < numDeletes; i++) {
      w.deleteDocuments(new Term("id", prefix + i));
    }
    w.commit();
  }

  /** Segments: S1 (5000 docs) then S2, S3, S4 (20 docs each), all with deletes. */
  static void buildIndex(Directory dir) throws IOException {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      addSegment(w, "a", 5000, 500);
      addSegment(w, "b", 20, 2);
      addSegment(w, "c", 20, 2);
      addSegment(w, "d", 20, 2);
    }
  }

  /**
   * Same scenario with the stock ConcurrentMergeScheduler: the natural merge of S2, S3, S4 is
   * running (held in doMerge by a latch) when forceMergeDeletes(true) is called.
   */
  public void testForceMergeDeletesWhileNaturalMergeIsRunning() throws Exception {
    try (Directory dir = newDirectory()) {
      buildIndex(dir);
      LogDocMergePolicy mp = new LogDocMergePolicy();
      mp.setMergeFactor(3);
      java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.atomic.AtomicBoolean first =
          new java.util.concurrent.atomic.AtomicBoolean(true);
      ConcurrentMergeScheduler cms =
          new ConcurrentMergeScheduler() {
            @Override
            protected void doMerge(MergeSource mergeSource, MergePolicy.OneMerge merge)
                throws IOException {
              if (first.compareAndSet(true, false)) {
                started.countDown();
                try {
                  release.await();
                } catch (InterruptedException e) {
                  throw new org.apache.lucene.util.ThreadInterruptedException(e);
                }
              }
              super.doMerge(mergeSource, merge);
            }
          };
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setMergePolicy(mp)
              .setMergeScheduler(cms);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        assertEquals(4, w.getSegmentCount());
        w.maybeMerge(); // starts the natural merge of S2, S3, S4 in a merge thread
        started.await();
        w.forceMergeDeletes(true); // while that merge is running
        release.countDown();
        w.waitForMerges();
        assertFalse(
            "deleted documents left after forceMergeDeletes(true): " + w.cloneSegmentInfos(),
            w.hasDeletions());
      } finally {
        release.countDown();
      }
    }
  }

  public void testForceMergeDeletesWhileNaturalMergeIsPending() throws Exception {
    try (Directory dir = newDirectory()) {
      buildIndex(dir);
      LogDocMergePolicy mp = new LogDocMergePolicy();
      mp.setMergeFactor(3);
      GatedMergeScheduler scheduler = new GatedMergeScheduler();
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setMergePolicy(mp)
              .setMergeScheduler(scheduler);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        assertEquals(4, w.getSegmentCount());
        w.maybeMerge(); // registers the natural merge of S2, S3, S4; nothing runs yet
        assertTrue("a natural merge should be pending", w.hasPendingMerges());

        scheduler.enabled = true;
        w.forceMergeDeletes(true);
        w.waitForMerges();

        // S1 (500 of 5000 docs deleted) is not part of the natural merge: it must have been merged
        assertFalse(
            "deleted documents left after forceMergeDeletes(true): " + w.cloneSegmentInfos(),
            w.hasDeletions());
      }
    }
  }
}
