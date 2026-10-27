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
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

/**
 * IndexWriter#deleteAll must delete every document whose add returned a smaller sequence number.
 *
 * <p>DocumentsWriter#lockAndAbortAll discards the publishable flush tickets first (forcePurge), then
 * aborts the pooled DWPTs and waits for running flushes (waitForFlush). A segment flush that was
 * already running (its ticket still pending at the purge) completes during that wait, and its ticket
 * stays in the ticket queue. deleteAll then clears segmentInfos and returns; the flushing thread
 * publishes the segment afterwards, so documents with a smaller seqNo than deleteAll survive it.
 * (deleteAll's own processEvents would publish the ticket first only if the flushing thread had
 * queued a ticket-backlog purge, which it does only when ticketCount >= the pool size.)
 *
 * <p>Public API only: InfoStream callbacks (keyed on thread and message) force the interleaving,
 * plus a barrier inside a document's Reader to get three DWPTs.
 */
public class TestDeleteAllMissesInFlightFlush extends LuceneTestCase {

  private static Document doc(String id, Reader body) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new TextField("body", body));
    return d;
  }

  private static int committedNumDocs(Directory dir) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      return r.numDocs();
    }
  }

  private static boolean waitsInForcePurge(Thread t) {
    if (t.getState() != Thread.State.WAITING && t.getState() != Thread.State.TIMED_WAITING) {
      return false;
    }
    for (StackTraceElement e : t.getStackTrace()) {
      if (e.getClassName().equals(DocumentsWriterFlushQueue.class.getName())
          && e.getMethodName().equals("forcePurge")) {
        return true;
      }
    }
    return false;
  }

  /** Three threads each hold a DWPT at the same time, so the pool ends up with three DWPTs. */
  private static void createThreeDwpts(IndexWriter w) throws Exception {
    CyclicBarrier barrier = new CyclicBarrier(3);
    List<Thread> ts = new ArrayList<>();
    AtomicReference<Throwable> err = new AtomicReference<>();
    for (int i = 0; i < 3; i++) {
      final int id = i;
      Reader r =
          new StringReader("a b c") {
            boolean waited;

            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
              if (waited == false) {
                waited = true;
                try {
                  barrier.await(30, TimeUnit.SECONDS); // all three DWPTs are held now
                } catch (Exception e) {
                  throw new IOException(e);
                }
              }
              return super.read(cbuf, off, len);
            }
          };
      Thread t =
          new Thread(
              () -> {
                try {
                  w.addDocument(doc("h" + id, r));
                } catch (Throwable e) {
                  err.set(e);
                }
              });
      ts.add(t);
      t.start();
    }
    for (Thread t : ts) {
      t.join(30_000);
      assertFalse(t.isAlive());
    }
    assertNull(err.get());
  }

  /**
   * Runs addDocument (which flushes a DWPT on this thread) concurrently with deleteAll, forcing the
   * interleaving described in the class javadoc. Returns {addSeqNo, deleteAllSeqNo}.
   */
  private long[] raceAddFlushWithDeleteAll(Directory dir, AtomicReference<IndexWriter> writerRef)
      throws Exception {
    Thread mainThread = Thread.currentThread();
    AtomicReference<Thread> deleteAllThread = new AtomicReference<>();
    AtomicBoolean armFlush = new AtomicBoolean(false);
    AtomicBoolean armAbort = new AtomicBoolean(true);
    AtomicBoolean armPublish = new AtomicBoolean(false);
    CountDownLatch deleteAllIsAborting = new CountDownLatch(1);
    CountDownLatch flusherAtPublish = new CountDownLatch(1);
    CountDownLatch deleteAllReturned = new CountDownLatch(1);
    AtomicLong deleteAllSeqNo = new AtomicLong(-1);
    AtomicReference<Throwable> err = new AtomicReference<>();

    InfoStream hook =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            try {
              Thread me = Thread.currentThread();
              if (me == mainThread
                  && "DWPT".equals(component)
                  && message.startsWith("flush postings as segment")
                  && armFlush.compareAndSet(true, false)) {
                // (1) main thread is inside DWPT#flush; its flush ticket is queued but not done.
                Thread t =
                    new Thread(
                        () -> {
                          try {
                            deleteAllSeqNo.set(writerRef.get().deleteAll());
                          } catch (Throwable e) {
                            err.set(e);
                          } finally {
                            deleteAllReturned.countDown();
                          }
                        },
                        "deleteAll");
                deleteAllThread.set(t);
                armPublish.set(true);
                t.start();
                // (2) wait until deleteAll has purged the ticket queue and is aborting pooled DWPTs
                assertTrue(deleteAllIsAborting.await(30, TimeUnit.SECONDS));
              } else if (me == deleteAllThread.get()
                  && "DWPT".equals(component)
                  && message.equals("now abort")
                  && armAbort.compareAndSet(true, false)) {
                deleteAllIsAborting.countDown();
                // (3) let the main thread finish its flush and reach the publish step
                assertTrue(flusherAtPublish.await(30, TimeUnit.SECONDS));
              } else if (me == mainThread
                  && "IW".equals(component)
                  && message.startsWith("publishFlushedSegment seg-private updates=")
                  && armPublish.compareAndSet(true, false)) {
                // (4) about to publish the flushed segment; let deleteAll run to completion first
                // (or until it waits for this thread's ticket-queue purge lock, which only a fixed
                // deleteAll does: then this thread must publish first).
                flusherAtPublish.countDown();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                while (deleteAllReturned.await(10, TimeUnit.MILLISECONDS) == false
                    && waitsInForcePurge(deleteAllThread.get()) == false) {
                  assertTrue("deleteAll did not finish", System.nanoTime() < deadline);
                }
              }
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return "DWPT".equals(component) || "IW".equals(component);
          }

          @Override
          public void close() {}
        };

    IndexWriterConfig iwc =
        new IndexWriterConfig(new MockAnalyzer(random()))
            .setMaxBufferedDocs(2)
            .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setMergeScheduler(new SerialMergeScheduler())
            .setInfoStream(hook);
    IndexWriter w = new IndexWriter(dir, iwc);
    writerRef.set(w);
    createThreeDwpts(w); // three DWPTs with one document each

    // The 4th document makes one DWPT reach maxBufferedDocs=2, so this thread flushes it.
    armFlush.set(true);
    long addSeqNo = Math.abs(w.addDocument(doc("last", new StringReader("d e f"))));
    assertFalse("flush hook did not run", armFlush.get());
    assertFalse("abort hook did not run", armAbort.get());
    assertFalse("publish hook did not run", armPublish.get());
    deleteAllThread.get().join(30_000);
    assertNull(err.get());

    return new long[] {addSeqNo, deleteAllSeqNo.get()};
  }

  public void testDeleteAllWhileASegmentFlushIsRunning() throws Exception {
    Directory dir = newDirectory();
    AtomicReference<IndexWriter> writerRef = new AtomicReference<>();
    long[] seqNos = raceAddFlushWithDeleteAll(dir, writerRef);
    IndexWriter w = writerRef.get();
    assertTrue(
        "addDocument seqNo " + seqNos[0] + " < deleteAll seqNo " + seqNos[1], seqNos[0] < seqNos[1]);
    w.commit();
    // Every add returned a seqNo below deleteAll's, so nothing may survive deleteAll.
    assertEquals("documents surviving deleteAll", 0, committedNumDocs(dir));
    w.close();
    dir.close();
  }

  /**
   * deleteAll is documented as equivalent to re-creating the index (field schema dropped), so a
   * field name may be reused with another schema afterwards. The resurrected segment keeps the old
   * schema: the next merge fails, closing the writer, and the commit cannot be opened by a new
   * IndexWriter.
   */
  public void testFieldReusedWithNewSchemaAfterDeleteAll() throws Exception {
    Directory dir = newDirectory();
    AtomicReference<IndexWriter> writerRef = new AtomicReference<>();
    long[] seqNos = raceAddFlushWithDeleteAll(dir, writerRef);
    assertTrue(seqNos[0] < seqNos[1]);
    IndexWriter w = writerRef.get();
    Document d = new Document();
    d.add(new NumericDocValuesField("id", 7)); // "id" was an indexed StringField before deleteAll
    w.addDocument(d);
    w.commit();
    w.getConfig().setMergePolicy(new TieredMergePolicy());
    Throwable mergeFailure = null;
    try {
      w.forceMerge(1);
    } catch (Throwable t) {
      mergeFailure = t;
    }
    Throwable tragedy = w.getTragicException();
    if (w.isOpen()) {
      w.close();
    }
    Throwable reopenFailure = null;
    try {
      new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random()))).close();
    } catch (Throwable t) {
      reopenFailure = t;
    }
    dir.close();
    assertNull(
        "merge after deleteAll failed (writer tragedy: "
            + tragedy
            + "; reopening with a new IndexWriter: "
            + reopenFailure
            + ")",
        mergeFailure);
    assertNull("committed index cannot be opened by a new IndexWriter", reopenFailure);
  }

  /** Control: the same operations without the forced interleaving. */
  public void testControlSequential() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig(new MockAnalyzer(random()))
            .setMaxBufferedDocs(2)
            .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            .setMergePolicy(NoMergePolicy.INSTANCE);
    IndexWriter w = new IndexWriter(dir, iwc);
    createThreeDwpts(w);
    w.addDocument(doc("last", new StringReader("d e f")));
    w.deleteAll();
    w.commit();
    assertEquals(0, committedNumDocs(dir));
    w.close();
    dir.close();
  }
}
