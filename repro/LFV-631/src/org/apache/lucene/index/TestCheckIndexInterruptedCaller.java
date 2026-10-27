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

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * CheckIndex with an executor catches the InterruptedException of an interrupted caller, prints
 * "ERROR: Interrupted exception occurred when getting segment check result", and then passes the
 * null segment status to processSegmentInfoStatusResult, which throws NullPointerException.
 */
public class TestCheckIndexInterruptedCaller extends LuceneTestCase {

  private static Directory twoSegmentIndex() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int s = 0; s < 2; s++) {
        for (int i = 0; i < 10; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", s + "-" + i, Field.Store.YES));
          w.addDocument(doc);
        }
        w.commit();
      }
    }
    return dir;
  }

  /** Runs each task on a pool thread, but only after the gate opens. */
  private static final class GatedExecutor extends AbstractExecutorService {
    final ExecutorService delegate = Executors.newSingleThreadExecutor();
    final CountDownLatch gate = new CountDownLatch(1);

    @Override
    public void execute(Runnable command) {
      delegate.execute(
          () -> {
            try {
              gate.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            command.run();
          });
    }

    @Override
    public void shutdown() {
      delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
      return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
      return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
      return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return delegate.awaitTermination(timeout, unit);
    }
  }

  /** Deterministic: the segment checks have not finished when the interrupted caller waits. */
  public void testInterruptedCallerWithExecutor() throws Exception {
    GatedExecutor executor = new GatedExecutor();
    try (Directory dir = twoSegmentIndex();
        CheckIndex checker = new CheckIndex(dir)) {
      Thread.currentThread().interrupt();
      try {
        checker.checkIndex(null, executor);
      } catch (NullPointerException e) {
        throw new AssertionError("interrupted CheckIndex threw NullPointerException", e);
      } catch (RuntimeException e) {
        // an interrupt-related exception (e.g. ThreadInterruptedException) is fine
      } finally {
        Thread.interrupted();
        executor.gate.countDown();
      }
    } finally {
      executor.shutdown();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
  }
}
