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
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

/**
 * A failed NRT reopen (IndexWriter#getReader) must not discard documents that other threads indexed
 * concurrently and that were already acknowledged with a sequence number.
 *
 * <p>While a full flush runs, a DocumentsWriterPerThread (DWPT) that reaches its flush trigger is
 * parked in DocumentsWriterFlushControl#blockedFlushes until the full flush ends. If getReader then
 * fails with a non-tragic exception after DocumentsWriter#flushAllThreads, getReader calls
 * DocumentsWriter#finishFullFlush(false) -> DocumentsWriterFlushControl#abortFullFlushes, which aborts
 * the blocked DWPTs. Their documents belong to operations that started after the reader's point in
 * time and already returned; they are dropped silently while the writer stays open.
 *
 * <p>Uses only public API (IndexWriterConfig#setInfoStream as a scheduling hook, MockDirectoryWrapper
 * failOn, a FilterMergePolicy) and a plain helper thread joined from the hook.
 */
public class TestGetReaderFailureDropsConcurrentDocs extends LuceneTestCase {

  private static Document doc(String id, String v) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new StringField("v", v, Field.Store.YES));
    return d;
  }

  private static Set<String> committedIdsAndValues(Directory dir) throws IOException {
    Set<String> out = new TreeSet<>();
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      StoredFields sf = r.storedFields();
      for (LeafReaderContext ctx : r.leaves()) {
        for (int i = 0; i < ctx.reader().maxDoc(); i++) {
          if (ctx.reader().getLiveDocs() == null || ctx.reader().getLiveDocs().get(i)) {
            Document d = sf.document(ctx.docBase + i);
            out.add(d.get("id") + "=" + d.get("v"));
          }
        }
      }
    }
    return out;
  }

  /** Runs the concurrent indexing operations on another thread and waits for them to return. */
  private static void indexConcurrently(
      IndexWriter w, AtomicLong maxConcurrentSeqNo, AtomicReference<Throwable> error)
      throws InterruptedException {
    Thread t =
        new Thread(
            () -> {
              try {
                // updateDocument opens a new DWPT on the new delete queue (1 doc) ...
                long a = w.updateDocument(new Term("id", "0"), doc("0", "new"));
                // ... and the second document reaches maxBufferedDocs=2: the DWPT becomes flush
                // pending during the full flush and is moved to blockedFlushes.
                long b = w.addDocument(doc("2", "x"));
                maxConcurrentSeqNo.set(Math.max(Math.abs(a), Math.abs(b)));
              } catch (Throwable e) {
                error.set(e);
              }
            },
            "concurrent-indexer");
    t.start();
    t.join(TimeUnit.SECONDS.toMillis(30));
    assertFalse("concurrent indexer did not finish", t.isAlive());
  }

  private IndexWriterConfig baseConfig() {
    return new IndexWriterConfig(new MockAnalyzer(random()))
        .setMaxBufferedDocs(2)
        .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH)
        .setMergePolicy(NoMergePolicy.INSTANCE)
        .setCommitOnClose(false);
  }

  private void assertNothingLost(IndexWriter w, Directory dir, AtomicLong concurrentSeqNo)
      throws IOException {
    assertNull("writer must stay open after a non-tragic failure", w.getTragicException());
    assertTrue(w.isOpen());
    long commitSeqNo = w.commit();
    assertTrue(
        "commit seqNo " + commitSeqNo + " must be above the concurrent ops' " + concurrentSeqNo.get(),
        commitSeqNo > concurrentSeqNo.get());
    // The concurrent updateDocument(id:0 -> new) and addDocument(id:2) returned with seqNos below
    // the commit's, so the commit must contain both (and id:1 from before the failed reopen).
    assertEquals(Set.of("0=new", "1=x", "2=x"), committedIdsAndValues(dir));
  }

  /** The NRT reader cannot be opened because of an I/O error (here: simulated by failOn). */
  public void testIOExceptionWhileOpeningNrtReader() throws Exception {
    MockDirectoryWrapper dir = newMockDirectory();
    AtomicBoolean armHook = new AtomicBoolean(false);
    AtomicBoolean armFailure = new AtomicBoolean(false);
    AtomicLong concurrentSeqNo = new AtomicLong(-1);
    AtomicReference<Throwable> error = new AtomicReference<>();
    AtomicReference<IndexWriter> writerRef = new AtomicReference<>();
    Thread mainThread = Thread.currentThread();

    dir.failOn(
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper d) throws IOException {
            if (armFailure.get()
                && Thread.currentThread() == mainThread
                && callStackContains(StandardDirectoryReader.class, "open")
                && armFailure.compareAndSet(true, false)) {
              throw new IOException("simulated I/O error while opening the NRT reader");
            }
          }
        });

    InfoStream hook =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            // DocumentsWriterPerThread#flush of the old-queue DWPT inside flushAllThreads: the full
            // flush has started (markForFullFlush done) on this thread.
            if (Thread.currentThread() == mainThread
                && "DWPT".equals(component)
                && message.startsWith("flush postings as segment")
                && armHook.compareAndSet(true, false)) {
              try {
                indexConcurrently(writerRef.get(), concurrentSeqNo, error);
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
              armFailure.set(true);
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return "DWPT".equals(component);
          }

          @Override
          public void close() {}
        };

    IndexWriter w = new IndexWriter(dir, baseConfig().setInfoStream(hook));
    writerRef.set(w);
    w.addDocument(doc("0", "old"));
    w.commit();
    w.addDocument(doc("1", "x")); // buffered: flushed by the reopen below

    armHook.set(true);
    expectThrows(IOException.class, () -> DirectoryReader.open(w).close());
    assertFalse("hook did not run", armHook.get());
    assertFalse("I/O failure was not injected", armFailure.get());
    assertNull(error.get());
    assertNothingLost(w, dir, concurrentSeqNo);
    w.close();
    dir.close();
  }

  /** A merge policy throws IOException from findFullFlushMerges(GET_READER, ...). */
  public void testMergePolicyIOExceptionDuringGetReader() throws Exception {
    Directory dir = newDirectory();
    AtomicBoolean arm = new AtomicBoolean(false);
    AtomicLong concurrentSeqNo = new AtomicLong(-1);
    AtomicReference<Throwable> error = new AtomicReference<>();
    AtomicReference<IndexWriter> writerRef = new AtomicReference<>();

    MergePolicy mp =
        new FilterMergePolicy(NoMergePolicy.INSTANCE) {
          @Override
          public MergeSpecification findFullFlushMerges(
              MergeTrigger trigger, SegmentInfos infos, MergeContext ctx) throws IOException {
            if (trigger == MergeTrigger.GET_READER && arm.compareAndSet(true, false)) {
              try {
                indexConcurrently(writerRef.get(), concurrentSeqNo, error);
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
              throw new IOException("simulated merge policy failure");
            }
            return null;
          }
        };

    IndexWriter w =
        new IndexWriter(dir, baseConfig().setMergePolicy(mp).setMaxFullFlushMergeWaitMillis(1000));
    writerRef.set(w);
    w.addDocument(doc("0", "old"));
    w.commit();
    w.addDocument(doc("1", "x"));

    arm.set(true);
    expectThrows(IOException.class, () -> DirectoryReader.open(w).close());
    assertFalse("merge policy hook did not run", arm.get());
    assertNull(error.get());
    assertNothingLost(w, dir, concurrentSeqNo);
    w.close();
    dir.close();
  }

  /**
   * The same abort through commit: publishing a fully deleted flushed segment consults
   * MergePolicy#keepFullyDeletedSegment inside publishFlushedSegments, which prepareCommitInternal
   * runs before it sets its {@code flushed} flag; an IOException there makes commit call
   * finishFullFlush(false) as well.
   */
  public void testMergePolicyIOExceptionWhilePublishingDuringCommit() throws Exception {
    Directory dir = newDirectory();
    AtomicBoolean arm = new AtomicBoolean(false);
    AtomicLong concurrentSeqNo = new AtomicLong(-1);
    AtomicReference<Throwable> error = new AtomicReference<>();
    AtomicReference<IndexWriter> writerRef = new AtomicReference<>();
    MergePolicy mp =
        new FilterMergePolicy(NoMergePolicy.INSTANCE) {
          @Override
          public boolean keepFullyDeletedSegment(
              org.apache.lucene.util.IOSupplier<CodecReader> readerIOSupplier) throws IOException {
            if (arm.compareAndSet(true, false)) {
              try {
                indexConcurrently(writerRef.get(), concurrentSeqNo, error);
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
              throw new IOException("simulated merge policy failure");
            }
            return false;
          }
        };
    IndexWriter w = new IndexWriter(dir, baseConfig().setMergePolicy(mp));
    writerRef.set(w);
    w.addDocument(doc("0", "old"));
    w.addDocument(doc("1", "x"));
    w.commit();
    // a buffered document deleted again: the commit flushes its DWPT as a fully deleted segment
    w.addDocument(doc("tmp", "x"));
    w.deleteDocuments(new Term("id", "tmp"));

    arm.set(true);
    expectThrows(IOException.class, w::commit);
    assertFalse("merge policy hook did not run", arm.get());
    assertNull(error.get());
    assertNothingLost(w, dir, concurrentSeqNo);
    w.close();
    dir.close();
  }

  /** Control: the same interleaving with a successful reopen keeps everything. */
  public void testControlSuccessfulReopen() throws Exception {
    Directory dir = newDirectory();
    AtomicBoolean arm = new AtomicBoolean(false);
    AtomicLong concurrentSeqNo = new AtomicLong(-1);
    AtomicReference<Throwable> error = new AtomicReference<>();
    AtomicReference<IndexWriter> writerRef = new AtomicReference<>();
    MergePolicy mp =
        new FilterMergePolicy(NoMergePolicy.INSTANCE) {
          @Override
          public MergeSpecification findFullFlushMerges(
              MergeTrigger trigger, SegmentInfos infos, MergeContext ctx) throws IOException {
            if (trigger == MergeTrigger.GET_READER && arm.compareAndSet(true, false)) {
              try {
                indexConcurrently(writerRef.get(), concurrentSeqNo, error);
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            }
            return null;
          }
        };
    IndexWriter w =
        new IndexWriter(dir, baseConfig().setMergePolicy(mp).setMaxFullFlushMergeWaitMillis(1000));
    writerRef.set(w);
    w.addDocument(doc("0", "old"));
    w.commit();
    w.addDocument(doc("1", "x"));
    arm.set(true);
    DirectoryReader.open(w).close();
    assertFalse(arm.get());
    assertNull(error.get());
    assertNothingLost(w, dir, concurrentSeqNo);
    w.close();
    dir.close();
  }
}
