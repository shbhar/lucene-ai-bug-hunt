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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.InfoStream;

/**
 * IndexWriter's sequence-number contract (class javadoc, "sequence_number" anchor; commit() and
 * prepareCommit() javadoc) must also hold for addIndexes when it runs concurrently with other
 * operations:
 *
 * <ul>
 *   <li>a delete whose sequence number is smaller than the one addIndexes returns is ordered before
 *       addIndexes, so it must not delete the documents that addIndexes adds;
 *   <li>a commit contains exactly the operations whose sequence number is &lt;= the one commit
 *       returns.
 * </ul>
 *
 * <p>Only public API is used. The interleaving is forced deterministically through an {@link
 * InfoStream} that parks a thread on a message it logs (no sleeps): the addIndexes thread parks
 * after its initial flush and before it publishes the added segments.
 */
public class TestAddIndexesSeqNoContract extends LuceneTestCase {

  /** Runs an action the first time a given thread logs a message with a given prefix. */
  private static final class ParkingInfoStream extends InfoStream {
    private record Hook(
        Thread thread, String component, String prefix, Runnable action, AtomicBoolean fired) {}

    private final List<Hook> hooks = new CopyOnWriteArrayList<>();

    AtomicBoolean on(Thread thread, String component, String prefix, Runnable action) {
      AtomicBoolean fired = new AtomicBoolean();
      hooks.add(new Hook(thread, component, prefix, action, fired));
      return fired;
    }

    @Override
    public void message(String component, String message) {
      for (Hook h : hooks) {
        if (h.thread() == Thread.currentThread()
            && h.component().equals(component)
            && message.startsWith(h.prefix())
            && h.fired().compareAndSet(false, true)) {
          h.action().run();
          return;
        }
      }
    }

    @Override
    public boolean isEnabled(String component) {
      return true;
    }

    @Override
    public void close() {}
  }

  private interface IOCall {
    long call() throws IOException;
  }

  private static IndexWriterConfig config(InfoStream infoStream) {
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    // addIndexes(CodecReader...) merges in the calling thread, so its messages come from that
    // thread
    iwc.setMergeScheduler(new SerialMergeScheduler());
    iwc.setCommitOnClose(false);
    if (infoStream != null) {
      iwc.setInfoStream(infoStream);
    }
    return iwc;
  }

