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
import java.io.UncheckedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * With SerialMergeScheduler, two threads call addIndexes(CodecReader...) on the same writer. All
 * addIndexes merges share one writer-wide queue, and SerialMergeScheduler runs whatever is queued on
 * the calling thread; AddIndexesMergeSource#merge rethrows a merge failure to that thread. So a call
 * whose own readers are fine can run the other call's merge and fail with that merge's exception,
 * while the owner of the failing merge also reports it.
 *
 * <p>Threads are ordered by delaying them only: call B's reader is held while B's own merge opens it,
 * until call A is blocked on the scheduler. Call A's reader fails when its merge opens its postings
 * (a stand-in for a read error in A's input). Public API only.
 */
public class TestAddIndexesSerialSchedulerForeignFailure extends LuceneTestCase {

  private static final long WAIT_S = 30;

  private static void waitFor(CountDownLatch l) {
    try {
      if (l.await(WAIT_S, TimeUnit.SECONDS) == false) {
        throw new AssertionError("timed out waiting for the other thread");
      }
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  private static Directory source(String v) throws IOException {
    Directory src = newDirectory();
    try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig(new MockAnalyzer(random())))) {
      Document d = new Document();
      d.add(new StringField("f", v, Field.Store.NO));
      w.addDocument(d);
    }
    return src;
  }

  private abstract static class Wrapped extends FilterCodecReader {
    Wrapped(CodecReader in) {
      super(in);
    }

    @Override
    public CacheHelper getCoreCacheHelper() {
      return in.getCoreCacheHelper();
    }

    @Override
    public CacheHelper getReaderCacheHelper() {
      return in.getReaderCacheHelper();
    }
  }

  public void testCallerFailsWithOtherCallsMergeFailure() throws Exception {
    Directory srcA = source("a");
    Directory srcB = source("b");
    DirectoryReader ra = DirectoryReader.open(srcA);
    DirectoryReader rb = DirectoryReader.open(srcB);
    CodecReader leafA = (CodecReader) ra.leaves().get(0).reader();
    CodecReader leafB = (CodecReader) rb.leaves().get(0).reader();

    AtomicReference<Thread> threadA = new AtomicReference<>();
    CountDownLatch bMerging = new CountDownLatch(1);
    AtomicBoolean holdB = new AtomicBoolean(true);
    // B's input is fine; B's own merge is held until A waits for the scheduler
    CodecReader goodB =
        new Wrapped(leafB) {
          @Override
          public FieldsProducer getPostingsReader() {
            if (holdB.compareAndSet(true, false)) {
              bMerging.countDown();
              long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_S);
              Thread a;
              while ((a = threadA.get()) == null || a.getState() != Thread.State.BLOCKED) {
                if (System.nanoTime() > deadline) {
                  throw new AssertionError("call A never blocked on the merge scheduler");
                }
                Thread.onSpinWait();
              }
            }
            return super.getPostingsReader();
          }
        };
    // A's input fails when a merge opens it
    CodecReader badA =
        new Wrapped(leafA) {
          @Override
          public FieldsProducer getPostingsReader() {
            throw new UncheckedIOException(new IOException("simulated read error in call A's input"));
          }
        };

    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setMergeScheduler(new SerialMergeScheduler());
    iwc.setMergePolicy(new TieredMergePolicy());
    IndexWriter w = new IndexWriter(dir, iwc);

    AtomicReference<Throwable> errA = new AtomicReference<>();
    AtomicReference<Throwable> errB = new AtomicReference<>();
    Thread tb =
        new Thread(
            () -> {
              try {
                w.addIndexes(goodB);
              } catch (Throwable t) {
                errB.set(t);
              }
            });
    Thread ta =
        new Thread(
            () -> {
              try {
                w.addIndexes(badA);
              } catch (Throwable t) {
                errA.set(t);
              }
            });
    tb.start();
    waitFor(bMerging); // B holds the scheduler, running its own merge
    threadA.set(ta);
    ta.start(); // A queues its merge and blocks on the scheduler, which B holds
    tb.join(TimeUnit.SECONDS.toMillis(WAIT_S));
    ta.join(TimeUnit.SECONDS.toMillis(WAIT_S));
    assertFalse(tb.isAlive());
    assertFalse(ta.isAlive());

    int docs;
    try (DirectoryReader r = DirectoryReader.open(w)) {
      docs = r.numDocs();
    }
    try {
      assertNotNull("call A (bad input) must fail", errA.get());
      assertNull(
          "call B's own input was fine and its merge succeeded, but B failed with "
              + errB.get()
              + " (documents in the writer: "
              + docs
              + ")",
          errB.get());
    } finally {
      IOUtils.close(w, ra, rb, dir, srcA, srcB);
    }
  }
}
