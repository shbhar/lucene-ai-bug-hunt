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
 * When a document makes its per-thread buffer flush-pending, DocumentsWriterFlushControl checks the
 * buffer out for flushing (adds it to its flushing set) and then, in a finally block, updates its
 * stall state, which asks the InfoStream (public extension point) isEnabled("DWFC"). If that call
 * throws, the checked-out buffer is never handed to a flushing thread and never leaves the flushing
 * set, so every later wait for flushes to finish waits forever.
 *
 * <p>Public API only; default IndexWriterConfig except maxBufferedDocs=2 and the InfoStream.
 */
public class TestFlushCheckoutStrandedByLogging extends LuceneTestCase {

  private static final class ThrowOnceOnStallCheck extends InfoStream {
    final AtomicBoolean armed = new AtomicBoolean();
    final Throwable toThrow;

    ThrowOnceOnStallCheck(Throwable toThrow) {
      this.toThrow = toThrow;
    }

    @Override
    public void message(String component, String message) {}

    @Override
    public boolean isEnabled(String component) {
      if ("DWFC".equals(component) && armed.compareAndSet(true, false)) {
        if (toThrow instanceof Error e) throw e;
        throw (RuntimeException) toThrow;
      }
      return false;
    }

    @Override
    public void close() {}
  }

  private static Document doc(int i) {
    Document d = new Document();
    d.add(new StringField("id", Integer.toString(i), Field.Store.YES));
    return d;
  }

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
          if (e.getClassName().startsWith("org.apache.lucene.index.")) {
            where.append("\n    at ").append(e);
          }
        }
        f.cancel(true);
        throw new AssertionError(what + " did not return within 30 s; stuck in:" + where);
      }
    } finally {
      ex.shutdownNow();
      ex.awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  /** IndexWriter class javadoc: "if you hit an Error ... IndexWriter will close itself". */
  public void testErrorWhileCheckingOutFlushClosesWriter() throws Exception {
    OutOfMemoryError oome = new OutOfMemoryError("simulated allocation failure while logging");
    ThrowOnceOnStallCheck infoStream = new ThrowOnceOnStallCheck(oome);
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w =
          new IndexWriter(
              dir,
              new IndexWriterConfig(new MockAnalyzer(random()))
                  .setMaxBufferedDocs(2)
                  .setInfoStream(infoStream));
      w.addDocument(doc(0));
      infoStream.armed.set(true);
      // the second document makes the buffer flush-pending; it is checked out for flushing
      Throwable t = callWithin("addDocument", () -> w.addDocument(doc(1)));
      assertSame("addDocument must rethrow the Error", oome, t);
      assertFalse("IndexWriter must close itself after an Error", w.isOpen());
    }
  }

  /** With an unchecked exception the writer stays open; close() must still return. */
  public void testExceptionWhileCheckingOutFlushThenCloseReturns() throws Exception {
    IllegalStateException ise = new IllegalStateException("logging backend failed");
    ThrowOnceOnStallCheck infoStream = new ThrowOnceOnStallCheck(ise);
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w =
          new IndexWriter(
              dir,
              new IndexWriterConfig(new MockAnalyzer(random()))
                  .setMaxBufferedDocs(2)
                  .setInfoStream(infoStream));
      w.addDocument(doc(0));
      infoStream.armed.set(true);
      Throwable t = callWithin("addDocument", () -> w.addDocument(doc(1)));
      assertSame(ise, t);
      assertTrue(w.isOpen());
      Throwable closeResult = callWithin("IndexWriter.close()", w::close);
      assertNull("close() should commit and return: " + closeResult, closeResult);
    }
  }
}
