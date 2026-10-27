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
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

/**
 * Impact demo (not the oracle of the candidate): the same interleaving as
 * TestForceMergeAdoptedMergeFailure, followed by the common "forceMerge(1); close()" sequence with
 * the default commitOnClose=true. Both calls return normally, yet the document indexed since the
 * last commit is not in the index: the tragic event rolled the writer back and close() of an
 * already closed writer does nothing. The test asserts that a sequence of calls that all returned
 * normally kept every document.
 *
 * <p>forceMerge(1) waits for a merge that was already registered when it was called (it marks it as a
 * forced merge). If that merge fails, forceMerge must not return normally with the index still
 * unmerged. The failure is dropped from IndexWriter's mergeExceptions list because the merge keeps
 * the mergeGen of its registration, and the tragic event that the failing merge thread sets is
 * published only after mergeFinish() has woken the waiting forceMerge. The InfoStream below holds
 * the merge thread right before it sets the tragedy, which makes that window deterministic.
 */
public class TestForceMergeAdoptedMergeFailureThenClose extends LuceneTestCase {

  /**
   * MockDirectoryWrapper fault injection, as in Lucene's own disk-full tests: once armed, every new
   * file a merge writes fails with an IOException.
   */
  private static final class DiskFull extends MockDirectoryWrapper.Failure {
    final AtomicBoolean armed = new AtomicBoolean();
    final AtomicInteger thrown = new AtomicInteger();

    @Override
    public void eval(MockDirectoryWrapper dir) throws IOException {
      if (armed.get() && callStackContains(IndexWriter.class, "mergeMiddle")) {
        thrown.incrementAndGet();
        throw new IOException("fake disk full while writing the merged segment");
      }
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (latch.await(60, TimeUnit.SECONDS) == false) {
        throw new AssertionError("timed out waiting for " + latch);
      }
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  private static Document doc(String id) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    return doc;
  }

  /**
   * @param naturalMergeFromCommit true: the natural merge is started by an earlier commit() and is
   *     already running when forceMerge(1) is called; false: it is started by forceMerge's own flush
   *     (no other call at all)
   */
  private void runScenario(boolean naturalMergeFromCommit) throws Exception {
    runScenario(naturalMergeFromCommit, false);
  }

  /**
   * @param ownMerge control: no natural merge at all (merge factor 10); the merge that fails is the
   *     one forceMerge(1) itself registers
   */
  private void runScenario(boolean naturalMergeFromCommit, boolean ownMerge) throws Exception {
    final CountDownLatch mergeInMiddle = new CountDownLatch(1);
    final CountDownLatch releaseMerge = new CountDownLatch(1);
    final CountDownLatch forceMergeWaiting = new CountDownLatch(1);
    final CountDownLatch forceMergeReturned = new CountDownLatch(1);
    final AtomicBoolean heldBeforeTragedy = new AtomicBoolean();
    final AtomicInteger middleStarts = new AtomicInteger();

    InfoStream infoStream =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            if ("TP".equals(component) && "mergeMiddleStart".equals(message)) {
              if (middleStarts.incrementAndGet() == 1) {
                mergeInMiddle.countDown();
                await(releaseMerge);
              }
            } else if ("TP".equals(component) && "forceMergeBeforeWait".equals(message)) {
              forceMergeWaiting.countDown();
            } else if ("IW".equals(component) && message.startsWith("hit tragic")) {
              // The failed merge has already left runningMerges and released the IndexWriter
              // monitor; the tragedy is set right after this message. Hold it here until
              // forceMerge has returned (bounded, so a fixed IndexWriter cannot hang the test).
              heldBeforeTragedy.set(true);
              try {
                forceMergeReturned.await(10, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                throw new AssertionError(e);
              }
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return "TP".equals(component) || "IW".equals(component);
          }

          @Override
          public void close() {}
        };

    final AtomicReference<Throwable> mergeThreadFailure = new AtomicReference<>();
    ConcurrentMergeScheduler cms =
        new ConcurrentMergeScheduler() {
          @Override
          protected void handleMergeException(Throwable exc) {
            mergeThreadFailure.compareAndSet(null, exc);
          }
        };
    LogDocMergePolicy mp = new LogDocMergePolicy();
    mp.setMergeFactor(ownMerge ? 10 : 2);

    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    DiskFull diskFull = new DiskFull();
    dir.failOn(diskFull);
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setMergeScheduler(cms)
            .setMergePolicy(mp)
            .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            .setRAMBufferSizeMB(256)
            .setInfoStream(infoStream);
    IndexWriter w =
        new IndexWriter(dir, iwc) {
          @Override
          protected boolean isEnableTestPoints() {
            return true;
          }
        };

    w.addDocument(doc("0"));
    w.commit(); // segment _0, nothing to merge yet
    w.addDocument(doc("1"));
    if (ownMerge) {
      w.commit(); // segment _1; merge factor 10: no natural merge
      assertEquals(2, w.getSegmentCount());
    } else if (naturalMergeFromCommit) {
      // segment _1; the commit's maybeMerge registers the natural merge [_0, _1] and CMS starts it
      w.commit();
      await(mergeInMiddle);
      assertEquals(2, w.getSegmentCount());
    }

    final AtomicReference<Throwable> forceMergeFailure = new AtomicReference<>();
    final AtomicInteger segmentsAtReturn = new AtomicInteger(-1);
    final AtomicBoolean returnedNormally = new AtomicBoolean();
    Thread forceMerger =
        new Thread(
            () -> {
              try {
                w.forceMerge(1);
                returnedNormally.set(true);
                segmentsAtReturn.set(w.getSegmentCount());
              } catch (Throwable t) {
                forceMergeFailure.set(t);
              } finally {
                forceMergeReturned.countDown();
              }
            },
            "forceMerger");
    forceMerger.start();

    // forceMerge(1) adopted the running natural merge and is now waiting for it
    await(forceMergeWaiting);
    await(mergeInMiddle);

    // make the adopted merge fail (disk full), then let it continue
    diskFull.armed.set(true);
    releaseMerge.countDown();

    forceMerger.join(60_000);
    assertFalse("forceMerge did not finish", forceMerger.isAlive());

    // wait for the tragic rollback the failed merge triggers
    for (int i = 0; i < 600 && w.isOpen(); i++) {
      Thread.sleep(100);
    }
    diskFull.armed.set(false);
    cms.sync();

    assertTrue("the merge must have hit the injected failure", diskFull.thrown.get() > 0);
    assertTrue("the hook before the tragedy must have run", heldBeforeTragedy.get());
    if (returnedNormally.get() == false) {
      // forceMerge reported the failure: the caller knows, nothing to check
      System.out.println(getTestName() + ": forceMerge threw " + forceMergeFailure.get());
      w.close();
      dir.close();
      return;
    }
    Throwable closeFailure = null;
    try {
      w.close(); // commitOnClose=true (default): flush, wait for merges, commit, close
    } catch (Throwable t) {
      closeFailure = t;
    }
    int docs;
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      docs = r.numDocs();
    }
    System.out.println(
        getTestName()
            + ": forceMerge(1) returned normally, close() threw="
            + closeFailure
            + ", documents in the index after close: "
            + docs
            + " of 2, tragedy="
            + w.getTragicException());
    if (closeFailure == null) {
      assertEquals(
          "forceMerge(1) and close() both returned normally, but the index lost documents", 2, docs);
    }
    dir.close();
  }

  /** The natural merge is started by forceMerge's own flush; doc "1" is not yet committed. */
  public void testForceMergeThenCloseLosesUncommittedDoc() throws Exception {
    runScenario(false);
  }
}
