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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Two tasks of one TaskExecutor#invokeAll call fail with the same Throwable instance. That happens
 * whenever the same exception object is thrown from two slices: the JVM itself reuses one
 * preallocated NullPointerException (ArrayIndexOutOfBoundsException, ClassCastException, ...) for
 * an implicit exception that a hot, compiled code site throws repeatedly (HotSpot's default
 * OmitStackTraceInFastThrow), and code may also throw a preallocated exception.
 *
 * <p>invokeAll's javadoc: "wait for them to complete and return the obtained results. If an
 * exception is thrown by more than one callable, the subsequent ones will be added as suppressed
 * exceptions to the first one that was caught." So the caller must get that exception, after every
 * task has completed.
 */
public class TestTaskExecutorSameExceptionTwice extends LuceneTestCase {

  /**
   * An Executor that starts each runnable on its own thread and returns only once the task that
   * runnable picked has started (the callables release {@code started}), so that tasks 0, 1 and 2
   * run on these threads and the calling thread runs task 3.
   */
  private static Executor startEachTaskOnItsOwnThread(Semaphore started, List<Thread> threads) {
    return r -> {
      Thread t = new Thread(r, "TestTaskExecutorSameExceptionTwice-" + threads.size());
      threads.add(t);
      t.start();
      try {
        assertTrue(started.tryAcquire(30, TimeUnit.SECONDS));
      } catch (InterruptedException e) {
        throw new AssertionError(e);
      }
    };
  }

  public void testTwoTasksThrowTheSameException() throws Exception {
    RuntimeException shared = new RuntimeException("thrown by two tasks");
    Semaphore started = new Semaphore(0);
    List<Thread> threads = new ArrayList<>();
    // tasks 0, 1 and 2 have all started before tasks 0 and 1 throw
    CyclicBarrier allStarted = new CyclicBarrier(3);
    CountDownLatch releaseSlowTask = new CountDownLatch(1);
    AtomicBoolean slowTaskFinished = new AtomicBoolean();
    TaskExecutor taskExecutor = new TaskExecutor(startEachTaskOnItsOwnThread(started, threads));

    List<Callable<Integer>> tasks = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      tasks.add(
          () -> {
            started.release();
            allStarted.await(30, TimeUnit.SECONDS);
            throw shared;
          });
    }
    tasks.add(
        () -> {
          started.release();
          allStarted.await(30, TimeUnit.SECONDS);
          // released by the test once invokeAll has returned; a correct invokeAll waits for this
          // task, which then gives up after 5 seconds and completes
          releaseSlowTask.await(5, TimeUnit.SECONDS);
          slowTaskFinished.set(true);
          return 2;
        });
    tasks.add(() -> 3);

    Throwable thrown = null;
    try {
      taskExecutor.invokeAll(tasks);
    } catch (Throwable t) {
      thrown = t;
    }
    boolean slowTaskStillRunningWhenInvokeAllReturned = slowTaskFinished.get() == false;
    releaseSlowTask.countDown();
    for (Thread t : threads) t.join();

    assertNotNull("invokeAll did not throw", thrown);
    assertFalse(
        "invokeAll returned while a task was still running (it threw " + thrown + ")",
        slowTaskStillRunningWhenInvokeAllReturned);
    assertSame("invokeAll threw " + thrown + " instead of the tasks' exception", shared, thrown);
  }

  /**
   * The same through IndexSearcher: a query whose scorer throws one preallocated exception on two
   * segments, searched with an executor (one segment per slice).
   */
  public void testSearchWhoseSlicesThrowTheSameException() throws Exception {
    IllegalStateException shared = new IllegalStateException("thrown from two segments");
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig(new MockAnalyzer(random())).setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int s = 0; s < 2; s++) {
          Document doc = new Document();
          doc.add(new StringField("f", "v", Field.Store.NO));
          w.addDocument(doc);
          w.flush();
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Semaphore started = new Semaphore(0);
        List<Thread> threads = new ArrayList<>();
        CyclicBarrier bothSegmentsStarted = new CyclicBarrier(2);
        Executor executor = startEachTaskOnItsOwnThread(started, threads);
        IndexSearcher searcher =
            new IndexSearcher(reader, executor) {
              @Override
              protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
                return slices(leaves, 1, 1, false); // one segment per slice
              }
            };
        searcher.setQueryCache(null);
        Query q =
            new Query() {
              @Override
              public Weight createWeight(IndexSearcher s, ScoreMode scoreMode, float boost) {
                return new ConstantScoreWeight(this, boost) {
                  @Override
                  public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
                    // the slice that runs on the calling thread does not signal: only executor
                    // threads wait for an acquire in startEachTaskOnItsOwnThread
                    if (threads.contains(Thread.currentThread())) started.release();
                    try {
                      bothSegmentsStarted.await(30, TimeUnit.SECONDS);
                    } catch (Exception e) {
                      throw new AssertionError(e);
                    }
                    throw shared;
                  }

                  @Override
                  public boolean isCacheable(LeafReaderContext ctx) {
                    return false;
                  }
                };
              }

              @Override
              public String toString(String field) {
                return "ThrowsSharedException";
              }

              @Override
              public void visit(QueryVisitor visitor) {}

              @Override
              public boolean equals(Object other) {
                return this == other;
              }

              @Override
              public int hashCode() {
                return System.identityHashCode(this);
              }
            };
        Throwable thrown = null;
        try {
          searcher.search(q, 10);
        } catch (Throwable t) {
          thrown = t;
        }
        for (Thread t : threads) t.join();
        assertSame("search threw " + thrown + " instead of the scorer's exception", shared, thrown);
      }
    }
  }
}
