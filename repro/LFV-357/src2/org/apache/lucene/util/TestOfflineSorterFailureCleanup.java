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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.OfflineSorter.BufferSize;
import org.apache.lucene.util.OfflineSorter.ByteSequencesWriter;

/**
 * OfflineSorter.sort deletes the temp files it created when it fails ("So we can remove any
 * partially written temp files on exception"), but with an ExecutorService it does so while
 * partition-sort tasks it already submitted are still queued or running. Those tasks then create
 * and write temp files after sort() has thrown, and nobody deletes them.
 *
 * <p>Both tests use a single-thread FIFO executor that is busy with unrelated work while sort()
 * runs, so the first partition's sort task is still queued when sort() fails on the second
 * partition. The unrelated work ends as soon as the sorting thread has either finished or is
 * blocked outside of reading the input. No sleeps are used as triggers.
 */
public class TestOfflineSorterFailureCleanup extends LuceneTestCase {

  private static final String PREFIX = "foo";

  /** 1500 records of 1000 bytes (about 1.5 MB: two partitions with a 1 MB buffer). */
  private static String writeInput(Directory dir, boolean truncateLastRecord) throws IOException {
    IndexOutput out = dir.createTempOutput("unsorted", "tmp", IOContext.DEFAULT);
    try (ByteSequencesWriter w = new ByteSequencesWriter(out)) {
      byte[] rec = new byte[1000];
      for (int i = 0; i < 1500; i++) {
        rec[0] = (byte) (i >> 8);
        rec[1] = (byte) i;
        w.write(rec);
      }
      if (truncateLastRecord) {
        // a record header that announces 32767 bytes, followed by only 10 bytes
        out.writeShort(Short.MAX_VALUE);
        out.writeBytes(new byte[10], 10);
      }
      CodecUtil.writeFooter(out);
    }
    return out.getName();
  }

  private static List<String> sorterTempFiles(Directory dir) throws IOException {
    List<String> files = new ArrayList<>();
    for (String f : dir.listAll()) {
      if (f.startsWith(PREFIX)) {
        files.add(f);
      }
    }
    return files;
  }

  private static OfflineSorter newSorter(Directory dir, ExecutorService exec, int maxPartitions) {
    return new OfflineSorter(
        dir,
        PREFIX,
        OfflineSorter.DEFAULT_COMPARATOR,
        BufferSize.megabytes(1),
        OfflineSorter.MAX_TEMPFILES,
        -1,
        exec,
        maxPartitions);
  }

  /** True if t is blocked (WAITING) in a frame of the named OfflineSorter method. */
  private static boolean blockedIn(Thread t, String method) {
    if (t.getState() != Thread.State.WAITING) {
      return false;
    }
    for (StackTraceElement e : t.getStackTrace()) {
      if (e.getClassName().equals(OfflineSorter.class.getName())
          && e.getMethodName().equals(method)) {
        return true;
      }
    }
    return false;
  }

  /** Waits until the sorting thread has ended or is blocked somewhere other than readPartition. */
  private static void awaitSortFailedOrBlockedAfterFailure(Thread sorting) {
    while (sorting.isAlive()
        && (sorting.getState() != Thread.State.WAITING || blockedIn(sorting, "readPartition"))) {
      Thread.onSpinWait();
    }
  }

  /** The input is truncated, so reading the second partition throws. */
  public void testFailedReadLeavesTempFileOfQueuedPartition() throws Exception {
    try (Directory dir = newDirectory()) {
      String input = writeInput(dir, true);
      ExecutorService exec = Executors.newSingleThreadExecutor(new NamedThreadFactory("sorter"));
      CountDownLatch busy = new CountDownLatch(1);
      exec.submit(
          () -> {
            busy.await(); // unrelated work that occupies the executor's only thread
            return null;
          });
      AtomicReference<Throwable> failure = new AtomicReference<>();
      OfflineSorter sorter = newSorter(dir, exec, 2);
      Thread sorting =
          new Thread(
              () -> {
                try {
                  sorter.sort(input);
                } catch (Throwable t) {
                  failure.set(t);
                }
              });
      try {
        sorting.start();
        awaitSortFailedOrBlockedAfterFailure(sorting);
      } finally {
        busy.countDown();
        sorting.join();
        exec.shutdown();
        assertTrue(exec.awaitTermination(1, TimeUnit.MINUTES));
      }
      assertTrue(String.valueOf(failure.get()), failure.get() instanceof IOException);
      assertEquals(
          "temp files left behind by the failed sort", List.of(), sorterTempFiles(dir));
    }
  }

  /** The sorting thread is interrupted while it waits for a partition permit. */
  public void testInterruptLeavesTempFileOfQueuedPartition() throws Exception {
    try (Directory dir = newDirectory()) {
      String input = writeInput(dir, false);
      ExecutorService exec = Executors.newSingleThreadExecutor(new NamedThreadFactory("sorter"));
      CountDownLatch busy = new CountDownLatch(1);
      exec.submit(
          () -> {
            busy.await();
            return null;
          });
      AtomicReference<Throwable> failure = new AtomicReference<>();
      OfflineSorter sorter = newSorter(dir, exec, 1);
      Thread sorting =
          new Thread(
              () -> {
                try {
                  sorter.sort(input);
                } catch (Throwable t) {
                  failure.set(t);
                }
              });
      try {
        sorting.start();
        // With maxPartitionsInRAM = 1 the sorting thread must block for the second partition's
        // permit, because the first partition's task cannot run yet. Wait for that state.
        while (blockedIn(sorting, "readPartition") == false) {
          assertTrue("sorting thread ended early: " + failure.get(), sorting.isAlive());
          Thread.onSpinWait();
        }
        sorting.interrupt();
        awaitSortFailedOrBlockedAfterFailure(sorting);
      } finally {
        busy.countDown();
        sorting.join();
        exec.shutdown();
        assertTrue(exec.awaitTermination(1, TimeUnit.MINUTES));
      }
      assertTrue(
          String.valueOf(failure.get()), failure.get() instanceof ThreadInterruptedException);
      assertEquals(
          "temp files left behind by the interrupted sort", List.of(), sorterTempFiles(dir));
    }
  }
}
