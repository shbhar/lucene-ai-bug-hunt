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
package org.apache.lucene.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.OfflineSorter.BufferSize;
import org.apache.lucene.util.OfflineSorter.ByteSequencesWriter;

/**
 * readPartition acquires a partitionsInRAM permit that the SortPartitionTask releases when it
 * finishes. If exec.submit rejects the task (a bounded ThreadPoolExecutor with the default
 * AbortPolicy), sort() fails with RejectedExecutionException, but the permit is never released. The
 * next sort() on the same OfflineSorter then waits forever for a permit, although the executor is
 * idle.
 */
public class TestOfflineSorterRejectedSubmit extends LuceneTestCase {

  /** Capacity of the executor's queue: ample for the sorter's own tasks on this input. */
  private static final int QUEUE = 16;

  private static String writeInput(Directory dir) throws IOException {
    IndexOutput out = dir.createTempOutput("unsorted", "tmp", IOContext.DEFAULT);
    try (ByteSequencesWriter w = new ByteSequencesWriter(out)) {
      byte[] rec = new byte[1000];
      for (int i = 0; i < 1500; i++) {
        rec[0] = (byte) (i >> 8);
        rec[1] = (byte) i;
        w.write(rec);
      }
      CodecUtil.writeFooter(out);
    }
    return out.getName();
  }

  private static boolean blockedInReadPartition(Thread t) {
    if (t.getState() != Thread.State.WAITING) {
      return false;
    }
    for (StackTraceElement e : t.getStackTrace()) {
      if (e.getClassName().equals(OfflineSorter.class.getName())
          && e.getMethodName().equals("readPartition")) {
        return true;
      }
    }
    return false;
  }

  /** Control: without a rejection, the same sorter can be reused on the same pool. */
  public void testControlReuseWithoutRejection() throws Exception {
    try (Directory dir = newDirectory()) {
      String input = writeInput(dir);
      ThreadPoolExecutor exec =
          new ThreadPoolExecutor(
              1,
              1,
              0,
              TimeUnit.MILLISECONDS,
              new ArrayBlockingQueue<>(QUEUE),
              new NamedThreadFactory("sorter"));
      try {
        OfflineSorter sorter =
            new OfflineSorter(
                dir,
                "foo",
                OfflineSorter.DEFAULT_COMPARATOR,
                BufferSize.megabytes(1),
                OfflineSorter.MAX_TEMPFILES,
                -1,
                exec,
                1);
        for (int i = 0; i < 3; i++) {
          dir.deleteFile(sorter.sort(input));
        }
      } finally {
        exec.shutdownNow();
        assertTrue(exec.awaitTermination(1, TimeUnit.MINUTES));
      }
    }
  }

  public void testSortAfterRejectedSubmitDoesNotHang() throws Exception {
    try (Directory dir = newDirectory()) {
      String input = writeInput(dir);
      // one thread, a bounded queue, default AbortPolicy
      ThreadPoolExecutor exec =
          new ThreadPoolExecutor(
              1,
              1,
              0,
              TimeUnit.MILLISECONDS,
              new ArrayBlockingQueue<>(QUEUE),
              new NamedThreadFactory("sorter"));
      try {
        OfflineSorter sorter =
            new OfflineSorter(
                dir,
                "foo",
                OfflineSorter.DEFAULT_COMPARATOR,
                BufferSize.megabytes(1),
                OfflineSorter.MAX_TEMPFILES,
                -1,
                exec,
                1);

        // The pool is saturated by other work (one running task, a full queue), so the sorter's
        // first partition task is rejected.
        CountDownLatch busy = new CountDownLatch(1);
        List<Future<?>> otherWork = new ArrayList<>();
        otherWork.add(
            exec.submit(
                () -> {
                  busy.await();
                  return null;
                }));
        for (int i = 0; i < QUEUE; i++) {
          otherWork.add(exec.submit(() -> null));
        }
        expectThrows(RejectedExecutionException.class, () -> sorter.sort(input));
        busy.countDown();
        for (Future<?> f : otherWork) {
          f.get(); // the other work is done: the pool is idle again
        }

        // Retry the sort with the same sorter.
        AtomicReference<Object> outcome = new AtomicReference<>();
        Thread retry =
            new Thread(
                () -> {
                  try {
                    outcome.set(sorter.sort(input));
                  } catch (Throwable t) {
                    outcome.set(t);
                  }
                });
        retry.start();
        try {
          retry.join(TimeUnit.SECONDS.toMillis(20));
          boolean hung = retry.isAlive();
          boolean waitingForPermit = hung && blockedInReadPartition(retry);
          assertFalse(
              "sort() did not return within 20 s; waiting for a partition permit: "
                  + waitingForPermit
                  + "; executor active="
                  + exec.getActiveCount()
                  + " queued="
                  + exec.getQueue().size(),
              hung);
          assertTrue(String.valueOf(outcome.get()), outcome.get() instanceof String);
          dir.deleteFile((String) outcome.get());
        } finally {
          retry.interrupt(); // release the blocked thread so the test can end
          retry.join();
        }
      } finally {
        exec.shutdownNow();
        assertTrue(exec.awaitTermination(1, TimeUnit.MINUTES));
      }
    }
  }
}
