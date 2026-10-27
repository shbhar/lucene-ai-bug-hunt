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
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A full-flush (merge-on-commit / merge-on-getReader) merge that IndexWriter refuses to register,
 * because one of its segments is already part of a registered merge, is still kept in the
 * point-in-time merge specification: its merge readers are opened and never closed, and the commit
 * or getReader call waits for it although it can never run.
 *
 * <p>MergePolicy#findFullFlushMerges javadoc: "If a OneMerge in the returned MergeSpecification
 * includes a segment already included in a registered merge, then IndexWriter#commit() or
 * IndexWriter#prepareCommit() will throw a IllegalStateException."
 */
public class TestRejectedFullFlushMerge extends LuceneTestCase {

  /** Proposes {s0, s1} and {s1, s2}: the second merge conflicts with the first one. */
  static final class OverlappingFullFlushMerges extends FilterMergePolicy {
    final MergeTrigger trigger;

    OverlappingFullFlushMerges(MergeTrigger trigger) {
      super(NoMergePolicy.INSTANCE);
      this.trigger = trigger;
    }

    @Override
    public MergeSpecification findFullFlushMerges(
        MergeTrigger t, SegmentInfos infos, MergeContext ctx) {
      if (t != trigger || infos.size() < 3) {
        return null;
      }
      MergeSpecification spec = new MergeSpecification();
      spec.add(new OneMerge(List.of(infos.info(0), infos.info(1))));
      spec.add(new OneMerge(List.of(infos.info(1), infos.info(2))));
      return spec;
    }
  }

  /** Two flushed segments plus one buffered document that the full flush turns into a third. */
  private static IndexWriter writerWithThreeSegments(
      Directory dir, MergeTrigger trigger, long waitMillis, MergeScheduler ms) throws IOException {
    IndexWriterConfig iwc =
        new IndexWriterConfig(new MockAnalyzer(random()))
            .setMergePolicy(new OverlappingFullFlushMerges(trigger))
            .setMergeScheduler(ms)
            .setMaxFullFlushMergeWaitMillis(waitMillis)
            .setCommitOnClose(false);
    IndexWriter w = new IndexWriter(dir, iwc);
    for (int i = 0; i < 3; i++) {
      Document d = new Document();
      d.add(new StringField("id", Integer.toString(i), Field.Store.YES));
      w.addDocument(d);
      if (i < 2) {
        w.flush(); // the third segment is flushed by the commit / getReader call itself
      }
    }
    return w;
  }

