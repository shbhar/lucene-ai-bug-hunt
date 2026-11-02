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
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.InfoStream;

/**
 * IndexWriter.close() (commitOnClose=true) waits in waitForMerges() until no merge is pending or
 * running. If a merge fails while close() is waiting, the tragic-event rollback is skipped (the
 * writer is already closing) and waitForMerges() never checks for the tragedy. With
 * ConcurrentMergeScheduler's default settings for a spinning disk (maxThreadCount=1,
 * maxMergeCount=6) and two big merges (over 50 MB each), CMS stops the larger merge while the
 * smaller one runs. When the running merge fails, its merge thread exits without calling
 * updateMergeThreads(), so the stopped merge is never resumed or aborted, and close() never
 * returns.
 *
 * <p>The schedule is fixed with latches on InfoStream messages ("now stop" from CMS, "waitForMerges"
 * from IndexWriter) and one simulated disk-full IOException on the running merge's thread. The test
 * asserts that close() returns (normally or with an exception) once the fault has happened.
 */
public class TestCloseAfterMergeFailureWithStoppedMerge extends LuceneTestCase {

  private static final int MB = 1024 * 1024;
  private static final long WAIT_SECONDS = 60;

  public void testCloseReturnsAfterRunningMergeFailsWhileBigMergeIsStopped() throws Exception {
    MockDirectoryWrapper dir = newMockFSDirectory(createTempDir("close-after-merge-failure"));
    dir.setThrottling(MockDirectoryWrapper.Throttling.NEVER);
    dir.setCheckIndexOnClose(false);

    CountDownLatch largerMergeStopped = new CountDownLatch(1);
    CountDownLatch closeWaitsForMerges = new CountDownLatch(1);
    CountDownLatch failingMergeStarted = new CountDownLatch(1);
    AtomicReference<Thread> failingMergeThread = new AtomicReference<>();
    AtomicBoolean failNextWrite = new AtomicBoolean();

    InfoStream latches =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            if ("MS".equals(component) && message.contains("now stop")) {
              largerMergeStopped.countDown();
            }
            if ("IW".equals(component) && message.equals("waitForMerges")) {
              closeWaitsForMerges.countDown();
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return "MS".equals(component) || "IW".equals(component);
          }

          @Override
          public void close() {}
        };

