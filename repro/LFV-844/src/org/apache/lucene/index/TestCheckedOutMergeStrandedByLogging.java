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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

/**
 * IndexWriter's merge source checks a merge out (pending -> running) and only then logs "checked
 * out merge" to the merge scheduler's InfoStream. If that logging throws, nothing runs or finishes
 * the checked-out merge, so it stays in the running set forever.
 *
 * <p>Uses only public API: an InfoStream (public extension point) enabled for "MS", the default
 * ConcurrentMergeScheduler, and LogDocMergePolicy so that the second flushed segment triggers a
 * merge.
 */
public class TestCheckedOutMergeStrandedByLogging extends LuceneTestCase {

  private static final class ThrowOnceAtCheckout extends InfoStream {
    final AtomicBoolean armed = new AtomicBoolean();
    final Throwable toThrow;

    ThrowOnceAtCheckout(Throwable toThrow) {
      this.toThrow = toThrow;
    }

    @Override
    public void message(String component, String message) {
      if ("MS".equals(component)
          && message.contains("checked out merge")
          && armed.compareAndSet(true, false)) {
        if (toThrow instanceof Error e) throw e;
        throw (RuntimeException) toThrow;
      }
    }

    @Override
    public boolean isEnabled(String component) {
      return "MS".equals(component);
    }

    @Override
    public void close() {}
  }

  private static Document doc(int i) {
    Document d = new Document();
    d.add(new StringField("id", Integer.toString(i), Field.Store.YES));
    return d;
  }

  private static IndexWriterConfig config(InfoStream infoStream) {
    LogDocMergePolicy mp = new LogDocMergePolicy();
    mp.setMergeFactor(2);
    return new IndexWriterConfig(new MockAnalyzer(random()))
        .setMaxBufferedDocs(2)
        .setMergePolicy(mp)
        .setInfoStream(infoStream);
    // merge scheduler: the default ConcurrentMergeScheduler
  }

  /** Runs the call on another thread; fails (and unblocks it) if it does not return in time. */
  private static Throwable callWithin(String what, ThrowingRunnable r) throws Exception {
    ExecutorService ex = Executors.newSingleThreadExecutor();
    Thread[] worker = new Thread[1];
    try {
      Future<Throwable> f =
          ex.submit(
              () -> {
                worker[0] = Thread.currentThread();
                try {
                  r.run();
                  return null;
                } catch (Throwable t) {
                  return t;
                }
              });
      try {
        return f.get(30, TimeUnit.SECONDS);
      } catch (TimeoutException te) {
        StringBuilder where = new StringBuilder();
        for (StackTraceElement e : worker[0].getStackTrace()) {
          if (e.getClassName().startsWith("org.apache.lucene.index.IndexWriter")) {
            where.append("\n    at ").append(e);
          }
        }
        f.cancel(true); // interrupt the stuck call so the test can finish
        throw new AssertionError(what + " did not return within 30 s; stuck in:" + where);
      }
    } finally {
      ex.shutdownNow();
      ex.awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  /**
   * IndexWriter class javadoc: "if you hit an Error ... then IndexWriter will close itself". Here
   * the Error is thrown while the merge that the second flush selected is being checked out; the
   * writer's tragic rollback then waits forever for that merge to finish.
   */
  public void testErrorAtMergeCheckoutClosesWriter() throws Exception {
    OutOfMemoryError oome = new OutOfMemoryError("simulated allocation failure while logging");
    ThrowOnceAtCheckout infoStream = new ThrowOnceAtCheckout(oome);
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, config(infoStream));
      w.addDocument(doc(0));
      w.addDocument(doc(1)); // first segment
      w.addDocument(doc(2));
      infoStream.armed.set(true);
      // the fourth document flushes the second segment, which makes LogDocMergePolicy select a merge
      Throwable t = callWithin("addDocument", () -> w.addDocument(doc(3)));
      assertSame("addDocument must rethrow the Error", oome, t);
      assertFalse("IndexWriter must close itself after an Error", w.isOpen());
      assertSame(oome, w.getTragicException());
    }
  }

  /**
   * The same window with an unchecked exception from the logger: the call fails and the writer
   * stays open (expected for a non-Error), but close() must still shut it down.
   */
  public void testExceptionAtMergeCheckoutThenCloseReturns() throws Exception {
    IllegalStateException ise = new IllegalStateException("logging backend failed");
    ThrowOnceAtCheckout infoStream = new ThrowOnceAtCheckout(ise);
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, config(infoStream));
      w.addDocument(doc(0));
      w.addDocument(doc(1));
      w.addDocument(doc(2));
      infoStream.armed.set(true);
      Throwable t = callWithin("addDocument", () -> w.addDocument(doc(3)));
      assertSame(ise, t);
      assertTrue(w.isOpen());
      Throwable closeResult = callWithin("IndexWriter.close()", w::close);
      assertNull("close() should commit and return", closeResult);
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        // the failing addDocument indexed its document before the merge step failed
        assertTrue("numDocs=" + r.numDocs(), r.numDocs() == 3 || r.numDocs() == 4);
      }
    }
  }
}