  /** The documented IllegalStateException is not thrown; the commit silently waits instead. */
  public void testCommitThrowsIllegalStateException() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w =
        writerWithThreeSegments(dir, MergeTrigger.COMMIT, 2000, new ConcurrentMergeScheduler());
    long t0 = System.nanoTime();
    Throwable thrown = null;
    try {
      w.commit();
    } catch (Throwable t) {
      thrown = t;
    }
    long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
    w.rollback();
    try {
      dir.close();
    } catch (Throwable _) {
      // the leaked merge readers are checked by testWriterReleasesAllFilesOnClose
    }
    assertTrue(
        "MergePolicy#findFullFlushMerges javadoc: commit() must throw IllegalStateException for a"
            + " full-flush merge of an already-merging segment; got "
            + thrown
            + " after waiting "
            + ms
            + " ms",
        thrown instanceof IllegalStateException);
  }

  /**
   * Same, with the conflict against a merge that was registered before findFullFlushMerges ran: a
   * natural merge of {s0, s1} is running (held in the merge scheduler) when the commit's
   * findFullFlushMerges proposes {s1, s2} without consulting getMergingSegments().
   */
  public void testCommitThrowsIllegalStateExceptionForRunningMerge() throws Exception {
    Directory dir = newDirectory();
    CountDownLatch naturalRunning = new CountDownLatch(1);
    CountDownLatch releaseNatural = new CountDownLatch(1);
    MergePolicy mp =
        new FilterMergePolicy(NoMergePolicy.INSTANCE) {
          boolean proposed;

          @Override
          public MergeSpecification findMerges(
              MergeTrigger t, SegmentInfos infos, MergeContext ctx) {
            if (proposed || infos.size() != 2) {
              return null;
            }
            proposed = true;
            MergeSpecification spec = new MergeSpecification();
            spec.add(new OneMerge(List.of(infos.info(0), infos.info(1))));
            return spec;
          }

          @Override
          public MergeSpecification findFullFlushMerges(
              MergeTrigger t, SegmentInfos infos, MergeContext ctx) {
            if (t != MergeTrigger.COMMIT || infos.size() < 3) {
              return null;
            }
            MergeSpecification spec = new MergeSpecification();
            spec.add(new OneMerge(List.of(infos.info(1), infos.info(2))));
            return spec;
          }
        };
    MergeScheduler ms =
        new ConcurrentMergeScheduler() {
          final java.util.concurrent.atomic.AtomicBoolean first =
              new java.util.concurrent.atomic.AtomicBoolean(true);

          @Override
          protected void doMerge(MergeSource mergeSource, MergePolicy.OneMerge merge)
              throws IOException {
            if (first.compareAndSet(true, false)) { // the natural merge {s0, s1}
              naturalRunning.countDown();
              try {
                assertTrue(releaseNatural.await(60, TimeUnit.SECONDS));
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            }
            super.doMerge(mergeSource, merge);
          }
        };
    IndexWriterConfig iwc =
        new IndexWriterConfig(new MockAnalyzer(random()))
            .setMergePolicy(mp)
            .setMergeScheduler(ms)
            .setMaxFullFlushMergeWaitMillis(2000)
            .setCommitOnClose(false);
    IndexWriter w = new IndexWriter(dir, iwc);
    for (int i = 0; i < 3; i++) {
      Document d = new Document();
      d.add(new StringField("id", Integer.toString(i), Field.Store.YES));
      w.addDocument(d);
      if (i < 2) {
        w.flush();
      }
    }
    assertTrue(naturalRunning.await(30, TimeUnit.SECONDS)); // {s0, s1} is registered and running
    long t0 = System.nanoTime();
    Throwable thrown = null;
    try {
      w.commit();
    } catch (Throwable t) {
      thrown = t;
    }
    long ms2 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
    releaseNatural.countDown();
    w.rollback();
    try {
      dir.close();
    } catch (Throwable _) {
      // leaked merge readers, see testWriterReleasesAllFilesOnClose
    }
    assertTrue(
        "MergePolicy#findFullFlushMerges javadoc: commit() must throw IllegalStateException for a"
            + " full-flush merge of a segment that is already in a running merge; got "
            + thrown
            + " after waiting "
            + ms2
            + " ms",
        thrown instanceof IllegalStateException);
  }

  /** The rejected merge's readers are never closed: files stay open after IndexWriter.close(). */
  public void testWriterReleasesAllFilesOnClose() throws Exception {
    MockDirectoryWrapper dir = newMockDirectory();
    IndexWriter w =
        writerWithThreeSegments(dir, MergeTrigger.COMMIT, 500, new ConcurrentMergeScheduler());
    try {
      w.commit();
    } catch (IllegalStateException _) {
      // documented outcome
    }
    w.close();
    Throwable closeFailure = null;
    try {
      dir.close(); // MockDirectoryWrapper fails if any file is still open
    } catch (Throwable t) {
      closeFailure = t;
    }
    assertNull("IndexWriter.close() left files open: " + closeFailure, closeFailure);
  }

  /** With a long wait, getReader never returns, not even after rollback() closed the writer. */
  public void testGetReaderReturnsAfterRollback() throws Exception {
    Directory dir = newDirectory();
    CountDownLatch inMerge = new CountDownLatch(1);
    MergeScheduler ms =
        new ConcurrentMergeScheduler() {
          @Override
          public synchronized void merge(MergeSource mergeSource, MergeTrigger trigger)
              throws IOException {
            super.merge(mergeSource, trigger);
            if (trigger == MergeTrigger.GET_READER) {
              inMerge.countDown(); // getReader is now past the merge registration
            }
          }
        };
    IndexWriter w = writerWithThreeSegments(dir, MergeTrigger.GET_READER, 3_600_000L, ms);
    AtomicReference<Object> result = new AtomicReference<>();
    Thread t =
        new Thread(
            () -> {
              try {
                result.set(DirectoryReader.open(w));
              } catch (Throwable th) {
                result.set(th);
              }
            });
    t.start();
    assertTrue(inMerge.await(30, TimeUnit.SECONDS));
    w.rollback();
    assertFalse(w.isOpen());
    t.join(20_000);
    boolean stuck = t.isAlive();
    if (stuck) {
      t.interrupt(); // MergeSpecification.await is interruptible
      t.join();
    }
    if (result.get() instanceof DirectoryReader r) {
      r.close();
    }
    try {
      dir.close();
    } catch (Throwable _) {
      // leaked merge readers, see testWriterReleasesAllFilesOnClose
    }
    assertFalse(
        "DirectoryReader.open(writer) still waiting 20 s after rollback() closed the writer", stuck);
  }
}
