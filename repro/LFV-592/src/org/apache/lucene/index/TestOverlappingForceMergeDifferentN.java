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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

/**
 * Probe of the TLA+ trace two_nofail_diff: forceMerge(1) on one thread, forceMerge(2) on another
 * while the first one's merge runs. The second call overwrites the writer-wide forced-merge state
 * (mergeMaxNumSegments, segmentsToMerge and the maxNumSegments of the running merges), so the
 * cascade stops at 2 segments and forceMerge(1) can return with 2 segments. No failure is involved.
 */
public class TestOverlappingForceMergeDifferentN extends LuceneTestCase {

  private static void await(CountDownLatch l) {
    try {
      if (l.await(60, TimeUnit.SECONDS) == false) {
        throw new AssertionError("timeout");
      }
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  public void testOverlappingForceMerge() throws Exception {
    CountDownLatch firstMergeHeld = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch secondCallerWaiting = new CountDownLatch(1);
    CountDownLatch firstCallerWaiting = new CountDownLatch(1);
    AtomicInteger middle = new AtomicInteger();
    AtomicInteger waits = new AtomicInteger();
    InfoStream is =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            if ("mergeMiddleStart".equals(message) && middle.incrementAndGet() == 1) {
              firstMergeHeld.countDown();
              await(release);
            } else if ("forceMergeBeforeWait".equals(message)) {
              String t = Thread.currentThread().getName();
              if (t.equals("fm1")) {
                firstCallerWaiting.countDown();
              } else if (t.equals("fm2")) {
                secondCallerWaiting.countDown();
              }
              waits.incrementAndGet();
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return "TP".equals(component);
          }

          @Override
          public void close() {}
        };
    LogDocMergePolicy inner = new LogDocMergePolicy();
    inner.setMergeFactor(2);
    // only forced merges: no natural merges, so the segment count is driven by the forceMerge calls
    MergePolicy mp =
        new FilterMergePolicy(inner) {
          @Override
          public MergeSpecification findMerges(
              MergeTrigger mergeTrigger, SegmentInfos segmentInfos, MergeContext mergeContext) {
            return null;
          }

          @Override
          public MergeSpecification findFullFlushMerges(
              MergeTrigger mergeTrigger, SegmentInfos segmentInfos, MergeContext mergeContext) {
            return null;
          }
        };
    Directory dir = new ByteBuffersDirectory();
    IndexWriter w =
        new IndexWriter(
            dir,
            new IndexWriterConfig()
                .setMergeScheduler(new ConcurrentMergeScheduler())
                .setMergePolicy(mp)
                .setInfoStream(is)) {
          @Override
          protected boolean isEnableTestPoints() {
            return true;
          }
        };
    for (int i = 0; i < 8; i++) {
      Document d = new Document();
      d.add(new StringField("id", "" + i, Field.Store.YES));
      w.addDocument(d);
      w.commit();
    }
    assertEquals(8, w.getSegmentCount());
    AtomicInteger segsAfter1 = new AtomicInteger(-1);
    AtomicReference<Throwable> err = new AtomicReference<>();
    Thread fm1 =
        new Thread(
            () -> {
              try {
                w.forceMerge(1);
                segsAfter1.set(w.getSegmentCount());
              } catch (Throwable t) {
                err.set(t);
              }
            },
            "fm1");
    fm1.start();
    await(firstMergeHeld);
    await(firstCallerWaiting);
    Thread fm2 =
        new Thread(
            () -> {
              try {
                w.forceMerge(2);
              } catch (Throwable t) {
                err.set(t);
              }
            },
            "fm2");
    fm2.start();
    try {
      await(secondCallerWaiting);
    } finally {
      release.countDown();
    }
    fm1.join(60_000);
    fm2.join(60_000);
    w.getConfig().getMergeScheduler();
    ((ConcurrentMergeScheduler) w.getConfig().getMergeScheduler()).sync();
    System.out.println(
        "forceMerge(1) returned with "
            + segsAfter1.get()
            + " segments; after both calls and all merges: "
            + w.getSegmentCount()
            + " segments; error="
            + err.get());
    assertNull(err.get());
    int after = segsAfter1.get();
    w.close();
    dir.close();
    assertEquals("forceMerge(1) must leave at most 1 segment", 1, after);
  }
}