  private static Document doc(String id, long value) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.NO));
    doc.add(new NumericDocValuesField("val", value));
    return doc;
  }

  /** A committed index with one document: id=k, val=100. */
  private static Directory sourceIndex() throws IOException {
    Directory src = newDirectory();
    try (IndexWriter w = new IndexWriter(src, config(null))) {
      w.addDocument(doc("k", 100));
      w.commit();
    }
    return src;
  }

  /** Sorted "val" values of all live documents. */
  private static List<Long> liveValues(DirectoryReader reader) throws IOException {
    List<Long> values = new ArrayList<>();
    for (LeafReaderContext ctx : reader.leaves()) {
      Bits liveDocs = ctx.reader().getLiveDocs();
      NumericDocValues dv = ctx.reader().getNumericDocValues("val");
      for (int doc = 0; doc < ctx.reader().maxDoc(); doc++) {
        if (liveDocs == null || liveDocs.get(doc)) {
          assertTrue(dv.advanceExact(doc));
          values.add(dv.longValue());
        }
      }
    }
    values.sort(null);
    return values;
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue("latch timeout", latch.await(60, TimeUnit.SECONDS));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Starts {@code addIndexes} in a new thread, parks it on its own message {@code parkPrefix}, runs
   * {@code op} in this thread, then lets addIndexes finish. Returns {op seqNo, addIndexes seqNo}.
   */
  private static long[] runWhileAddIndexesIsParked(
      ParkingInfoStream infoStream, String parkPrefix, IOCall addIndexes, IOCall op)
      throws Exception {
    CountDownLatch parked = new CountDownLatch(1);
    CountDownLatch go = new CountDownLatch(1);
    AtomicLong addIndexesSeqNo = new AtomicLong();
    AtomicReference<Throwable> error = new AtomicReference<>();
    Thread t =
        new Thread(
            () -> {
              try {
                addIndexesSeqNo.set(addIndexes.call());
              } catch (Throwable e) {
                error.set(e);
              }
            });
    AtomicBoolean fired =
        infoStream.on(
            t,
            "IW",
            parkPrefix,
            () -> {
              parked.countDown();
              await(go);
            });
    long opSeqNo;
    t.start();
    try {
      await(parked);
      opSeqNo = op.call();
    } finally {
      go.countDown();
      t.join();
    }
    if (error.get() != null) {
      throw new AssertionError(error.get());
    }
    assertTrue("addIndexes parked on '" + parkPrefix + "'", fired.get());
    return new long[] {opSeqNo, addIndexesSeqNo.get()};
  }

  /** addIndexes(Directory...): a delete with a smaller seqNo deletes the added document. */
  public void testDeleteWithSmallerSeqNoMustNotDeleteDocAddedByAddIndexesDirectory()
      throws Exception {
    try (Directory src = sourceIndex();
        Directory dir = newDirectory()) {
      ParkingInfoStream infoStream = new ParkingInfoStream();
      try (IndexWriter w = new IndexWriter(dir, config(infoStream))) {
        w.addDocument(doc("k", 1));
        w.commit();
        long[] seqNos =
            runWhileAddIndexesIsParked(
                infoStream,
                "addIndexes: process directory",
                () -> w.addIndexes(src),
                () -> w.deleteDocuments(new Term("id", "k")));
        assertDeleteOrderedBeforeAddIndexes(w, seqNos[0], seqNos[1]);
      }
    }
  }

  /** addIndexes(CodecReader...): same as above. */
  public void testDeleteWithSmallerSeqNoMustNotDeleteDocAddedByAddIndexesCodecReader()
      throws Exception {
    try (Directory src = sourceIndex();
        DirectoryReader srcReader = DirectoryReader.open(src);
        Directory dir = newDirectory()) {
      CodecReader[] leaves =
          srcReader.leaves().stream()
              .map(ctx -> (CodecReader) ctx.reader())
              .toArray(CodecReader[]::new);
      ParkingInfoStream infoStream = new ParkingInfoStream();
      try (IndexWriter w = new IndexWriter(dir, config(infoStream))) {
        w.addDocument(doc("k", 1));
        w.commit();
        long[] seqNos =
            runWhileAddIndexesIsParked(
                infoStream,
                "merged new segment",
                () -> w.addIndexes(leaves),
                () -> w.deleteDocuments(new Term("id", "k")));
        assertDeleteOrderedBeforeAddIndexes(w, seqNos[0], seqNos[1]);
      }
    }
  }

  private static void assertDeleteOrderedBeforeAddIndexes(
      IndexWriter w, long deleteSeqNo, long addIndexesSeqNo) throws IOException {
    assertTrue(
        "delete seqNo " + deleteSeqNo + " < addIndexes seqNo " + addIndexesSeqNo,
        deleteSeqNo < addIndexesSeqNo);
    try (DirectoryReader r = DirectoryReader.open(w)) {
      // delete(id:k) comes first and removes only the pre-existing doc (val=1); addIndexes then
      // adds the doc with val=100.
      assertEquals(
          "delete (seqNo "
              + deleteSeqNo
              + ") is ordered before addIndexes (seqNo "
              + addIndexesSeqNo
              + "), live values",
          List.of(100L),
          liveValues(r));
    }
  }

  /** A commit point contains an addIndexes whose seqNo is larger than the commit's seqNo. */
  public void testCommitMustNotContainAddIndexesWithLargerSeqNo() throws Exception {
    try (Directory src = sourceIndex();
        Directory dir = newDirectory()) {
      ParkingInfoStream infoStream = new ParkingInfoStream();
      try (IndexWriter w = new IndexWriter(dir, config(infoStream))) {
        w.addDocument(doc("x", 1));
        w.commit();

        CountDownLatch parked = new CountDownLatch(1);
        CountDownLatch go = new CountDownLatch(1);
        AtomicLong addIndexesSeqNo = new AtomicLong();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t =
            new Thread(
                () -> {
                  try {
                    addIndexesSeqNo.set(w.addIndexes(src));
                  } catch (Throwable e) {
                    error.set(e);
                  }
                });
        infoStream.on(
            t,
            "IW",
            "addIndexes: process directory",
            () -> {
              parked.countDown();
              await(go);
            });
        long commitSeqNo;
        AtomicBoolean commitHookFired;
        t.start();
        try {
          await(parked); // addIndexes has done its initial flush and has not published yet
          w.addDocument(doc("y", 2)); // a pending change, so the commit has something to flush

          // Once commit has done its full flush (its seqNo is fixed), let addIndexes finish. A
          // writer that orders addIndexes after this commit would block it here, so wait at most
          // 10 s and then let the commit go on.
          commitHookFired =
              infoStream.on(
                  Thread.currentThread(),
                  "IW",
                  "now apply all deletes",
                  () -> {
                    go.countDown();
                    try {
                      t.join(10_000);
                    } catch (InterruptedException e) {
                      throw new RuntimeException(e);
                    }
                  });
          commitSeqNo = w.commit();
        } finally {
          go.countDown();
          t.join();
        }
        if (error.get() != null) {
          throw new AssertionError(error.get());
        }
        assertTrue("commit hook fired", commitHookFired.get());

        try (DirectoryReader committed = DirectoryReader.open(dir)) {
          List<Long> values = liveValues(committed);
          assertTrue(
              "the commit contains the docs added before it: " + values,
              values.contains(1L) && values.contains(2L));
          // commit() javadoc: "All sequence numbers <= this value will be reflected in the
          // commit, and all others will not."
          assertEquals(
              "commit seqNo "
                  + commitSeqNo
                  + ", addIndexes seqNo "
                  + addIndexesSeqNo.get()
                  + ": commit contains the added doc",
              addIndexesSeqNo.get() <= commitSeqNo,
              values.contains(100L));
        }
      }
    }
  }

  /** Control: the sequential case (fixed long ago) keeps the added document. */
  public void testControlSequentialDeleteThenAddIndexes() throws Exception {
    try (Directory src = sourceIndex();
        Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config(null))) {
      w.addDocument(doc("k", 1));
      w.commit();
      long deleteSeqNo = w.deleteDocuments(new Term("id", "k"));
      long addIndexesSeqNo = w.addIndexes(src);
      assertDeleteOrderedBeforeAddIndexes(w, deleteSeqNo, addIndexesSeqNo);
    }
  }
}