    // one simulated disk-full error on the running merge's thread, once close() waits for merges
    dir.failOn(
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper d) throws IOException {
            if (Thread.currentThread() == failingMergeThread.get()
                && failNextWrite.compareAndSet(true, false)) {
              throw new IOException("simulated disk full during merge");
            }
          }
        });

    TwoBigMerges mergePolicy =
        new TwoBigMerges(
            () -> {
              if (failingMergeThread.compareAndSet(null, Thread.currentThread())) {
                failingMergeStarted.countDown();
                await(closeWaitsForMerges);
                failNextWrite.set(true);
              }
            });

    List<Throwable> mergeThreadExceptions = Collections.synchronizedList(new ArrayList<>());
    ConcurrentMergeScheduler cms =
        new ConcurrentMergeScheduler() {
          @Override
          protected void handleMergeException(Throwable exc) {
            mergeThreadExceptions.add(exc);
          }
        };
    // ConcurrentMergeScheduler's own defaults for a spinning disk: maxThreadCount=1,
    // maxMergeCount=6
    cms.setDefaultMaxMergesAndThreads(true);

    IndexWriterConfig iwc =
        new IndexWriterConfig(new MockAnalyzer(random()))
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(mergePolicy)
            .setMergeScheduler(cms)
            .setUseCompoundFile(false)
            .setRAMBufferSizeMB(256)
            .setMaxFullFlushMergeWaitMillis(0)
            .setCommitOnClose(true)
            .setInfoStream(latches);
    IndexWriter w = new IndexWriter(dir, iwc);

    // four segments: 2 x 55 docs (merge A, the larger) and 2 x 45 docs (merge B) of 1 MB each;
    // both merges are "big" for CMS (estimated size > 50 MB), so with maxThreadCount=1 CMS stops
    // A and lets B run
    byte[] blob = new byte[MB];
    for (int docs : new int[] {55, 55, 45, 45}) {
      for (int i = 0; i < docs; i++) {
        random().nextBytes(blob);
        Document doc = new Document();
        doc.add(new StoredField("blob", blob));
        w.addDocument(doc);
      }
      w.commit();
    }
    assertEquals(4, w.getSegmentCount());

    mergePolicy.armed = true;
    w.maybeMerge(); // registers B, then A; CMS starts one merge thread for each

    boolean scheduled =
        largerMergeStopped.await(WAIT_SECONDS, TimeUnit.SECONDS)
            && failingMergeStarted.await(WAIT_SECONDS, TimeUnit.SECONDS);
    if (scheduled == false) {
      closeWaitsForMerges.countDown(); // let merge B continue without a fault
      w.rollback();
      dir.close();
      fail("setup: CMS did not stop the larger merge, or merge B did not start");
    }

    AtomicReference<Throwable> closeException = new AtomicReference<>();
    Thread closer =
        new Thread(
            () -> {
              try {
                w.close();
              } catch (Throwable t) {
                closeException.set(t);
              }
            },
            "closer");
    closer.start();
    closer.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));

    boolean closeHung = closer.isAlive();
    String evidence = "";
    if (closeHung) {
      long[] monitorDeadlock = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
      StringBuilder sb = new StringBuilder();
      sb.append("tragic exception: ").append(w.getTragicException()).append('\n');
      sb.append("merge thread exceptions: ").append(mergeThreadExceptions).append('\n');
      sb.append("JVM-detected monitor deadlock: ")
          .append(monitorDeadlock == null ? "none" : monitorDeadlock.length + " threads")
          .append('\n');
      for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
        Thread t = e.getKey();
        if (t == closer || t.getName().startsWith("Lucene Merge Thread")) {
          sb.append(t.getName()).append(" ").append(t.getState()).append('\n');
          StackTraceElement[] st = e.getValue();
          for (int i = 0; i < Math.min(st.length, 12); i++) {
            sb.append("    at ").append(st[i]).append('\n');
          }
        }
      }
      evidence = sb.toString();
      // clean up: interrupting close() makes shutdown() roll back, which aborts the stopped merge
      closer.interrupt();
      closer.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
    }
    assertFalse(
        "IndexWriter.close() did not return within "
            + WAIT_SECONDS
            + " s after a merge failed while close() was waiting for merges\n"
            + evidence,
        closeHung);
    assertNotNull("the merge failure should be the writer's tragic exception", w.getTragicException());
    assertFalse(w.isOpen());
    dir.close();
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(WAIT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Proposes exactly two merges (B = segments 2+3 first, then A = segments 0+1) once armed. */
  private static final class TwoBigMerges extends MergePolicy {
    volatile boolean armed;
    private final Runnable onFailingMergeStart;

    TwoBigMerges(Runnable onFailingMergeStart) {
      this.onFailingMergeStart = onFailingMergeStart;
    }

    @Override
    public MergeSpecification findMerges(
        MergeTrigger mergeTrigger, SegmentInfos segmentInfos, MergeContext mergeContext) {
      if (armed == false || mergeTrigger != MergeTrigger.EXPLICIT) {
        return null;
      }
      armed = false;
      List<SegmentCommitInfo> infos = new ArrayList<>();
      for (SegmentCommitInfo info : segmentInfos) {
        infos.add(info);
      }
      MergeSpecification spec = new MergeSpecification();
      spec.add(
          new OneMerge(List.of(infos.get(2), infos.get(3))) {
            @Override
            public CodecReader wrapForMerge(CodecReader reader) throws IOException {
              onFailingMergeStart.run();
              return reader;
            }
          });
      spec.add(new OneMerge(List.of(infos.get(0), infos.get(1))));
      return spec;
    }

    @Override
    public MergeSpecification findForcedMerges(
        SegmentInfos segmentInfos,
        int maxSegmentCount,
        Map<SegmentCommitInfo, Boolean> segmentsToMerge,
        MergeContext mergeContext) {
      return null;
    }

    @Override
    public MergeSpecification findForcedDeletesMerges(
        SegmentInfos segmentInfos, MergeContext mergeContext) {
      return null;
    }
  }
}
