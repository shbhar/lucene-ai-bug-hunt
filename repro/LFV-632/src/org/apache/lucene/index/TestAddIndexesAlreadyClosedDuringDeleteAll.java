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
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.InfoStream;

/**
 * While IndexWriter#deleteAll waits for a running merge to abort, merges are disabled. An
 * addIndexes(CodecReader...) call that already passed its initial flush then fails with
 * AlreadyClosedException("this IndexWriter is closed") although the writer is open and stays open:
 * the check at IndexWriter.java:3300 treats "merges disabled" as "writer closed". deleteAll's
 * javadoc says concurrent addIndexes callers may receive MergeAbortedException.
 *
 * <p>Threads are ordered only by delaying them: an InfoStream callback holds the natural merge's
 * thread at its "merging" message (outside the IndexWriter monitor) and signals when deleteAll
 * waits; a FilterCodecReader holds the addIndexes caller in numDocs() (after its flush, before the
 * check). Public API only.
 */
public class TestAddIndexesAlreadyClosedDuringDeleteAll extends LuceneTestCase {

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

  private static Document doc(String v) {
    Document d = new Document();
    d.add(new StringField("f", v, Field.Store.NO));
    return d;
  }

  public void testAddIndexesDuringDeleteAllAbortWait() throws Exception {
    Directory src = newDirectory();
    try (IndexWriter sw = new IndexWriter(src, new IndexWriterConfig(new MockAnalyzer(random())))) {
      sw.addDocument(doc("s"));
    }

    Thread mainThread = Thread.currentThread();
    AtomicBoolean holdMerge = new AtomicBoolean(true);
    CountDownLatch mergeHeld = new CountDownLatch(1);
    CountDownLatch releaseMerge = new CountDownLatch(1);
    CountDownLatch deleteAllWaits = new CountDownLatch(1);
    AtomicReference<Thread> deleteAllThread = new AtomicReference<>();
    InfoStream hook =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            Thread me = Thread.currentThread();
            if ("IW".equals(component)
                && message.startsWith("merging ")
                && me != mainThread
                && holdMerge.compareAndSet(true, false)) {
              // a merge thread, in mergeMiddle (no IndexWriter monitor held)
              mergeHeld.countDown();
              waitFor(releaseMerge);
            } else if ("IW".equals(component)
                && message.startsWith("now wait for ")
                && me == deleteAllThread.get()) {
              deleteAllWaits.countDown(); // inside abortMerges, about to wait()
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return true;
          }

          @Override
          public void close() {}
        };

    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setMergeScheduler(new ConcurrentMergeScheduler());
    iwc.setMergePolicy(new TieredMergePolicy());
    iwc.setInfoStream(hook);
    IndexWriter w = new IndexWriter(dir, iwc);
    w.addDocument(doc("a"));
    w.commit();
    w.addDocument(doc("b"));
    w.commit();
    w.forceMerge(1, false); // a natural (background) merge thread starts and is held
    waitFor(mergeHeld);

    DirectoryReader srcReader = DirectoryReader.open(src);
    CodecReader leaf = (CodecReader) srcReader.leaves().get(0).reader();
    AtomicReference<Thread> caller = new AtomicReference<>();
    CountDownLatch callerValidating = new CountDownLatch(1);
    CountDownLatch releaseCaller = new CountDownLatch(1);
    AtomicBoolean holdCaller = new AtomicBoolean(true);
    CodecReader hooked =
        new FilterCodecReader(leaf) {
          @Override
          public int numDocs() {
            // addIndexes reads numDocs() after its flush, before its merges-enabled check
            if (Thread.currentThread() == caller.get() && holdCaller.compareAndSet(true, false)) {
              callerValidating.countDown();
              waitFor(releaseCaller);
            }
            return super.numDocs();
          }

          @Override
          public CacheHelper getCoreCacheHelper() {
            return in.getCoreCacheHelper();
          }

          @Override
          public CacheHelper getReaderCacheHelper() {
            return in.getReaderCacheHelper();
          }
        };
    AtomicReference<Throwable> addErr = new AtomicReference<>();
    Thread adder =
        new Thread(
            () -> {
              try {
                w.addIndexes(hooked);
              } catch (Throwable t) {
                addErr.set(t);
              }
            });
    caller.set(adder);
    adder.start();
    waitFor(callerValidating); // the caller has flushed and is validating its readers

    AtomicReference<Throwable> daErr = new AtomicReference<>();
    Thread deleter =
        new Thread(
            () -> {
              try {
                w.deleteAll();
              } catch (Throwable t) {
                daErr.set(t);
              }
            });
    deleteAllThread.set(deleter);
    deleter.start();
    waitFor(deleteAllWaits); // deleteAll has disabled merges and waits for the held merge

    releaseCaller.countDown();
    adder.join(TimeUnit.SECONDS.toMillis(WAIT_S));
    assertFalse(adder.isAlive());
    releaseMerge.countDown();
    deleter.join(TimeUnit.SECONDS.toMillis(WAIT_S));
    assertFalse(deleter.isAlive());
    assertNull("deleteAll failed: " + daErr.get(), daErr.get());

    Throwable t = addErr.get();
    boolean open = w.isOpen();
    // the writer is fully usable afterwards
    long seq = w.addIndexes(leaf);
    assertTrue(seq > 0);
    try {
      assertFalse(
          "addIndexes(CodecReader...) threw "
              + t
              + " while the writer was open (isOpen()="
              + open
              + " after deleteAll returned); deleteAll's javadoc says concurrent addIndexes"
              + " callers may receive MergeAbortedException",
          t instanceof AlreadyClosedException && open);
    } finally {
      IOUtils.close(w, srcReader, dir, src);
    }
  }
}
