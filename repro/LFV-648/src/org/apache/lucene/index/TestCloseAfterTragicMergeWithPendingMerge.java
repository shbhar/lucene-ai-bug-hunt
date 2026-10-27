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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

/**
 * IndexWriter.close() (commitOnClose=true, ConcurrentMergeScheduler) must return or throw after a
 * background merge fails. It never returns when, at the moment the failure is recorded as the
 * writer's tragic exception, another merge is registered but not started yet:
 *
 * <ul>
 *   <li>close() is closing, so the tragic event does not roll the writer back;
 *   <li>after the tragedy, getNextMerge() / hasPendingMerges() refuse to hand out the pending merge,
 *       so no merge thread can ever start it;
 *   <li>waitForMerges() loops until pendingMerges is empty and never looks at the tragedy.
 * </ul>
 *
 * <p>The pending merge here is the cascade merge that a successful forced merge registers
 * (forceMerge(1, false) followed by close()). Latches in a Directory wrapper order the three
 * forced merges; an InfoStream hook only holds the successful merge thread for the few
 * instructions between registering the cascade merge and asking the scheduler to start it. The
 * one injected fault is an IOException from createOutput of one merge (a full disk); no fault
 * fires after it.
 */
public class TestCloseAfterTragicMergeWithPendingMerge extends LuceneTestCase {

  private static void await(CountDownLatch latch) {
    try {
      if (latch.await(60, TimeUnit.SECONDS) == false) {
        throw new AssertionError("test latch timed out");
      }
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  private static void addSegment(IndexWriter w, int numDocs, String prefix) throws IOException {
    for (int i = 0; i < numDocs; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", prefix + i, Field.Store.YES));
      w.addDocument(doc);
    }
    w.commit();
  }

  /** The bug: one merge fails while the cascade merge is pending; close() never returns. */
  public void testCloseReturnsAfterMergeFailureWithPendingMerge() throws Exception {
    runScenario(true);
  }

  /** Control: the same schedule without the fault; close() commits and returns. */
  public void testControlSameScheduleWithoutFault() throws Exception {
    runScenario(false);
  }

  private void runScenario(boolean failC) throws Exception {
    // Forced merges of LogDocMergePolicy(mergeFactor=2) over six segments of 1,1,2,2,4,4 docs:
    // A=[s1,s2] (2 docs), B=[s3,s4] (4 docs), C=[s5,s6] (8 docs). When B finishes after A, the
    // forced-merge cascade registers X=[A',B'] (6 docs).
    final AtomicBoolean armed = new AtomicBoolean(false);
    final CountDownLatch releaseA = new CountDownLatch(1);
    final CountDownLatch releaseB = new CountDownLatch(1);
    final CountDownLatch releaseC = new CountDownLatch(1);
    final AtomicInteger cFaults = new AtomicInteger();

    Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexOutput createOutput(String name, IOContext context) throws IOException {
            if (armed.get()
                && context.context() == IOContext.Context.MERGE
                && context.mergeInfo() != null) {
              int docs = context.mergeInfo().totalMaxDoc();
              if (docs == 2) {
                await(releaseA);
              } else if (docs == 4) {
                await(releaseB);
              } else if (docs == 8) {
                await(releaseC);
                if (failC && cFaults.getAndIncrement() == 0) {
                  throw new IOException("simulated: no space left on device");
                }
              }
            }
            return super.createOutput(name, context);
          }
        };

    final CountDownLatch closeWaitingForMerges = new CountDownLatch(1);
    final CountDownLatch firstMergeDone = new CountDownLatch(1);
    final CountDownLatch secondMergeDoneHeld = new CountDownLatch(1);
    final CountDownLatch releaseSecondMergeThread = new CountDownLatch(1);
    final AtomicInteger mergedCount = new AtomicInteger();
    InfoStream infoStream =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            if (armed.get() == false || "IW".equals(component) == false) {
              return;
            }
            if (message.equals("waitForMerges")) {
              closeWaitingForMerges.countDown();
            } else if (message.startsWith("merged new segment")) {
              // printed by IndexWriter.merge after the merge committed, outside of any lock
              int n = mergedCount.incrementAndGet();
              if (n == 1) {
                firstMergeDone.countDown();
              } else if (n == 2) {
                secondMergeDoneHeld.countDown();
                await(releaseSecondMergeThread);
              }
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return "IW".equals(component);
          }

          @Override
          public void close() {}
        };

    LogDocMergePolicy mp = new LogDocMergePolicy();
    mp.setMergeFactor(2);
    mp.setMinMergeDocs(1);
    ConcurrentMergeScheduler cms = new ConcurrentMergeScheduler();
    cms.setMaxMergesAndThreads(4, 4);
    // only keeps the expected merge failure from failing the test as an uncaught exception
    cms.setSuppressExceptions();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setMergeScheduler(cms)
            .setInfoStream(infoStream)
            .setCommitOnClose(true)
            .setMaxFullFlushMergeWaitMillis(0);
    IndexWriter w = new IndexWriter(dir, iwc);
    addSegment(w, 1, "a");
    addSegment(w, 1, "b");
    addSegment(w, 2, "c");
    addSegment(w, 2, "d");
    addSegment(w, 4, "e");
    addSegment(w, 4, "f");
    assertEquals(6, w.cloneSegmentInfos().size());
    w.getConfig().setMergePolicy(mp);

    armed.set(true);
    // starts A, B and C in the background; all three wait in createOutput
    w.forceMerge(1, false);

    final AtomicReference<Throwable> closeResult = new AtomicReference<>();
    final AtomicBoolean closeReturned = new AtomicBoolean(false);
    Thread closer =
        new Thread(
            () -> {
              try {
                w.close();
              } catch (Throwable t) {
                closeResult.set(t);
              } finally {
                closeReturned.set(true);
              }
            },
            "closer");
    closer.start();
    await(closeWaitingForMerges); // close() is inside waitForMerges

    releaseA.countDown();
    await(firstMergeDone); // A committed; the cascade found nothing to register yet

    releaseB.countDown();
    await(secondMergeDoneHeld); // B committed and registered the cascade merge X (pending)
    assertTrue("expected the cascade merge to be pending", w.hasPendingMerges());

    releaseC.countDown(); // with failC, C fails: tragic exception, but close() is in progress
    if (failC) {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
      while (w.getTragicException() == null && System.nanoTime() < deadline) {
        Thread.sleep(10);
      }
      assertNotNull("merge C should have failed tragically", w.getTragicException());
    }

    releaseSecondMergeThread.countDown(); // B's merge thread now asks the scheduler for X

    closer.join(TimeUnit.SECONDS.toMillis(60));
    boolean hung = closer.isAlive();
    StringBuilder closerStack = new StringBuilder();
    for (StackTraceElement e : closer.getStackTrace()) {
      closerStack.append("\n    at ").append(e);
    }
    if (hung) {
      // clean up: interrupting the waiting close() makes it roll back and close the writer
      closer.interrupt();
      closer.join(TimeUnit.SECONDS.toMillis(60));
    }
    assertFalse(
        "IndexWriter.close() did not return within 60 s after a background merge failed"
            + " (tragic exception: "
            + w.getTragicException()
            + "); it waits in waitForMerges() for a pending merge that can no longer start;"
            + " close() thread stack:"
            + closerStack,
        hung);
    assertTrue(closeReturned.get());
    if (failC == false) {
      assertNull("close() should succeed without a fault", closeResult.get());
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(14, r.numDocs());
      }
    }
    dir.close();
  }
}
